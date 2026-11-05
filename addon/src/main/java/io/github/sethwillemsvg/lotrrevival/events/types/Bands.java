package io.github.sethwillemsvg.lotrrevival.events.types;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.events.SiteRoster;
import io.github.sethwillemsvg.lotrrevival.events.VenueFinder;
import io.github.sethwillemsvg.lotrrevival.events.WorldEvent;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Shared staging for the event types: a leader (a nemesis when one is free) and its band, placed on open floor. */
final class Bands {
    private Bands() {}

    /** Spawns a leader and {@code followers} NPCs of the faction around {@code at}; returns what was placed. */
    static List<NpcEntity> spawn(ServerLevel level, WorldEvent ev, ResourceLocation faction, String side, int followers, BlockPos at,
                                 @Nullable BoundingBox bounds, SiteRoster.Pick pick, boolean nemesisLeader) {
        List<NpcEntity> out = new ArrayList<>();
        List<BlockPos> spots = VenueFinder.floor(level, at, bounds, followers + 1, 6);
        if (spots.size() < (followers + 1 + 1) / 2) {
            LotrLog.dbg("events", "event {}: only {} of {} floor spots for {} at {}", ev.shortId(), spots.size(), followers + 1, faction,
                    at.toShortString());
            return out;
        }
        BlockPos first = spots.get(0);
        NpcEntity leader = nemesisLeader ? WorldEventManager.spawnNemesis(level, ev, faction, first, side) : null;
        if (leader == null) {
            ResourceLocation type = SiteRoster.type(level, faction, true, pick.table());
            if (type != null) leader = WorldEventManager.spawnNpc(level, ev, type, first, side, "leader");
        }
        if (leader != null) out.add(leader);
        for (int i = 1; i < spots.size(); i++) {
            ResourceLocation type = SiteRoster.type(level, faction, false, pick.table());
            if (type == null) break;
            NpcEntity n = WorldEventManager.spawnNpc(level, ev, type, spots.get(i), side, "soldier");
            if (n != null) out.add(n);
        }
        LotrLog.dbg("events", "event {}: placed {} of {} for {} ({} side) at {} leader={}", ev.shortId(), out.size(), followers + 1, faction,
                side, at.toShortString(), leader == null ? "-" : LotrLog.id(leader.getUUID()));
        return out;
    }

    /** Points each NPC of one band at the nearest NPC of the other. */
    static void engage(List<NpcEntity> a, List<NpcEntity> b) {
        for (NpcEntity x : a) nearest(x, b);
        for (NpcEntity y : b) nearest(y, a);
    }

    private static void nearest(NpcEntity x, List<NpcEntity> others) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (NpcEntity o : others) {
            double d = o.distanceToSqr(x);
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        if (best != null) x.setTarget(best);
    }
}
