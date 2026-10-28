package io.github.sethwillemsvg.lotrrevival.npc.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

/** Opens a closed fence gate on the current path when close to it, and closes it again after passing. */
public final class FenceGateGoal extends Goal {
    private static final double REACH_SQR = 2.25;

    private final Mob mob;
    private BlockPos gatePos = BlockPos.ZERO;
    private boolean passed;
    private double openDirX;
    private double openDirZ;

    public FenceGateGoal(Mob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (!(mob.getNavigation() instanceof GroundPathNavigation navigation) || !navigation.canOpenDoors()) {
            return false;
        }
        Path path = navigation.getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int last = Math.min(path.getNextNodeIndex() + 2, path.getNodeCount());
        for (int i = path.getNextNodeIndex(); i < last; i++) {
            Node node = path.getNode(i);
            BlockPos pos = new BlockPos(node.x, node.y, node.z);
            if (isClosedGate(mob.level().getBlockState(pos))
                    && mob.distanceToSqr(pos.getX() + 0.5, mob.getY(), pos.getZ() + 0.5) <= REACH_SQR) {
                gatePos = pos;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return !passed && mob.level().getBlockState(gatePos).getBlock() instanceof FenceGateBlock;
    }

    @Override
    public void start() {
        passed = false;
        openDirX = gatePos.getX() + 0.5 - mob.getX();
        openDirZ = gatePos.getZ() + 0.5 - mob.getZ();
        setOpen(true);
    }

    @Override
    public void stop() {
        BlockPos feet = mob.blockPosition();
        if (!feet.equals(gatePos)) {
            setOpen(false);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        double dx = gatePos.getX() + 0.5 - mob.getX();
        double dz = gatePos.getZ() + 0.5 - mob.getZ();
        if (openDirX * dx + openDirZ * dz < 0.0 && !mob.blockPosition().equals(gatePos)) {
            passed = true;
        }
    }

    private static boolean isClosedGate(BlockState state) {
        return state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN);
    }

    private void setOpen(boolean open) {
        BlockState state = mob.level().getBlockState(gatePos);
        if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN) != open) {
            mob.level().setBlock(gatePos, state.setValue(FenceGateBlock.OPEN, open), 10);
            mob.level().levelEvent(null, open ? 1008 : 1014, gatePos, 0);
        }
    }
}
