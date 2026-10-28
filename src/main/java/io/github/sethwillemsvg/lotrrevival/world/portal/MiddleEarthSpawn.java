package io.github.sethwillemsvg.lotrrevival.world.portal;

import io.github.sethwillemsvg.lotrrevival.world.WorldHeights;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthLanding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Middle-earth's spawn arrival point. Respawning and the ring portal both use the same rule so they agree.
 */
public final class MiddleEarthSpawn {
    /** Blocks south (+z) of the landing point where arrivals stand. */
    public static final int SOUTH_OFFSET = 4;

    private MiddleEarthSpawn() {}

    /**
     * The arrival point in {@code level} (Middle-earth), standing on the top solid block: 4 south of the twin
     * portal once it exists, before that 4 south of the landing point.
     */
    public static BlockPos arrivalPoint(ServerLevel level) {
        MiddleEarthLanding.Point landing = MiddleEarthLanding.load(level.getServer().getResourceManager());
        RingPortalPlan.Arrival arrival =
                RingPortalPlan.middleEarthArrival(RingPortals.data(level).middleEarthPortal(), landing);
        int x = arrival.x();
        int z = arrival.z();
        int y = WorldHeights.surface(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }
}
