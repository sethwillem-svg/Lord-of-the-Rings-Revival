package io.github.sethwillemsvg.lotrrevival.npc.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

/** Ground navigation that opens wooden doors and routes through fence gates. */
public final class NpcGroundNavigation extends GroundPathNavigation {
    public NpcGroundNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanOpenDoors(true);
        setCanFloat(true);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new GateAwareNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }
}
