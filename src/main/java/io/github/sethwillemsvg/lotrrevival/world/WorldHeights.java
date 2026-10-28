package io.github.sethwillemsvg.lotrrevival.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Height lookups that are safe for columns whose chunk is not loaded yet.
 *
 * <p>{@code Level.getHeight} returns the bottom of the world when the chunk is not loaded, which put ring-portal
 * arrivals at y -64 and made them fall out of the world. These helpers load (and if needed generate) the chunk
 * first.
 */
public final class WorldHeights {
    private WorldHeights() {}

    /** The heightmap value at block {@code x, z}, loading or generating its chunk first. */
    public static int surface(ServerLevel level, Heightmap.Types type, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(type, x & 15, z & 15) + 1;
    }
}
