package io.github.sethwillemsvg.lotrrevival.events;

import io.github.sethwillemsvg.lotrrevival.core.api.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.core.api.ReputationHooks;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Participants and credit (spec Participation and credit): a player who damages an event NPC, or is damaged by one, within the
 * event radius + 32 joins; their side is the side whose enemies they damaged most. Hired units' damage counts for their owner.
 */
public final class Participation {
    public static int joins;

    private Participation() {}

    public static void onDamage(LivingDamageEvent.Post e) {
        try {
            LivingEntity victim = e.getEntity();
            if (victim.level().isClientSide) return;
            Entity src = e.getSource().getEntity();
            EventParticipant vp = EventParticipant.of(victim);
            if (vp != null && src != null) {
                UUID player = creditedPlayer(src);
                WorldEvent ev = WorldEventManager.get(vp.eventId());
                if (player != null && ev != null && ev.state == WorldEvent.State.ACTIVE && near(ev, src)) {
                    WorldEvent.PlayerPart part = join(ev, player);
                    if (vp.side().equals(EventParticipant.LEAD)) part.damageToLead += e.getNewDamage();
                    else part.damageToOpposition += e.getNewDamage();
                }
            }
            if (src != null && victim instanceof ServerPlayer p) {
                EventParticipant ap = EventParticipant.of(src);
                WorldEvent ev = ap == null ? null : WorldEventManager.get(ap.eventId());
                if (ev != null && ev.state == WorldEvent.State.ACTIVE && near(ev, p)) {
                    WorldEvent.PlayerPart part = join(ev, p.getUUID());
                    if (ap.side().equals(EventParticipant.LEAD)) part.hurtByLead = true;
                    else part.hurtByOpposition = true;
                }
            }
        } catch (RuntimeException ex) {
            LotrLog.error("events", "participation listener failed", ex);
        }
    }

    private static UUID creditedPlayer(Entity src) {
        if (src instanceof ServerPlayer p) return p.getUUID();
        Optional<UUID> owner = HiredUnits.owner(src);
        return owner.orElse(null);
    }

    private static boolean near(WorldEvent ev, Entity e) {
        double r = ev.radius + 32;
        return e.blockPosition().distSqr(ev.centre) <= r * r;
    }

    public static WorldEvent.PlayerPart join(WorldEvent ev, UUID player) {
        WorldEvent.PlayerPart part = ev.players.get(player);
        if (part == null) {
            part = new WorldEvent.PlayerPart();
            ev.players.put(player, part);
            joins++;
            LotrLog.dbg("events", "participant joined: event={} player={}", ev.shortId(), LotrLog.id(player));
        }
        return part;
    }

    /**
     * Win bonus once to each participant of the winning side who dealt at least 20% of all player damage to the losing side, or
     * who is within 32 blocks of the centre at the end.
     */
    public static void payout(ServerLevel level, WorldEvent ev) {
        String winner = ev.outcome == WorldEvent.Outcome.LEAD_WON ? EventParticipant.LEAD
                : ev.outcome == WorldEvent.Outcome.OPPOSITION_WON ? EventParticipant.OPPOSITION : null;
        WorldEventType type = WorldEventManager.TYPES.get(ev.type);
        if (winner == null || type == null) return;
        int bonus = type.bonus(ev.outcome);
        List<ResourceLocation> factions = winner.equals(EventParticipant.LEAD) ? ev.leadFactions : ev.oppFactions;
        float total = 0;
        for (WorldEvent.PlayerPart p : ev.players.values()) total += winner.equals(EventParticipant.LEAD) ? p.damageToOpposition : p.damageToLead;
        for (Map.Entry<UUID, WorldEvent.PlayerPart> en : ev.players.entrySet()) {
            WorldEvent.PlayerPart part = en.getValue();
            String side = part.side();
            float dealt = winner.equals(EventParticipant.LEAD) ? part.damageToOpposition : part.damageToLead;
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(en.getKey());
            boolean nearCentre = p != null && p.level() == level && p.blockPosition().distSqr(ev.centre) <= 32 * 32;
            double share = total > 0 ? dealt / total : 0;
            boolean paid = side.equals(winner) && p != null && bonus > 0 && (share >= 0.2 || nearCentre);
            LotrLog.dbg("events", "win bonus: event={} player={} side={} winner={} share={} near={} -> {}", ev.shortId(), LotrLog.id(en.getKey()),
                    side, winner, String.format("%.2f", share), nearCentre, paid ? "+" + bonus + " " + factions : "nothing");
            if (paid) for (ResourceLocation f : factions) ReputationHooks.get().add(p, f, bonus, "world event " + ev.type.getPath());
        }
    }
}
