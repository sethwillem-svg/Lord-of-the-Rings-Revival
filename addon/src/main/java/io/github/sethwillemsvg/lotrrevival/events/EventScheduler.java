package io.github.sethwillemsvg.lotrrevival.events;

import io.github.sethwillemsvg.lotrrevival.core.api.FastTravel;
import io.github.sethwillemsvg.lotrrevival.core.api.StructureVenues;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nemesis.Ambush;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisConfig;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRandom;
import io.github.sethwillemsvg.lotrrevival.nemesis.Promotion;
import io.github.sethwillemsvg.lotrrevival.nemesis.Sides;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player event rolls (spec Scheduling): every 30 s per online player, staggered; eligibility, chance (halved in Yellow),
 * per-player cooldown, player-scaled server cap, side balance (60/40), type weights with up to 4 redraws, band and site checks.
 * {@link #stage} skips the chance and cooldown (commands, {@code force_type}, tests).
 */
public final class EventScheduler {
    public static int rolls, staged, rejected;
    /** Game tests: spawn-exclusion override (-1 uses the config). */
    public static int testSpawnExclusion = -1;

    private EventScheduler() {}

    public static void tick(MinecraftServer server) {
        if (!EventsConfig.enabled()) return;
        int period = EventsConfig.i(EventsConfig.ROLL_SECONDS) * 20;
        long tick = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if ((tick + (p.getUUID().hashCode() & 0x7fffffff) + 7) % period != 0) continue;
            try {
                roll(p);
            } catch (RuntimeException e) {
                LotrLog.error("events", "event roll failed for " + LotrLog.id(p.getUUID()), e);
            }
        }
    }

    public static void roll(ServerPlayer p) {
        rolls++;
        String forced = EventsConfig.forceType();
        String why = whyNotEligible(p, !forced.isEmpty());
        if (why != null) {
            LotrLog.dbg("events", "roll: player={} not eligible ({})", LotrLog.id(p.getUUID()), why);
            return;
        }
        if (!forced.isEmpty()) {
            ResourceLocation id = forced.contains(":") ? ResourceLocation.tryParse(forced) : io.github.sethwillemsvg.lotrrevival.LotrRevival.id(forced);
            LotrLog.dbg("events", "roll: player={} force_type={} -> staging now", LotrLog.id(p.getUUID()), id);
            stage(p, id);
            return;
        }
        double chance = EventsConfig.d(EventsConfig.CHANCE) * (LoadGovernor.state() == LoadGovernor.State.YELLOW ? 0.5 : 1);
        double r = NemesisRandom.roll();
        LotrLog.dbg("events", "roll: player={} at {} chance={} roll={} governor={} -> {}", LotrLog.id(p.getUUID()), p.blockPosition().toShortString(),
                String.format("%.3f", chance), String.format("%.3f", r), LoadGovernor.state(), r < chance ? "stage" : "no");
        if (r < chance) stage(p, null);
    }

    /** Null when an event may be staged for this player now; {@code ignoreCooldown} for forced events. */
    public static @Nullable String whyNotEligible(ServerPlayer p, boolean ignoreCooldown) {
        if (!EventsConfig.enabled()) return "events disabled";
        String ex = Promotion.whyPlayerExcluded(p);
        if (ex != null) return ex;
        if (!NemesisConfig.dimensionAllowed(p.level())) return "dimension";
        Long start = Ambush.SESSION_START.get(p.getUUID());
        long session = start == null ? 0 : p.getServer().getTickCount() - start;
        if (!ignoreCooldown && session < EventsConfig.i(EventsConfig.MIN_ONLINE_SECONDS) * 20L) return "online " + session / 20 + " s";
        if (WorldEventManager.countFor(p.getUUID()) > 0) return "already has an event";
        if (FastTravel.isTravelling(p)) return "fast travelling";
        if (LoadGovernor.state() == LoadGovernor.State.RED) return "governor red";
        long until = WorldEventSavedData.get(p.getServer()).playerCooldownUntil.getOrDefault(p.getUUID(), 0L);
        if (!ignoreCooldown && p.level().getGameTime() < until) return "cooldown " + (until - p.level().getGameTime()) / 20 + " s";
        int cap = serverCap(p.getServer());
        if (WorldEventManager.active().size() >= cap) return "server cap " + cap + " reached";
        return null;
    }

    /** 2 + 1 per 3 eligible online players, at most 8. */
    public static int serverCap(MinecraftServer server) {
        int eligible = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (Promotion.whyPlayerExcluded(p) == null) eligible++;
        return cap(eligible);
    }

    public static int cap(int eligiblePlayers) {
        return Math.min(EventsConfig.i(EventsConfig.MAX_EVENTS),
                EventsConfig.i(EventsConfig.MAX_EVENTS_BASE) + eligiblePlayers / EventsConfig.i(EventsConfig.PLAYERS_PER_EXTRA_EVENT));
    }

    /** Side balance: the player's side leads with probability side_balance; unsided players 50/50. */
    public static Sides.Side chooseLeadSide(Sides.Side playerSide) {
        if (playerSide == Sides.Side.NONE) return NemesisRandom.roll() < 0.5 ? Sides.Side.GOOD : Sides.Side.EVIL;
        return NemesisRandom.roll() < EventsConfig.d(EventsConfig.SIDE_BALANCE) ? playerSide : playerSide.opposite();
    }

    public static @Nullable WorldEventType drawType(boolean allowRaid) {
        List<WorldEventType> types = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (WorldEventType t : WorldEventManager.TYPES.values()) {
            int w = weight(t.id());
            if (w <= 0 || (!allowRaid && t.venueRequired())) continue;
            types.add(t);
            weights.add(w);
            total += w;
        }
        if (total == 0) return null;
        int r = NemesisRandom.nextInt(total);
        for (int i = 0; i < types.size(); i++) {
            r -= weights.get(i);
            if (r < 0) return types.get(i);
        }
        return types.getLast();
    }

    private static int weight(ResourceLocation id) {
        return switch (id.getPath()) {
            case "warband_clash" -> EventsConfig.i(EventsConfig.WEIGHT_CLASH);
            case "muster" -> EventsConfig.i(EventsConfig.WEIGHT_MUSTER);
            case "raid" -> EventsConfig.i(EventsConfig.WEIGHT_RAID);
            default -> 0;
        };
    }

    /** A random surface point in the 64-256 band around the player, in a loaded chunk; null after 8 tries. */
    public static @Nullable BlockPos bandPoint(ServerLevel level, BlockPos from) {
        int min = EventsConfig.i(EventsConfig.BAND_MIN), max = EventsConfig.i(EventsConfig.BAND_MAX);
        for (int i = 0; i < 8; i++) {
            double a = NemesisRandom.roll() * Math.PI * 2, d = min + NemesisRandom.roll() * (max - min);
            BlockPos p = new BlockPos((int) (from.getX() + Math.cos(a) * d), 0, (int) (from.getZ() + Math.sin(a) * d));
            if (!level.hasChunkAt(p)) continue;
            return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
        }
        return null;
    }

    /** Site checks: surface not water, away from world spawn and respawn points, spaced from other events, flat (open ground). */
    public static @Nullable String siteProblem(ServerLevel level, BlockPos centre, boolean openGround) {
        int excl = testSpawnExclusion >= 0 ? testSpawnExclusion : EventsConfig.i(EventsConfig.SPAWN_EXCLUSION);
        if (centre.distSqr(level.getSharedSpawnPos()) < (double) excl * excl) return "within " + excl + " of world spawn";
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            BlockPos rp = p.getRespawnPosition();
            if (rp != null && p.getRespawnDimension() == level.dimension() && centre.distSqr(rp) < (double) excl * excl)
                return "within " + excl + " of " + LotrLog.id(p.getUUID()) + "'s respawn point";
        }
        int spacing = EventsConfig.i(EventsConfig.EVENT_SPACING);
        for (WorldEvent e : WorldEventManager.active())
            if (e.dimension.equals(level.dimension().location()) && e.centre.distSqr(centre) < (double) spacing * spacing)
                return "within " + spacing + " of event " + e.shortId();
        if (openGround) {
            if (!level.hasChunkAt(centre)) return "chunk not loaded";
            if (!level.getFluidState(centre).isEmpty() || !level.getFluidState(centre.below()).isEmpty()) return "water";
            if (!VenueFinder.flatEnough(level, centre)) return "not flat";
        }
        return null;
    }

    /** Stages one event for this player now (type null = drawn by weight). Returns the event or null (reasons logged). */
    public static @Nullable WorldEvent stage(ServerPlayer p, @Nullable ResourceLocation typeId) {
        ServerLevel level = p.serverLevel();
        long tick = level.getGameTime();
        Sides.Side side = Sides.side(p);
        Sides.Side wantLead = chooseLeadSide(side);
        boolean allowRaid = LoadGovernor.state() == LoadGovernor.State.GREEN;
        for (int attempt = 0; attempt < 5; attempt++) {
            WorldEventType type = typeId != null ? WorldEventManager.TYPES.get(typeId) : drawType(allowRaid);
            if (type == null) {
                LotrLog.dbg("events", "stage: no type ({})", typeId);
                return null;
            }
            StructureVenues.Venue venue = type.venueKinds().isEmpty() ? null : VenueFinder.find(level, p.blockPosition(), type.venueKinds(), tick);
            if (venue == null && type.venueRequired()) {
                reject(p, type, attempt, "needs a " + type.venueKinds() + " venue, none found");
                if (typeId != null) return null;
                continue;
            }
            BlockPos centre = venue != null ? venue.centre() : bandPoint(level, p.blockPosition());
            if (centre == null) {
                reject(p, type, attempt, "no loaded point in the band");
                continue;
            }
            String problem = siteProblem(level, centre, venue == null);
            if (problem != null) {
                reject(p, type, attempt, "site " + centre.toShortString() + ": " + problem);
                if (venue != null) VenueFinder.cooldown(level, venue, tick);
                continue;
            }
            SiteRoster.Pick pick = SiteRoster.pick(level, centre, venue == null ? null : venue.structure(), wantLead);
            if (pick == null) {
                reject(p, type, attempt, "no factions at site " + centre.toShortString());
                continue;
            }
            if (type.venueRequired() && venue != null && venue.owner() != null) {
                if (!Sides.hostile(level, pick.lead(), venue.owner())) {
                    reject(p, type, attempt, "lead " + pick.lead() + " not hostile to village owner " + venue.owner());
                    continue;
                }
                pick = new SiteRoster.Pick(pick.lead(), venue.owner(), pick.leadSide(), pick.table(), pick.present());
            }
            String typeReject = type.rejectReason(pick);
            if (typeReject != null) {
                reject(p, type, attempt, typeReject);
                continue;
            }
            LotrLog.dbg("events", "stage: player={} side={} want_lead={} type={} venue={} centre={} lead={} opposition={} attempt={}",
                    LotrLog.id(p.getUUID()), side, wantLead, type.id(), venue == null ? "open ground" : VenueFinder.key(venue),
                    centre.toShortString(), pick.lead(), pick.opposition(), attempt);
            WorldEvent ev = WorldEventManager.start(level, type, centre, p.getUUID(), pick, venue);
            if (ev != null) {
                staged++;
                return ev;
            }
            if (venue != null) VenueFinder.cooldown(level, venue, tick);
        }
        return null;
    }

    private static void reject(ServerPlayer p, WorldEventType t, int attempt, String why) {
        rejected++;
        LotrLog.dbg("events", "stage rejected: player={} type={} attempt={} reason={}", LotrLog.id(p.getUUID()), t.id(), attempt, why);
    }
}
