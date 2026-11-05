package io.github.sethwillemsvg.lotrrevival.nodemap.travel;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.sevenstars.middleearth.world.dimension.ModDimensions;

/**
 * Where a traveller lands at a node. {@link #MIDDLE_EARTH} uses Middle-earth's safe-landing search; {@link #TOP_BLOCK} is
 * the test stand-in (game tests run in the overworld near the origin, outside Middle-earth's map border).
 */
@FunctionalInterface
public interface ArrivalFinder {
    /** Middle-earth's search falls back to its world spawn; a landing farther than this from the node counts as failed. */
    double MAX_DRIFT = 96;

    /**
     * @param exact a camp's stored block (used when feet and head are free and the block below is solid), or null
     */
    Optional<Vec3> find(ServerLevel level, double x, double z, @Nullable BlockPos exact, ServerPlayer player);

    static boolean standable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
    }

    ArrivalFinder MIDDLE_EARTH = (level, x, z, exact, player) -> {
        if (exact != null && standable(level, exact)) return Optional.of(Vec3.atBottomCenterOf(exact));
        Optional<Vec3> r = ModDimensions.findSafeMiddleEarthTeleportTarget(level, new Vec3(x, 64, z), player);
        if (r.isPresent() && Math.hypot(r.get().x - x, r.get().z - z) > MAX_DRIFT) {
            LotrLog.warn("travel", "arrival search for {},{} landed {} blocks away at {},{},{} (Middle-earth fallback); treated as failed",
                    Math.round(x), Math.round(z), Math.round(Math.hypot(r.get().x - x, r.get().z - z)), Math.round(r.get().x),
                    Math.round(r.get().y), Math.round(r.get().z));
            return Optional.empty();
        }
        return r;
    };

    ArrivalFinder TOP_BLOCK = (level, x, z, exact, player) -> {
        if (exact != null && standable(level, exact)) return Optional.of(Vec3.atBottomCenterOf(exact));
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
        return Optional.of(Vec3.atBottomCenterOf(top));
    };
}
