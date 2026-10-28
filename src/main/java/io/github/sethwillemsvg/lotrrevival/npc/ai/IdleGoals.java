package io.github.sethwillemsvg.lotrrevival.npc.ai;

import io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;

/** Goal installer: idle behavior (wander and look at nearby players). */
public final class IdleGoals {
    /** Blocks within which an NPC may turn to look at a player. */
    public static final float LOOK_RANGE = 8.0f;
    /** Chance per tick to start looking. Vanilla holds the look for 40 to 80 ticks. */
    public static final float LOOK_CHANCE = 0.02f;
    /** Average ticks between strolls (vanilla default) and the stroll radius (vanilla default, 10 blocks). */
    public static final int STROLL_INTERVAL = 120;

    private IdleGoals() {}

    public static void install(PathfinderMob mob, GoalSelector goals, NpcRace race) {
        WaterAvoidingRandomStrollGoal stroll = new WaterAvoidingRandomStrollGoal(mob, race.wanderSpeedMultiplier());
        stroll.setInterval(STROLL_INTERVAL);
        goals.addGoal(7, stroll);
        goals.addGoal(8, new LookAtPlayerGoal(mob, Player.class, LOOK_RANGE, LOOK_CHANCE));
    }
}
