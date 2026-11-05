package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.api.FastTravel;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Ambushes (spec Ambush rules): a rare check per online player; a nemesis with a grudge finds them and attacks. */
public final class Ambush {
    /** Player -> server tick of login (session time). */
    public static final Map<UUID, Long> SESSION_START = new HashMap<>();
    /** Set by the events module: true while the load governor is Red. */
    public static volatile BooleanSupplier governorRed = () -> false;
    /** Game tests: safe radius override (-1 uses the config). */
    public static int testSafeRadius = -1;

    private Ambush() {}

    public static void onLogin(ServerPlayer p) {
        SESSION_START.put(p.getUUID(), (long) p.getServer().getTickCount());
    }

    public static void onLogout(ServerPlayer p) {
        SESSION_START.remove(p.getUUID());
    }

    /** Staggered per player: every check period, each player once. */
    public static void tick(MinecraftServer server) {
        if (!NemesisConfig.enabled()) return;
        int period = NemesisConfig.i(NemesisConfig.AMBUSH_CHECK_SECONDS) * 20;
        long tick = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if ((tick + (p.getUUID().hashCode() & 0x7fffffff)) % period != 0) continue;
            try {
                check(p, period);
            } catch (RuntimeException e) {
                LotrLog.error("nemesis", "ambush check failed for " + LotrLog.id(p.getUUID()), e);
            }
        }
    }

    static void check(ServerPlayer p, int period) {
        MinecraftServer server = p.getServer();
        NemesisSavedData data = NemesisSavedData.get(server);
        long online = data.onlineTicks.merge(p.getUUID(), (long) period, Long::sum);
        data.setDirty();
        NemesisPlayerData.checkSideSwitch(p, data);
        long nowMs = System.currentTimeMillis();
        for (NemesisRecord r : data.records.values()) if (r.alive() && r.activeGrudge(p.getUUID()) > 0) r.lastHolderOnlineMs = nowMs;
        NemesisStats.ambushChecks++;
        String why = whyNotEligible(p, data);
        if (why != null) {
            LotrLog.dbg("nemesis", "ambush check: player={} not eligible ({})", LotrLog.id(p.getUUID()), why);
            return;
        }
        NemesisRecord pick = candidate(p, data);
        if (pick == null) return;
        int g = pick.activeGrudge(p.getUUID());
        double chance = NemesisConfig.d(NemesisConfig.AMBUSH_CHANCE) * Math.min(3, 1 + 0.25 * g);
        double roll = NemesisRandom.roll();
        LotrLog.dbg("nemesis", "ambush check: player={} candidate={} grudge={} chance={} roll={} -> {} online={}t", LotrLog.id(p.getUUID()),
                pick.shortId(), g, String.format("%.3f", chance), String.format("%.3f", roll), roll < chance ? "ambush" : "no", online);
        if (roll < chance) start(p, pick);
    }

    /** Null when an ambush may be staged against this player now. */
    public static @Nullable String whyNotEligible(ServerPlayer p, NemesisSavedData data) {
        String ex = Promotion.whyPlayerExcluded(p);
        if (ex != null) return ex;
        if (!NemesisConfig.dimensionAllowed(p.level())) return "dimension";
        Long start = SESSION_START.get(p.getUUID());
        long session = start == null ? 0 : p.getServer().getTickCount() - start;
        if (session < NemesisConfig.i(NemesisConfig.AMBUSH_MIN_ONLINE_MINUTES) * 1200L) return "online " + session / 20 + " s this session";
        int safe = testSafeRadius >= 0 ? testSafeRadius : NemesisConfig.i(NemesisConfig.SAFE_RADIUS);
        BlockPos spawn = p.serverLevel().getSharedSpawnPos();
        if (p.blockPosition().distSqr(spawn) < (double) safe * safe) return "within " + safe + " of world spawn";
        BlockPos respawn = p.getRespawnPosition();
        if (respawn != null && p.getRespawnDimension() == p.level().dimension() && p.blockPosition().distSqr(respawn) < (double) safe * safe)
            return "within " + safe + " of respawn point";
        if (FastTravel.isTravelling(p)) return "fast travelling";
        if (governorRed.getAsBoolean()) return "governor red";
        long until = data.ambushCooldownUntil.getOrDefault(p.getUUID(), 0L);
        long online = data.onlineTicks.getOrDefault(p.getUUID(), 0L);
        if (online < until) return "cooldown " + (until - online) / 20 + " s left";
        if (data.manifestCount() >= NemesisConfig.i(NemesisConfig.MAX_MANIFEST)) return "max_manifest reached";
        for (NemesisRecord r : data.records.values()) if (p.getUUID().equals(r.ambushTarget)) return "already ambushed";
        return null;
    }

    /** AT_LARGE records with a non-dormant grudge of 2 or more on the player, of a faction the player is an enemy of. */
    public static @Nullable NemesisRecord candidate(ServerPlayer p, NemesisSavedData data) {
        NemesisRecord best = null;
        for (NemesisRecord r : data.records.values()) {
            if (r.status != NemesisRecord.Status.AT_LARGE || r.activeGrudge(p.getUUID()) < 2) continue;
            if (!MeRanks.nemesisFaction(Sides.faction(p.level(), r.faction)) || !Sides.isEnemy(p.level(), r.faction, p)) continue;
            if (best == null || r.activeGrudge(p.getUUID()) > best.activeGrudge(p.getUUID())) best = r;
        }
        return best;
    }

    /** Spawns the ambush now (also used by commands and tests). Null when cancelled or no spot was found. */
    public static @Nullable NpcEntity start(ServerPlayer p, NemesisRecord r) {
        if (NeoForge.EVENT_BUS.post(new NemesisEvents.Ambush(r, p)).isCanceled()) {
            LotrLog.dbg("nemesis", "ambush cancelled by listener: id={} player={}", r.shortId(), LotrLog.id(p.getUUID()));
            return null;
        }
        BlockPos spot = spawnSpot(p);
        if (spot == null) {
            LotrLog.dbg("nemesis", "ambush skipped: no spawn spot in 16 tries near {}", p.blockPosition().toShortString());
            return null;
        }
        NpcEntity body = Manifestation.spawn(p.serverLevel(), r, spot, "ambush on " + LotrLog.id(p.getUUID()));
        if (body == null) return null;
        r.ambushTarget = p.getUUID();
        body.setTarget(p);
        NemesisSavedData data = NemesisSavedData.get(p.getServer());
        long online = data.onlineTicks.getOrDefault(p.getUUID(), 0L);
        data.ambushCooldownUntil.put(p.getUUID(), online + NemesisConfig.i(NemesisConfig.AMBUSH_COOLDOWN_MINUTES) * 1200L);
        data.setDirty();
        NemesisStats.ambushes++;
        DeathRules.taunt(body, r, "ambush", p);
        LotrLog.dbg("nemesis", "ambush: id={} name=\"{}\" player={} at {} (player at {}) dist={}", r.shortId(), r.displayName(),
                LotrLog.id(p.getUUID()), spot.toShortString(), p.blockPosition().toShortString(),
                String.format("%.1f", Math.sqrt(spot.distSqr(p.blockPosition()))));
        return body;
    }

    /** Surface 16-28 blocks away, not water, out of the player's line of sight if possible; 16 tries. */
    public static @Nullable BlockPos spawnSpot(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos visible = null;
        Vec3 eye = p.getEyePosition();
        for (int i = 0; i < 16; i++) {
            double a = NemesisRandom.roll() * Math.PI * 2, d = 16 + NemesisRandom.roll() * 12;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
            if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
            if (!level.getFluidState(top).isEmpty() || !level.getFluidState(top.below()).isEmpty()) continue;
            if (!level.getBlockState(top).isAir() || !level.getBlockState(top.above()).isAir()) continue;
            Vec3 target = Vec3.atCenterOf(top.above());
            boolean hidden = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType()
                    != HitResult.Type.MISS;
            if (hidden) return top;
            if (visible == null) visible = top;
        }
        return visible;
    }

    /** The ambush is over: the cooldown is 60 min instead of 75 if the player won. */
    public static void onAmbushEnded(MinecraftServer server, NemesisRecord r, UUID player, boolean playerWon) {
        if (playerWon) {
            NemesisSavedData data = NemesisSavedData.get(server);
            long online = data.onlineTicks.getOrDefault(player, 0L);
            data.ambushCooldownUntil.put(player, online + NemesisConfig.i(NemesisConfig.AMBUSH_COOLDOWN_WON_MINUTES) * 1200L);
            data.setDirty();
        }
        LotrLog.dbg("nemesis", "ambush ended: id={} player={} player_won={}", r.shortId(), LotrLog.id(player), playerWon);
        r.ambushTarget = null;
    }
}
