package io.github.sethwillemsvg.lotrrevival.combat.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.combat.Hitstop;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** combat.Hitstop: freeze ticks by weapon class, plus one for dwarves, never above four. */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HitstopTests {
    private HitstopTests() {}

    private static void expect(GameTestHelper h, String race, String cls, int ticks) {
        int got = Hitstop.ticks(race, cls);
        h.assertTrue(got == ticks, "hitstop for " + race + "/" + cls + " was " + got + ", expected " + ticks);
    }

    @GameTest(template = "empty")
    public static void hitstopTable(GameTestHelper h) {
        expect(h, "man", "dagger", 1);
        expect(h, "man", "sword", 2);
        expect(h, "man", "axe", 3);
        expect(h, "dwarf", "axe", 4);
        expect(h, "dwarf", "longsword", 4);
        expect(h, "orc", null, 1);
        expect(h, "elf", "banner", 2);
        h.succeed();
    }
}
