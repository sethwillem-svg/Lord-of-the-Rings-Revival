package io.github.sethwillemsvg.lotrrevival.client.anim.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.client.anim.NpcAnimatable;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.function.Predicate;

/**
 * client.anim.NpcAnimatable's version operator (specs/addon/npc-formation-stances.md): a stance plays its weapon group's
 * variant, then its shield version, where the race file has them, and falls back to the plain stance where it does not.
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StanceVariantTests {
    private StanceVariantTests() {}

    /** The stance clips of a race file as the build writes it today. */
    private static final Set<String> RACE_FILE = Set.of("idle", "idle_shield", "walk", "walk_shield", "guard", "guard_shield",
            "guard_spear", "guard_spear_shield", "guard_bow", "march", "march_shield", "march_spear", "march_spear_shield",
            "march_bow", "shield_brace", "shield_brace_spear", "spear_brace");
    /** An older race file: the four stances and their first shield versions only. */
    private static final Set<String> OLD_FILE = Set.of("idle", "idle_shield", "guard", "guard_shield", "march", "march_shield",
            "shield_brace", "spear_brace");

    private static void expect(GameTestHelper h, Set<String> file, String name, String weaponClass, boolean shield, String want) {
        Predicate<String> has = file::contains;
        String got = NpcAnimatable.version(name, NpcAnimatable.stanceGroup(weaponClass), shield, has);
        h.assertTrue(want.equals(got), name + " with " + weaponClass + (shield ? " and a shield" : "") + " plays " + got + ", expected " + want);
    }

    @GameTest(template = "empty")
    public static void stanceVariants(GameTestHelper h) {
        // spear and shield in a shield wall, and in a spear line: braced behind the shield, spear over it
        expect(h, RACE_FILE, "shield_brace", "spear", true, "shield_brace_spear");
        expect(h, RACE_FILE, "spear_brace", "spear", true, "shield_brace_spear");
        // a bow in a line plays its own guard (two-handed: no shield version), and marches with its own carry
        expect(h, RACE_FILE, "guard", "bow", false, "guard_bow");
        expect(h, RACE_FILE, "guard", "longbow", true, "guard_bow");
        expect(h, RACE_FILE, "march", "crossbow", false, "march_bow");
        // spears guard and march their own way, with a shield too; blades and axes play the plain stances
        expect(h, RACE_FILE, "guard", "spear", false, "guard_spear");
        expect(h, RACE_FILE, "march", "spear", true, "march_spear_shield");
        expect(h, RACE_FILE, "guard", "sword", true, "guard_shield");
        expect(h, RACE_FILE, "guard", "axe", false, "guard");
        expect(h, RACE_FILE, "spear_brace", "spear", false, "spear_brace");
        // movement clips have no group variants: only the shield version
        expect(h, RACE_FILE, "walk", "spear", true, "walk_shield");
        // a race file without guard_bow (an older build) falls back to guard; without shield_brace_spear, to the plain brace
        expect(h, OLD_FILE, "guard", "bow", false, "guard");
        expect(h, OLD_FILE, "march", "spear", true, "march_shield");
        expect(h, OLD_FILE, "shield_brace", "spear", true, "shield_brace");
        expect(h, OLD_FILE, "spear_brace", "spear", true, "spear_brace");
        h.succeed();
    }
}
