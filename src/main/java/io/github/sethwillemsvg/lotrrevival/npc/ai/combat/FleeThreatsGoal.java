package io.github.sethwillemsvg.lotrrevival.npc.ai.combat;

import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Civilian flee goal: runs from the nearest entity the predicate calls a threat within range. Vanilla's avoid goal
 * runs the same movement but filters through targeting conditions that need line of sight and attackability; fleeing
 * needs neither, so the search is done here.
 */
final class FleeThreatsGoal extends AvoidEntityGoal<LivingEntity> {
    private final Predicate<LivingEntity> threat;

    FleeThreatsGoal(PathfinderMob mob, Predicate<LivingEntity> threat, float range, double speed) {
        super(mob, LivingEntity.class, threat, range, speed, speed, entity -> true);
        this.threat = threat;
    }

    @Override
    public boolean canUse() {
        LivingEntity nearest = null;
        double best = (double) maxDist * maxDist;
        for (LivingEntity other : mob.level()
                .getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(maxDist, 3.0, maxDist))) {
            double dist = mob.distanceToSqr(other);
            if (dist <= best && other.isAlive() && threat.test(other)) {
                nearest = other;
                best = dist;
            }
        }
        toAvoid = nearest;
        if (nearest == null) {
            return false;
        }
        Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, nearest.position());
        if (away == null || nearest.distanceToSqr(away.x, away.y, away.z) < nearest.distanceToSqr(mob)) {
            return false;
        }
        path = pathNav.createPath(away.x, away.y, away.z, 0);
        return path != null;
    }
}
