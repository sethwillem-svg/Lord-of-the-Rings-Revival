package io.github.sethwillemsvg.lotrrevival.npc.routine;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace;
import net.minecraft.world.entity.ai.goal.GoalSelector;

/**
 * Goal installer: the day/night routine. Priority 6 sits just above the idle stroll (7), so a routine that restricts
 * where an NPC may wander replaces the free stroll, while NPCs with no routine (see {@link NpcRoutine#plan}) fall
 * through to it.
 */
public final class RoutineGoals {
    public static final int PRIORITY = 6;

    private RoutineGoals() {}

    public static void install(NpcEntity npc, GoalSelector goals, NpcRace race) {
        goals.addGoal(PRIORITY, new RoutineGoal(npc, race.wanderSpeedMultiplier()));
    }
}
