package io.github.sethwillemsvg.lotrrevival.world.road;

import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/**
 * The road-safe filter for trees and plants: a vanilla {@code block_predicate_filter} that rejects a position whose
 * block below is in {@code #lotrrevival:road_surfaces}. Every tree and plant placed feature of the Middle-earth
 * biomes builds its placement list through {@link #roadSafe(List)}. (The tag is written by Core.)
 */
public final class RoadVegetation {
    private RoadVegetation() {}

    /** Passes when the block below the position is not a road surface. */
    public static BlockPredicate notOnRoad() {
        return rejecting(BlockPredicate.matchesTag(new BlockPos(0, -1, 0), ModTags.Blocks.ROAD_SURFACES));
    }

    /** Wraps a "block below is road" predicate into its negation (kept separate so tests can inject one). */
    public static BlockPredicate rejecting(BlockPredicate roadBelow) {
        return BlockPredicate.not(roadBelow);
    }

    /** The placement modifier for the filter. */
    public static PlacementModifier filter() {
        return BlockPredicateFilter.forPredicate(notOnRoad());
    }

    /**
     * Adds the filter to a placement list, before a trailing {@link BiomeFilter} when there is one. Call it on the
     * list after the heightmap modifier, since the filter looks at the block below the final position.
     */
    public static List<PlacementModifier> roadSafe(List<PlacementModifier> modifiers) {
        List<PlacementModifier> out = new ArrayList<>(modifiers);
        int at = out.size();
        if (at > 0 && out.get(at - 1) instanceof BiomeFilter) {
            at--;
        }
        out.add(at, filter());
        return out;
    }
}
