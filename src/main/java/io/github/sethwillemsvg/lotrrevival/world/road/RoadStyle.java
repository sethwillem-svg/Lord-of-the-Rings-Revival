package io.github.sethwillemsvg.lotrrevival.world.road;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A road look: a weighted surface mix for the top layer and a base block for the three layers below
 * ({@code specs/world/waypoints-travel.md}, Road styles).
 *
 * @param id style id, for example {@code path}
 * @param surface the top layer mix, at least one entry
 * @param base the block of the 3 layers below the top
 */
public record RoadStyle(String id, List<Surface> surface, BlockState base) {
    /**
     * One entry of the surface mix.
     *
     * @param block the full block of the top layer
     * @param slab the matching bottom slab for a half step, or null when a half step is a full block of the pick
     * @param weight relative weight in the mix
     */
    public record Surface(BlockState block, @Nullable BlockState slab, int weight) {}

    /** Picks an entry for a roll in [0, 1). */
    public Surface pick(double roll) {
        int total = 0;
        for (Surface s : surface) {
            total += s.weight();
        }
        double target = roll * total;
        for (Surface s : surface) {
            target -= s.weight();
            if (target < 0) {
                return s;
            }
        }
        return surface.get(surface.size() - 1);
    }
}
