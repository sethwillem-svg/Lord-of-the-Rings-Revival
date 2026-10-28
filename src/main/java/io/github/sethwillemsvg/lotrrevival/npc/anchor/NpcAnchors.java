package io.github.sethwillemsvg.lotrrevival.npc.anchor;

import io.github.sethwillemsvg.lotrrevival.npc.data.NpcLoadoutData;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structure-bound NPC spawn anchors. The world pod calls {@link #place}; everything else here (tracking loaded
 * anchors, the 600-tick check, recording deaths) is driven by {@link NpcAnchorEvents}.
 */
public final class NpcAnchors {
    private static final Logger LOGGER = LoggerFactory.getLogger(NpcAnchors.class);

    /** Ticks between respawn checks of a loaded anchor. */
    public static final int CHECK_INTERVAL = 600;
    /** Ticks that must pass after a member died before it is replaced. */
    public static final int RESPAWN_DELAY = 24000;
    /** No respawn while a player is this close. */
    public static final int PLAYER_RANGE = 16;
    /** No respawn while a player within this distance has a clear line of sight to the spawn spot. */
    public static final int SIGHT_RANGE = 64;

    /** Loaded chunks that hold anchors, per dimension, as packed chunk positions. Server thread only. */
    private static final Map<ResourceKey<Level>, Set<Long>> TRACKED = new HashMap<>();

    private NpcAnchors() {}

    // ------------------------------------------------------------------ public API

