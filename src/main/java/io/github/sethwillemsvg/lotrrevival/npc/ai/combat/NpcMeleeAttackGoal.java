package io.github.sethwillemsvg.lotrrevival.npc.ai.combat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * Vanilla chase-and-hit melee, but the swing cooldown and attack reach follow the held weapon's class
 * ({@link NpcMelee}). Daggers swing fast and short, pikes slow and far.
 */
public final class NpcMeleeAttackGoal extends MeleeAttackGoal {
    private long nextAttackTick;

    public NpcMeleeAttackGoal(PathfinderMob mob, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(mob, speedModifier, followingTargetEvenIfNotSeen);
    }

    @Override
    public void start() {
        super.start();
        nextAttackTick = 0;
    }

    @Override
    protected boolean canPerformAttack(LivingEntity target) {
        return mob.level().getGameTime() >= nextAttackTick
                && NpcMelee.inReach(mob, target)
                && mob.getSensing().hasLineOfSight(target);
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target) {
        if (canPerformAttack(target)) {
            nextAttackTick = mob.level().getGameTime() + NpcMelee.cooldownTicks(mob.getMainHandItem());
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
        }
    }
}
