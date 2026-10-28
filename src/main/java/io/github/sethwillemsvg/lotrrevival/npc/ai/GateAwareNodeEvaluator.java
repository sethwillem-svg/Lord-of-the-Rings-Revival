package io.github.sethwillemsvg.lotrrevival.npc.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Walk evaluator that treats closed fence gates like closed wooden doors, so paths may go through them. */
public final class GateAwareNodeEvaluator extends WalkNodeEvaluator {
    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        PathType type = super.getPathType(context, x, y, z);
        if (type == PathType.FENCE
                && context.getBlockState(new BlockPos(x, y, z)).getBlock() instanceof FenceGateBlock) {
            return PathType.DOOR_WOOD_CLOSED;
        }
        return type;
    }
}
