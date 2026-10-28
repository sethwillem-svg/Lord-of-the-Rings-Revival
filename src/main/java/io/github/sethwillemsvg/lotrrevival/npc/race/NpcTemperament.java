package io.github.sethwillemsvg.lotrrevival.npc.race;

/** How a civilian reacts when threatened. Guards and captains always fight. */
public enum NpcTemperament {
    /** Runs away. */
    FLEE,
    /** Fights back, and flees below 30% health. */
    FIGHT_BACK_FLEE_LOW
}
