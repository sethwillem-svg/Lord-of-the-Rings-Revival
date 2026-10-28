package io.github.sethwillemsvg.lotrrevival.core.combat.gear;

/**
 * Gear holder for ticket #110: bronze and iron weapon sets, bronze tools and armour (vanilla table). Empty until that ticket fills it with
 * {@code Gear.*} calls; {@code CombatItems.init} already calls {@link #init()}, so the ticket
 * never touches {@code CoreMod}.
 */
public final class GeneralGear {
    private GeneralGear() {}

    /** Loads the class so its static gear fields register. Called from {@code CombatItems.init}. */
    public static void init() {}
}
