package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.api.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sergeants (spec Sergeants): the level-5 hired unit that dealt the most damage to a nemesis in its last 30 s becomes a
 * sergeant when the nemesis dies or cheats death: name, title, one strength, shown above it. Hiring code is not touched.
 */
public final class Sergeants {
    private record Hit(float total, long lastTick) {}

    /** Nemesis record id -> unit UUID -> damage dealt. */
    private static final Map<UUID, Map<UUID, Hit>> DAMAGE = new HashMap<>();
    private static final int WINDOW_TICKS = 600;

    private Sergeants() {}

    public static void onDamagePost(LivingDamageEvent.Post e) {
        Entity src = e.getSource().getEntity();
        if (src == null || e.getNewDamage() <= 0 || !HiredUnits.isUnit(src) || HiredUnits.level(src) < 5) return;
        NemesisRecord r = NemesisApi.bodyOf(e.getEntity());
        if (r == null) return;
        recordHit(r.id, src.getUUID(), e.getNewDamage(), e.getEntity().level().getGameTime());
    }

    public static void recordHit(UUID record, UUID unit, float dmg, long tick) {
        DAMAGE.computeIfAbsent(record, k -> new HashMap<>()).merge(unit, new Hit(dmg, tick),
                (a, b) -> new Hit(a.total() + b.total(), b.lastTick()));
    }

    public static void forget(UUID record) {
        DAMAGE.remove(record);
    }

    /** The nemesis body died or cheated death: promote the top level-5 damage dealer of the last 30 s. */
    public static void onNemesisDown(MinecraftServer server, NemesisRecord r, NpcEntity body) {
        Map<UUID, Hit> hits = DAMAGE.remove(r.id);
        if (hits == null || hits.isEmpty() || !(body.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        LivingEntity best = null;
        float bestDmg = 0;
        for (var en : hits.entrySet()) {
            if (now - en.getValue().lastTick() > WINDOW_TICKS) continue;
            Entity u = level.getEntity(en.getKey());
            if (!(u instanceof LivingEntity lu) || !lu.isAlive() || HiredUnits.level(lu) < 5 || lu.hasData(NemesisLink.SERGEANT)) continue;
            if (en.getValue().total() > bestDmg) {
                bestDmg = en.getValue().total();
                best = lu;
            }
        }
        if (best == null) {
            LotrLog.dbg("nemesis", "sergeant: none for id={} ({} damage dealers, none eligible)", r.shortId(), hits.size());
            return;
        }
        promote(server, best, bestDmg);
    }

    public static boolean promote(MinecraftServer server, LivingEntity unit, float dmg) {
        Optional<UUID> owner = HiredUnits.owner(unit);
        if (owner.isEmpty()) return false;
        NemesisSavedData data = NemesisSavedData.get(server);
        Set<UUID> mine = data.sergeants.computeIfAbsent(owner.get(), k -> new HashSet<>());
        int cap = NemesisConfig.i(NemesisConfig.MAX_SERGEANTS);
        if (mine.size() >= cap) {
            LotrLog.dbg("nemesis", "sergeant: unit={} not promoted, owner {} at cap {}", LotrLog.id(unit.getUUID()), LotrLog.id(owner.get()), cap);
            return false;
        }
        String race = unit instanceof NpcEntity npc ? Promotion.raceOf(npc) : "human";
        Sides.Side side = unit instanceof NpcEntity npc ? Sides.sideOfFaction(unit.level(), npc.getFactionIdentifier()) : Sides.Side.NONE;
        var faction = unit instanceof NpcEntity npc ? npc.getFactionIdentifier() : null;
        List<NemesisTraits.Trait> pool = NemesisTraits.of(NemesisTraits.Kind.STRENGTH, side, race,
                faction == null ? net.minecraft.resources.ResourceLocation.withDefaultNamespace("none") : faction);
        if (pool.isEmpty()) {
            LotrLog.warn("nemesis", "sergeant: no strength allowed for race={} side={}", race, side);
            return false;
        }
        NemesisTraits.Trait t = NemesisRandom.pick(pool);
        String first = NemesisNames.firstName(race);
        String title = NemesisNames.title(side, List.of(t.id()));
        SergeantData sd = new SergeantData(first, title, t.id());
        unit.setData(NemesisLink.SERGEANT, sd);
        unit.setCustomName(Component.translatable("lotr_revival.nemesis.sergeant_name", sd.displayName()));
        unit.setCustomNameVisible(true);
        mine.add(unit.getUUID());
        data.setDirty();
        NemesisStats.sergeants++;
        LotrLog.dbg("nemesis", "sergeant: unit={} owner={} name=\"{}\" trait={} damage={} sergeants={}/{}", LotrLog.id(unit.getUUID()),
                LotrLog.id(owner.get()), sd.displayName(), t.id(), dmg, mine.size(), cap);
        ServerPlayer p = server.getPlayerList().getPlayer(owner.get());
        if (p != null) p.sendSystemMessage(Component.translatable("lotr_revival.nemesis.sergeant", sd.displayName()).withStyle(ChatFormatting.GOLD));
        return true;
    }

    /** A sergeant that dies loses the title (design choice); its owner is told. */
    public static void onUnitDeath(LivingEntity e) {
        if (!e.hasData(NemesisLink.SERGEANT) || e.getServer() == null) return;
        SergeantData sd = e.getData(NemesisLink.SERGEANT);
        Optional<UUID> owner = HiredUnits.owner(e);
        NemesisSavedData data = NemesisSavedData.get(e.getServer());
        owner.ifPresent(o -> {
            Set<UUID> mine = data.sergeants.get(o);
            if (mine != null && mine.remove(e.getUUID())) data.setDirty();
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(o);
            if (p != null) p.sendSystemMessage(Component.translatable("lotr_revival.nemesis.sergeant_fallen", sd.displayName())
                    .withStyle(ChatFormatting.GRAY));
        });
        LotrLog.dbg("nemesis", "sergeant fallen: unit={} name=\"{}\"", LotrLog.id(e.getUUID()), sd.displayName());
    }
}