    /**
     * Stores an anchor in the chunk of {@code pos} and spawns {@code count} NPCs of {@code type} with {@code pos} as
     * their home. Placing the same type at the same position twice does nothing the second time.
     *
     * @param role role for civilians; empty rolls one as a normal spawn does (ignored for guards and captains)
     * @param homeRadius home radius of each NPC (below 1 means the default of 16)
     * @return how many NPCs were spawned now; slots that could not be filled are filled by later checks
     */
    public static int place(
            ServerLevel level, BlockPos pos, EntityType<?> type, Optional<NpcRole> role, int count, int homeRadius) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        LevelChunk chunk = level.getChunkAt(pos);
        List<SpawnAnchor> anchors = new ArrayList<>(anchorsIn(chunk));
        for (SpawnAnchor existing : anchors) {
            if (existing.sameSpot(pos, typeId)) {
                return 0;
            }
        }
        int radius = homeRadius < 1 ? NpcMarker.DEFAULT_HOME_RADIUS : homeRadius;
        SpawnAnchor anchor = SpawnAnchor.create(pos, type, role, Math.max(1, count), radius);
        int spawned = 0;
        for (int i = 0; i < anchor.count(); i++) {
            Optional<UUID> id = spawnOne(level, anchor);
            if (id.isPresent()) {
                anchor = anchor.withMember(id.get());
                spawned++;
            }
        }
        anchors.add(anchor);
        store(level, chunk, anchors);
        return spawned;
    }

    /** The anchors stored in a chunk (empty if none). */
    public static List<SpawnAnchor> anchorsIn(LevelChunk chunk) {
        return chunk.getExistingData(NpcAttachments.NPC_SPAWN_ANCHORS.get())
                .map(NpcSpawnAnchors::anchors)
                .orElse(List.of());
    }

    // ------------------------------------------------------------------ chunk tracking

    static void trackChunk(ServerLevel level, LevelChunk chunk) {
        if (!anchorsIn(chunk).isEmpty()) {
            TRACKED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(chunk.getPos().toLong());
        }
    }

    static void untrackChunk(ServerLevel level, ChunkPos pos) {
        Set<Long> set = TRACKED.get(level.dimension());
        if (set != null) {
            set.remove(pos.toLong());
        }
    }

    static void clearTracking() {
        TRACKED.clear();
    }

    /** Runs the check on every loaded chunk that holds anchors. Called every {@link #CHECK_INTERVAL} ticks. */
    static void checkLoadedChunks(ServerLevel level) {
        Set<Long> set = TRACKED.get(level.dimension());
        if (set == null || set.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        for (Long packed : new ArrayList<>(set)) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            if (chunk == null) {
                set.remove(packed);
                continue;
            }
            check(level, chunk, now);
        }
    }

    private static void store(ServerLevel level, LevelChunk chunk, List<SpawnAnchor> anchors) {
        if (anchors.isEmpty()) {
            chunk.removeData(NpcAttachments.NPC_SPAWN_ANCHORS.get());
            untrackChunk(level, chunk.getPos());
        } else {
            chunk.setData(NpcAttachments.NPC_SPAWN_ANCHORS.get(), new NpcSpawnAnchors(anchors));
            TRACKED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(chunk.getPos().toLong());
        }
        chunk.setUnsaved(true);
    }

    /** Removes every anchor of a chunk (the NPCs are left alone). Used by tests and the world pod's cleanup. */
    public static void clear(ServerLevel level, LevelChunk chunk) {
        store(level, chunk, List.of());
    }

    // ------------------------------------------------------------------ deaths

    /** Records the death of an NPC with a home, if it was a member of an anchor at that home. */
    static void onNpcDeath(ServerLevel level, NpcEntity npc) {
        BlockPos home = npc.homePos().orElse(null);
        if (home == null) {
            return;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(home.getX() >> 4, home.getZ() >> 4);
        if (chunk == null) {
            return;
        }
        List<SpawnAnchor> anchors = anchorsIn(chunk);
        List<SpawnAnchor> updated = new ArrayList<>(anchors.size());
        boolean changed = false;
        for (SpawnAnchor anchor : anchors) {
            if (anchor.pos().equals(home) && anchor.npcs().contains(npc.getUUID())) {
                List<UUID> npcs = new ArrayList<>(anchor.npcs());
                npcs.remove(npc.getUUID());
                List<Long> deaths = new ArrayList<>(anchor.deaths());
                deaths.add(level.getGameTime());
                updated.add(anchor.withNpcs(npcs).withDeaths(deaths));
                changed = true;
            } else {
                updated.add(anchor);
            }
        }
        if (changed) {
            store(level, chunk, updated);
        }
    }

    // ------------------------------------------------------------------ the check

    /**
     * One respawn check of every anchor in a chunk at game time {@code now}: members that no longer exist are
     * recorded as dead, and if the anchor is short, at most one NPC is spawned.
     */
    public static void check(ServerLevel level, LevelChunk chunk, long now) {
        List<SpawnAnchor> anchors = anchorsIn(chunk);
        if (anchors.isEmpty()) {
            return;
        }
        List<SpawnAnchor> updated = new ArrayList<>(anchors.size());
        boolean changed = false;
        for (SpawnAnchor anchor : anchors) {
            SpawnAnchor next = checkOne(level, anchor, now);
            changed |= !next.equals(anchor);
            updated.add(next);
        }
        if (changed) {
            store(level, chunk, updated);
        }
    }

    private static SpawnAnchor checkOne(ServerLevel level, SpawnAnchor anchor, long now) {
        BlockPos pos = anchor.pos();
        // Entities of a freshly loaded chunk arrive a little after the chunk; judging earlier would see them as dead.
        if (!level.areEntitiesLoaded(ChunkPos.asLong(pos))) {
            return anchor;
        }
        // Members wander, so a member that cannot be found only counts as gone if its whole home area is loaded.
        int pad = anchor.homeRadius() + 4;
        boolean areaLoaded = level.hasChunksAt(pos.offset(-pad, 0, -pad), pos.offset(pad, 0, pad));
        List<UUID> alive = new ArrayList<>();
        List<Long> deaths = new ArrayList<>(anchor.deaths());
        for (UUID id : anchor.npcs()) {
            Entity entity = level.getEntity(id);
            if (entity != null ? entity.isAlive() : !areaLoaded) {
                alive.add(id);
            } else {
                deaths.add(now);
            }
        }
        int missing = anchor.count() - alive.size();
        if (missing <= 0) {
            deaths.clear();
        } else {
            Collections.sort(deaths);
            while (deaths.size() > missing) {
                deaths.remove(0);
            }
        }
        SpawnAnchor result = anchor.withNpcs(alive).withDeaths(deaths);
        if (missing <= 0) {
            return result;
        }
        // A slot with no recorded death (a spawn that failed earlier) is eligible at once.
        boolean eligible = deaths.size() < missing || now - deaths.get(0) >= RESPAWN_DELAY;
        if (!eligible) {
            return result;
        }
        Optional<UUID> spawned = spawnOne(level, result, true);
        if (spawned.isEmpty()) {
            return result;
        }
        if (deaths.size() >= missing) {
            deaths.remove(0);
        }
        return result.withDeaths(deaths).withMember(spawned.get());
    }

    // ------------------------------------------------------------------ spawning

    private static Optional<UUID> spawnOne(ServerLevel level, SpawnAnchor anchor) {
        return spawnOne(level, anchor, false);
    }

    private static Optional<UUID> spawnOne(ServerLevel level, SpawnAnchor anchor, boolean respectPlayers) {
        EntityType<?> type = anchor.entityType().orElse(null);
        if (type == null) {
            LOGGER.warn("NPC spawn anchor at {} has unknown type {}", anchor.pos(), anchor.typeId());
            return Optional.empty();
        }
        Entity created = type.create(level);
        if (!(created instanceof NpcEntity npc)) {
            LOGGER.warn("NPC spawn anchor at {} has non-NPC type {}", anchor.pos(), anchor.typeId());
            if (created != null) {
                created.discard();
            }
            return Optional.empty();
        }
        BlockPos spot = findSpot(level, npc, anchor.pos());
        if (spot == null) {
            npc.discard();
            return Optional.empty();
        }
        if (respectPlayers && playerBlocksSpawn(level, spot)) {
            npc.discard();
            return Optional.empty();
        }
        npc.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360.0f, 0.0f);
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.STRUCTURE, null);
        if (anchor.role().isPresent() && npc.isCivilian()) {
            npc.setRole(anchor.role().get());
            NpcLoadoutData.apply(npc, level.getRandom());
        }
        npc.setHome(anchor.pos(), anchor.homeRadius());
        level.addFreshEntityWithPassengers(npc);
        return Optional.of(npc.getUUID());
    }

    /** A free standing position at or next to the anchor, or null if there is none. */
    @Nullable
    private static BlockPos findSpot(ServerLevel level, Entity entity, BlockPos origin) {
        int[] ys = {0, 1, -1, 2, -2};
        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy : ys) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        BlockPos below = pos.below();
                        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                                && level.noCollision(entity)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Whether any player is within {@link #PLAYER_RANGE} blocks or can see the spot from within {@link #SIGHT_RANGE}. */
    static boolean playerBlocksSpawn(ServerLevel level, BlockPos spot) {
        Vec3 target = Vec3.atCenterOf(spot).add(0, 0.5, 0);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level || player.isSpectator()) {
                continue;
            }
            double distance = player.position().distanceTo(Vec3.atCenterOf(spot));
            if (distance <= PLAYER_RANGE) {
                return true;
            }
            if (distance <= SIGHT_RANGE
                    && level.clip(new ClipContext(
                                    player.getEyePosition(),
                                    target,
                                    ClipContext.Block.COLLIDER,
                                    ClipContext.Fluid.NONE,
                                    player))
                                    .getType()
                            == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }
}
