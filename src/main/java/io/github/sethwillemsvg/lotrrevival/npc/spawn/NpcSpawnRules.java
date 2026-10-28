package io.github.sethwillemsvg.lotrrevival.npc.spawn;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcClass;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Pure rules of the wild spawner: cap and damping math and the spot checks. Nothing here touches a level, so
 * GameTests can run them without the Middle-earth dimension.
 */
public final class NpcSpawnRules {
    /** Wild NPCs allowed per {@link #CHUNKS_PER_CAP} eligible chunks. */
    public static final int CAP_PER_AREA = 100;

    public static final int CHUNKS_PER_CAP = 196;
    /** Chunk radius of the eligible square around a player: 7 gives 15 x 15 chunks. */
    public static final int AREA_RADIUS_CHUNKS = 7;

    public static final double MIN_DAMPING = 0.5;
    public static final int MIN_LIGHT = 8;
    public static final double MIN_PLAYER_DISTANCE = 24.0;
    public static final int GROUP_ATTEMPTS = 3;
    public static final int GROUP_SPREAD = 5;
    public static final int TRAVELLER_ROAD_RADIUS = 8;
    /** Civilians and travellers spawn from tick 0 (inclusive) to this tick of the day (exclusive). */
    public static final long DAY_END = 12000L;

    private static final Set<Block> SPAWNABLE_SURFACES =
            Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.DIRT_PATH, Blocks.PODZOL, Blocks.SAND);
    private static final Set<Block> HOBBIT_SURFACES = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.COARSE_DIRT);

    private NpcSpawnRules() {}

    /** Multiplayer damping {@code max(0.5, 1 / (1 + d x (P - 1)))}; never below half. */
    public static double damping(int players, double d) {
        int extra = Math.max(0, players - 1);
        return Math.max(MIN_DAMPING, 1.0 / (1.0 + d * extra));
    }

    /** Global cap of wild NPCs: 100 per 196 eligible chunks, times the damping, rounded down. */
    public static int cap(int eligibleChunks, int players, double d) {
        double raw = (double) CAP_PER_AREA * Math.max(0, eligibleChunks) / CHUNKS_PER_CAP * damping(players, d);
        return (int) Math.floor(raw + 1.0e-9);
    }

    public static boolean isSpawnableSurface(Block block) {
        return SPAWNABLE_SURFACES.contains(block);
    }

    /**
     * Hobbits stand only on the natural top block (grass-like ground) at or above sea level. The biome's own surface
     * block is not available from the surface rules, so grass, podzol and coarse dirt stand in for it.
     */
    public static boolean hobbitGroundOk(Block ground, int groundY, int seaLevel) {
        return groundY >= seaLevel && HOBBIT_SURFACES.contains(ground);
    }

    /** Civilians (and so travellers) spawn by day only; guards any time. */
    public static boolean timeAllowed(NpcClass npcClass, long dayTime) {
        return npcClass != NpcClass.CIVILIAN || Math.floorMod(dayTime, 24000L) < DAY_END;
    }

    /** Travellers need a road within 8 blocks, unless the road tag has no members at all. */
    public static boolean travellerAllowed(boolean roadTagHasMembers, boolean nearRoad) {
        return !roadTagHasMembers || nearRoad;
    }

    /**
     * What the spawner found at one candidate spot.
     *
     * @param spawnableSurface the ground block is on the spawnable list
     * @param liquid water or lava at the feet or the ground
     * @param skyVisible sky visible from the feet
     * @param light block light plus sky light at the feet
     * @param playerDistanceSq squared distance to the nearest player
     * @param spawnDistanceSq squared distance to the world spawn
     * @param inStructure the column is inside a structure bounding box
     * @param roomToStand the spawn box is free of collisions
     */
    public record Spot(
            boolean spawnableSurface,
            boolean liquid,
            boolean skyVisible,
            int light,
            double playerDistanceSq,
            double spawnDistanceSq,
            boolean inStructure,
            boolean roomToStand) {}

    /** Why a spot was turned down; {@link #NONE} means it is fine. */
    public enum Reject {
        NONE,
        SURFACE,
        LIQUID,
        NO_SKY,
        DARK,
        NEAR_PLAYER,
        NEAR_WORLD_SPAWN,
        IN_STRUCTURE,
        NO_ROOM
    }

    public static Reject check(Spot spot) {
        if (spot.liquid()) {
            return Reject.LIQUID;
        }
        if (!spot.spawnableSurface()) {
            return Reject.SURFACE;
        }
        if (!spot.skyVisible()) {
            return Reject.NO_SKY;
        }
        if (spot.light() < MIN_LIGHT) {
            return Reject.DARK;
        }
        double min = MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE;
        if (spot.playerDistanceSq() < min) {
            return Reject.NEAR_PLAYER;
        }
        if (spot.spawnDistanceSq() < min) {
            return Reject.NEAR_WORLD_SPAWN;
        }
        if (!spot.roomToStand()) {
            return Reject.NO_ROOM;
        }
        if (spot.inStructure()) {
            return Reject.IN_STRUCTURE;
        }
        return Reject.NONE;
    }
}
