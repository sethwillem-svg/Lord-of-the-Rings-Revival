package io.github.sethwillemsvg.lotrrevival.core.combat.gear;

/**
 * Gear holder for ticket #116: Gondor weapons including the lance, Gondor and levy armour. Empty until
 * that ticket fills it with {@code Gear.*} calls; {@code CombatItems.init} already calls {@link #init()},
 * so the ticket never touches {@code CoreMod}.
 */
public final class GondorGear {
    private GondorGear() {}

    /** Loads the class so its static gear fields register. Called from {@code CombatItems.init}. */
    public static void init() {}
}
