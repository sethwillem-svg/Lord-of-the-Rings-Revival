package io.github.sethwillemsvg.lotrrevival.world.portal;

import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthLanding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Middle-earth's spawn arrival point. Respawning and the ring portal both call this so they agree. Until the
 * portal ticket lands it is the landing point; that ticket makes it prefer the twin portal.
 */
public final class MiddleEarthSpawn {
    /** Blocks south (+z) of the landing point where arrivals stand. */
    public static final int SOUTH_OFFSET = 4;

    private MiddleEarthSpawn() {}

    /** The arrival point in {@code level} (Middle-earth), standing on the top solid block. */
    public static BlockPos arrivalPoint(ServerLevel level) {
        MiddleEarthLanding.Point landing = MiddleEarthLanding.load(level.getServer().getResourceManager());
        int x = landing.x();
        int z = landing.z() + SOUTH_OFFSET;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }
}
