package io.github.sethwillemsvg.lotrrevival.npc.ai;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;

/** Goal installer: swimming, panic while burning, and doors and gates. */
public final class SafetyGoals {
    /** Panic-run speed multiplier while on fire. */
    public static final double FIRE_PANIC_SPEED = 1.5;

    private SafetyGoals() {}

    public static void install(PathfinderMob mob, GoalSelector goals) {
        goals.addGoal(0, new FloatGoal(mob));
        goals.addGoal(1, new FirePanicGoal(mob));
        // Wooden doors only: vanilla treats iron doors as not openable by hand.
        goals.addGoal(2, new OpenDoorGoal(mob, true));
        goals.addGoal(2, new FenceGateGoal(mob));
    }

    /** Panic that triggers only while the mob is on fire (hits are handled by later retaliation tickets). */
    private static final class FirePanicGoal extends PanicGoal {
        FirePanicGoal(PathfinderMob mob) {
            super(mob, FIRE_PANIC_SPEED);
        }

        @Override
        protected boolean shouldPanic() {
            return mob.isOnFire();
        }
    }
}
