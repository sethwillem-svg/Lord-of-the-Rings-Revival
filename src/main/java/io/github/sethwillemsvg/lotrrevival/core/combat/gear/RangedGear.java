package io.github.sethwillemsvg.lotrrevival.core.combat.gear;

/**
 * Gear holder for ticket #120 and #121: faction bows, the hobbit sling, bronze and iron crossbows. Empty until that ticket fills it with
 * {@code Gear.*} calls; {@code CombatItems.init} already calls {@link #init()}, so the ticket
 * never touches {@code CoreMod}.
 */
public final class RangedGear {
    private RangedGear() {}

    /** Loads the class so its static gear fields register. Called from {@code CombatItems.init}. */
    public static void init() {
        CrossbowGear.init();
    }
}
