package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.api.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.core.api.ReputationHooks;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Escape, cheat-death, scars, weakness deaths, dying for good and the reputation call (spec Escape and cheat-death, Dying for
 * good, Reputation).
 */
public final class DeathRules {
    /** Escapes decided during a damage event; the body is removed at the end of the tick. */
    private static final List<Runnable> PENDING = new ArrayList<>();

    private DeathRules() {}

    /** Bookkeeping on any hit to a body: who fought it (intel, boss bar) and the once-per-manifestation grudge. */
    public static void onIncoming(LivingIncomingDamageEvent e) {
        NemesisRecord r = NemesisApi.bodyOf(e.getEntity());
        if (r == null) return;
        if (e.getSource().getEntity() instanceof Player p) {
            r.knowsStrengths.add(p.getUUID());
            if (r.damagedBy.add(p.getUUID())) r.addGrudge(p.getUUID(), 1, e.getEntity().level().getGameTime());
        }
    }

    /** Escape: once per manifestation, when a hit would leave it at 20% health or less. */
    public static void onDamagePre(LivingDamageEvent.Pre e) {
        try {
            if (!(e.getEntity() instanceof NpcEntity npc)) return;
            NemesisRecord r = NemesisApi.bodyOf(npc);
            if (r == null || r.escapeTried) return;
            float after = npc.getHealth() - e.getNewDamage();
            if (after > npc.getMaxHealth() * 0.2f) return;
            r.escapeTried = true;
            Entity attacker = e.getSource().getEntity();
            double chance = NemesisConfig.listAt(NemesisConfig.ESCAPE_CHANCES, r.rank.ordinal(), 0.1);
            if (attacker != null && r.grudge(attacker.getUUID()) >= 5) chance += NemesisConfig.d(NemesisConfig.ESCAPE_GRUDGE_BONUS);
            double roll = NemesisRandom.roll();
            boolean escaped = roll < chance;
            LotrLog.dbg("nemesis", "escape roll: id={} rank={} health {}->{} of {} attacker={} chance={} roll={} -> {}", r.shortId(), r.rank,
                    npc.getHealth(), after, npc.getMaxHealth(), attacker == null ? "-" : LotrLog.id(attacker.getUUID()),
                    String.format("%.2f", chance), String.format("%.2f", roll), escaped ? "escaped" : "stays");
            if (!escaped) return;
            e.setNewDamage(0);
            escape(npc.getServer(), r, npc, attacker);
        } catch (RuntimeException ex) {
            LotrLog.error("nemesis", "escape listener failed", ex);
        }
    }

