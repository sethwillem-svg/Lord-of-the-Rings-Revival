package io.github.sethwillemsvg.lotrrevival.npc.ai.combat;

import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace;
import io.github.sethwillemsvg.lotrrevival.npc.race.NpcTemperament;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;

/**
 * Goal installer: hostility targeting, faction warfare, fleeing, melee and idle healing. Installed from the end of
 * the NpcEntity constructor.
 */
public final class CombatGoals {
    private CombatGoals() {}

    public static void install(NpcEntity npc, GoalSelector goals, GoalSelector targets, NpcRace race) {
        boolean fighter = NpcCombat.isFighter(npc);
        boolean canFightBack = fighter || race.temperament() == NpcTemperament.FIGHT_BACK_FLEE_LOW;

        if (canFightBack) {
            targets.addGoal(1, new NpcTargetGoal(npc, fighter));
            goals.addGoal(4, new MeleeAttackGoal(npc, NpcCombat.CHASE_SPEED, false));
        }
        if (fighter) {
            goals.addGoal(2, new AvoidEntityGoal<>(
                    npc,
                    Creeper.class,
                    NpcCombat.CREEPER_DISTANCE,
                    race.fleeSpeedMultiplier(),
                    race.fleeSpeedMultiplier()));
        } else {
            goals.addGoal(2, new FleeThreatsGoal(
                    npc,
                    threat -> isThreat(npc, threat) && shouldFlee(npc, race),
                    NpcCombat.FLEE_RANGE,
                    race.fleeSpeedMultiplier()));
        }
        goals.addGoal(6, new HealWhileIdleGoal(npc));
    }

    /** Hobbit civilians always flee; man civilians flee unless they are fighting something, or when low on health. */
    private static boolean shouldFlee(NpcEntity npc, NpcRace race) {
        return race.temperament() == NpcTemperament.FLEE || npc.getTarget() == null || NpcCombat.isLowHealth(npc);
    }

    /** What civilians run from: hostile players, monsters and enemy NPCs. */
    private static boolean isThreat(NpcEntity npc, LivingEntity other) {
        if (other == npc) {
            return false;
        }
        if (other instanceof Player player) {
            return NpcCombat.isHostilePlayer(npc, player);
        }
        return NpcCombat.isMonster(other) || FactionRelations.isHostile(npc, other);
    }
}
