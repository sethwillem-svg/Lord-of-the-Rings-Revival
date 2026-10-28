package io.github.sethwillemsvg.lotrrevival.world.portal;

import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthLanding;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * Where ring portal travel puts people. Pure functions over stored data so they can be tested without a
 * Middle-earth level.
 */
public final class RingPortalPlan {
    /** Blocks south (+z) of a portal where arrivals stand. */
    public static final int SOUTH_OFFSET = 4;

    private RingPortalPlan() {}

    /** A column to arrive in; {@code resolveHeight} says to stand on the top solid block, else use {@code y}. */
    public record Arrival(int x, int y, int z, boolean resolveHeight) {}

    /** Middle-earth arrival: 4 south of the twin portal once it exists, else 4 south of the landing point. */
    public static Arrival middleEarthArrival(Optional<BlockPos> twin, MiddleEarthLanding.Point landing) {
        int x = twin.map(BlockPos::getX).orElse(landing.x());
        int z = twin.map(BlockPos::getZ).orElse(landing.z());
        return new Arrival(x, 0, z + SOUTH_OFFSET, true);
    }

    /** Return arrival: 4 south of the overworld portal, else the overworld spawn. */
    public static Arrival overworldReturn(Optional<BlockPos> overworldPortal, BlockPos overworldSpawn) {
        if (overworldPortal.isPresent()) {
            BlockPos portal = overworldPortal.get();
            return new Arrival(portal.getX(), 0, portal.getZ() + SOUTH_OFFSET, true);
        }
        return new Arrival(overworldSpawn.getX(), overworldSpawn.getY(), overworldSpawn.getZ(), false);
    }

    /** The twin is built on arrival when none was ever stored, or when the stored one is gone. */
    public static boolean needsTwin(Optional<BlockPos> storedTwin, boolean entityPresent) {
        return storedTwin.isEmpty() || !entityPresent;
    }

    /** The column the twin stands over: the stored one, else the landing point. */
    public static BlockPos twinColumn(Optional<BlockPos> storedTwin, MiddleEarthLanding.Point landing) {
        return storedTwin.orElse(new BlockPos(landing.x(), 0, landing.z()));
    }
}