    public static void escape(MinecraftServer server, NemesisRecord r, NpcEntity npc, @Nullable Entity attacker) {
        NemesisStats.escapes++;
        taunt(npc, r, "escape", attacker);
        Renown.add(server, r, NemesisConfig.i(NemesisConfig.RENOWN_ESCAPE), "escaped");
        if (attacker instanceof Player p) r.addGrudge(p.getUUID(), 1, npc.level().getGameTime());
        Sergeants.forget(r.id);
        if (r.ambushTarget != null) Ambush.onAmbushEnded(server, r, r.ambushTarget, true);
        NeoForge.EVENT_BUS.post(new NemesisEvents.Defeated(r, NemesisEvents.Outcome.ESCAPED, "escape", attacker));
        PENDING.add(() -> Manifestation.retreat(server, r, "escaped"));
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        List<Runnable> run = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Runnable r : run) r.run();
    }

    /** Body death: reputation, sergeants, then cheat-death or dying for good. */
    public static void onDeath(LivingDeathEvent e) {
        try {
            if (!(e.getEntity() instanceof NpcEntity npc) || npc.getServer() == null) return;
            NemesisRecord r = NemesisApi.bodyOf(npc);
            if (r == null) {
                NemesisLink link = NemesisLink.of(npc);
                if (link != null) LotrLog.dbg("nemesis", "death of a linked body that is not live: entity={} id={} manifestation={}",
                        LotrLog.id(npc.getUUID()), LotrLog.id(link.id()), link.manifestation());
                Sergeants.onUnitDeath(npc);
                return;
            }
            bodyDeath(npc.getServer(), r, npc, e.getSource());
        } catch (RuntimeException ex) {
            LotrLog.error("nemesis", "death listener failed", ex);
        }
    }

    public static void bodyDeath(MinecraftServer server, NemesisRecord r, NpcEntity npc, DamageSource src) {
        Entity killer = src.getEntity();
        long tick = npc.level().getGameTime();
        DamageKind kind = DamageKind.of(src);
        boolean byPlayerOrUnit = killer instanceof ServerPlayer || HiredUnits.isUnit(killer);
        if (byPlayerOrUnit) {
            int mult = NemesisConfig.i(NemesisConfig.REPUTATION_MULTIPLIER);
            ReputationHooks.get().onKill((LivingEntity) killer, npc, mult);
            LotrLog.dbg("nemesis", "reputation: onKill(killer={}, victim={}, x{}) via {}", LotrLog.id(killer.getUUID()), r.shortId(), mult,
                    ReputationHooks.get().getClass().getSimpleName());
        } else {
            LotrLog.dbg("nemesis", "reputation: none for {} (killer {} is not a player or hired unit; source {})", r.shortId(),
                    killer == null ? "-" : killer.getType().toShortString() + ":" + LotrLog.id(killer.getUUID()), src.getMsgId());
        }
        Sergeants.onNemesisDown(server, r, npc);
        if (r.ambushTarget != null) Ambush.onAmbushEnded(server, r, r.ambushTarget, true);
        Manifestation.bodyDied(server, r);
        boolean weak = TraitEffects.isWeaknessHit(r, npc, src);
        String killerDesc = killer == null ? "environment:" + src.getMsgId() : LotrLog.id(killer.getUUID());
        if (weak) {
            die(server, r, 2, "killed by its weakness " + r.weakness + " (" + kind.key() + ")", killer);
            return;
        }
        int tries = r.cheatDeaths;
        if (tries >= 2) {
            die(server, r, 1, "already cheated death twice", killer);
            return;
        }
        double chance = NemesisConfig.listAt(NemesisConfig.CHEAT_DEATH_CHANCES, tries, 0);
        double roll = NemesisRandom.roll();
        boolean cheats = roll < chance;
        LotrLog.dbg("nemesis", "cheat-death roll: id={} try={} kind={} killer={} chance={} roll={} -> {}", r.shortId(), tries + 1, kind.key(),
                killerDesc, String.format("%.2f", chance), String.format("%.2f", roll), cheats ? "cheats death" : "dies");
        if (!cheats) {
            die(server, r, 1, "cheat-death failed", killer);
            return;
        }
        r.cheatDeaths++;
        r.status = NemesisRecord.Status.AT_LARGE;
        if (!r.scars.contains(kind)) r.scars.add(kind);
        var scar = NemesisTraits.scarFor(kind);
        if (scar != null && !scar.title().isEmpty()) r.scarTitle = scar.title();
        Renown.add(server, r, NemesisConfig.i(NemesisConfig.RENOWN_CHEAT_DEATH), "cheated death");
        if (killer instanceof Player p) r.addGrudge(p.getUUID(), 2, tick);
        NemesisStats.cheatDeaths++;
        NemesisSavedData.get(server).setDirty();
        LotrLog.dbg("nemesis", "cheated death: id={} now \"{}\" scars={} cheat_deaths={}", r.shortId(), r.displayName(), r.scars, r.cheatDeaths);
        rumour(server, r, Component.translatable("lotr_revival.nemesis.rumour.cheated", r.displayName()));
        NeoForge.EVENT_BUS.post(new NemesisEvents.Defeated(r, NemesisEvents.Outcome.CHEATED_DEATH, kind.key(), killer));
    }

    /** Dying for good: rule 1 death, 2 weakness, 3 lost challenge, 4 faded, 5 operator. */
    public static void die(MinecraftServer server, NemesisRecord r, int rule, String reason, @Nullable Entity killer) {
        if (!r.alive()) return;
        Manifestation.Body b = Manifestation.body(r.id);
        if (b != null) Manifestation.retreat(server, r, "dying for good");
        r.status = NemesisRecord.Status.DEAD;
        r.deadSinceTick = server.overworld().getGameTime();
        NemesisSavedData data = NemesisSavedData.get(server);
        data.bindBody(r, null);
        data.setDirty();
        NemesisStats.deaths++;
        LotrLog.dbg("nemesis", "dead for good: id={} name=\"{}\" faction={} rank={} rule={} reason={} tick={}", r.shortId(), r.displayName(),
                r.faction, r.rank, rule, reason, r.deadSinceTick);
        rumour(server, r, Component.translatable("lotr_revival.nemesis.rumour.dead", r.displayName()));
        NeoForge.EVENT_BUS.post(new NemesisEvents.Defeated(r, NemesisEvents.Outcome.DEAD, "rule " + rule + ": " + reason, killer));
        Renown.reseat(server, r.faction, "death of " + r.shortId());
    }

    /** One grey line to the online grudge holders. */
    public static void rumour(MinecraftServer server, NemesisRecord r, Component line) {
        for (UUID u : r.grudges.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) p.sendSystemMessage(line.copy().withStyle(ChatFormatting.GRAY));
        }
    }

    public static void taunt(NpcEntity npc, NemesisRecord r, String kind, @Nullable Entity at) {
        if (!(at instanceof ServerPlayer p)) return;
        int n = NemesisRandom.nextInt(3);
        p.sendSystemMessage(Component.translatable("lotr_revival.nemesis.say", r.displayName(),
                Component.translatable("lotr_revival.nemesis.taunt." + kind + "." + n, p.getDisplayName())).withStyle(ChatFormatting.RED));
    }
}
