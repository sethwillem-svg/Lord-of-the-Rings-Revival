package io.github.sethwillemsvg.lotrrevival.combat;

import org.jetbrains.annotations.Nullable;

/** How many ticks a landed NPC hit freezes the swing for: heavier weapons bite longer, dwarves one tick more, never above four. */
public final class Hitstop {
    private static final int MAX_TICKS = 4;

    private Hitstop() {}

    public static int ticks(String race, @Nullable String weaponClass) {
        int base = switch (weaponClass == null ? "unarmed" : weaponClass) {
            case "dagger", "unarmed" -> 1;
            case "sword", "spear" -> 2;
            case "axe", "longsword" -> 3;
            default -> 2;
        };
        if (race.equals("dwarf")) base++;
        return Math.min(base, MAX_TICKS);
    }
}
