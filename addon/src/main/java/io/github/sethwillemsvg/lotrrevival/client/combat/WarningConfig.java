package io.github.sethwillemsvg.lotrrevival.client.combat;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Attack warning marker settings: lotr_revival-client.toml, section [combat.warning] (specs/addon/combat-parry-warning.md). */
public final class WarningConfig {
    private WarningConfig() {}

    public static final ModConfigSpec SPEC;
    static final ModConfigSpec.BooleanValue ENABLED, SHOW_OTHERS, THROUGH_WALLS, OFFSCREEN_ARROW;
    static final ModConfigSpec.IntValue RANGE, MAX_DRAWN;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("combat").push("warning");
        ENABLED = b.comment("Show a marker over NPCs winding up a melee swing").define("enabled", true);
        SHOW_OTHERS = b.comment("Also show (smaller, faded) markers for swings at other players and NPCs").define("show_others", true);
        RANGE = b.comment("Blocks from the camera").defineInRange("range", 24, 4, 128);
        THROUGH_WALLS = b.comment("Draw markers through blocks").define("through_walls", false);
        OFFSCREEN_ARROW = b.comment("An arrow at the screen edge for swings at you from outside the view").define("offscreen_arrow", true);
        MAX_DRAWN = b.comment("Most markers drawn at once (the nearest; swings at you always show)").defineInRange("max_drawn", 32, 1, 256);
        b.pop().pop();
        SPEC = b.build();
    }

    private static boolean get(ModConfigSpec.BooleanValue v) {
        try {
            return v.get();
        } catch (IllegalStateException notLoaded) {
            return v.getDefault();
        }
    }

    private static int get(ModConfigSpec.IntValue v) {
        try {
            return v.get();
        } catch (IllegalStateException notLoaded) {
            return v.getDefault();
        }
    }

    public static boolean enabled() { return get(ENABLED); }
    public static boolean showOthers() { return get(SHOW_OTHERS); }
    public static boolean throughWalls() { return get(THROUGH_WALLS); }
    public static boolean offscreenArrow() { return get(OFFSCREEN_ARROW); }
    public static int range() { return get(RANGE); }
    public static int maxDrawn() { return get(MAX_DRAWN); }

    static void logInUse() {
        LotrLog.info("combat", "client warning: enabled={} range={} max_drawn={} show_others={} through_walls={} offscreen_arrow={}",
                enabled(), range(), maxDrawn(), showOthers(), throughWalls(), offscreenArrow());
    }
}
