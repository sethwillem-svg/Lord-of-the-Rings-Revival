package io.github.sethwillemsvg.lotrrevival.world.travel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Picks where a fast traveller stands: the waypoint column, a stored custom position, or the nearest safe column. */
public final class ArrivalFinder {
    /** Half-width of the fallback search area (5 x 5 columns). */
    public static final int SEARCH_RADIUS = 2;

    private ArrivalFinder() {}

    /** Column offsets of the fallback search, nearest to the centre first; ties keep a stable order. */
    public static List<int[]> columnOrder(int radius) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                offsets.add(new int[] {dx, dz});
            }
        }
        offsets.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1])
                .thenComparingInt(o -> o[0])
                .thenComparingInt(o -> o[1]));
        return offsets;
    }

    /** Where the traveller's feet go, as the middle of the block. Generates the arrival chunk if needed. */
    public static Vec3 find(ServerLevel level, TravelTarget target) {
        if (target.customPos().isPresent()) {
            BlockPos stored = target.customPos().get();
            level.getChunk(stored.getX() >> 4, stored.getZ() >> 4);
            if (isSafe(level, stored)) {
                return center(stored);
            }
        }
        return center(findColumn(level, BlockPos.containing(target.x(), 0, target.z())));
    }

    /** The topmost safe standing spot in the column or, failing that, the nearest safe column within the radius. */
    public static BlockPos findColumn(ServerLevel level, BlockPos column) {
        BlockPos centre = topOf(level, column.getX(), column.getZ());
        for (int[] offset : columnOrder(SEARCH_RADIUS)) {
            BlockPos candidate = topOf(level, column.getX() + offset[0], column.getZ() + offset[1]);
            if (isSafe(level, candidate)) {
                return candidate;
            }
        }
        return centre;
    }

    /** The first free block above the highest solid non-leaf block of a column. Generates the chunk. */
    public static BlockPos topOf(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Feet and head blocks are free of collision and fluid, and the block below is solid ground. */
    public static boolean isSafe(ServerLevel level, BlockPos feet) {
        if (!level.isInWorldBounds(feet) || !level.isInWorldBounds(feet.above())) {
            return false;
        }
        return isFree(level, feet) && isFree(level, feet.above()) && isGround(level, feet.below());
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static boolean isGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }
}
