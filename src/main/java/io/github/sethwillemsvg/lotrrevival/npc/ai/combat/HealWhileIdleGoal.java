package io.github.sethwillemsvg.lotrrevival.npc.ai.combat;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Always-running flagless goal: trader roles and hired units heal 1 HP every 40 ticks while damaged and without a
 * target. Everyone else never regenerates (vanilla mobs do not, so nothing happens for them).
 */
final class HealWhileIdleGoal extends Goal {
    private final NpcEntity npc;
    private int ticks;

    HealWhileIdleGoal(NpcEntity npc) {
        this.npc = npc;
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    /** Counts real ticks; vanilla otherwise ticks running goals every second tick. */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void tick() {
        if (npc.getTarget() != null || npc.getHealth() >= npc.getMaxHealth() || !NpcCombat.canHeal(npc)) {
            ticks = 0;
            return;
        }
        if (++ticks >= NpcCombat.HEAL_INTERVAL_TICKS) {
            ticks = 0;
            npc.heal(1.0f);
        }
    }
}
