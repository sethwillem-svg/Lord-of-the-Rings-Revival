package io.github.sethwillemsvg.lotrrevival.world.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.world.WorldConfig;
import io.github.sethwillemsvg.lotrrevival.world.portal.MiddleEarthRespawn;
import io.github.sethwillemsvg.lotrrevival.world.portal.MiddleEarthRespawn.Choice;
import io.github.sethwillemsvg.lotrrevival.world.portal.MiddleEarthRespawn.Kind;
import io.github.sethwillemsvg.lotrrevival.world.portal.MiddleEarthRespawn.Settings;
import io.github.sethwillemsvg.lotrrevival.world.portal.MiddleEarthRespawn.Spot;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Respawn decisions with injected inputs; the GameTest server has no Middle-earth level. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiddleEarthRespawnTest {
    private static final Settings DEFAULTS = new Settings(true, 5000, 2000, 500, 1500);
    private static final Spot SPAWN = new Spot(100.5, 4.5);
    private static final Spot BED = new Spot(10_000.5, 10_000.5);

    @GameTest(template = "empty")
    public static void farDeathRespawnsNearDeathPointOnLand(GameTestHelper helper) {
        Spot death = new Spot(BED.x() + 6000, BED.z());
        for (int seed = 0; seed < 50; seed++) {
            Choice choice = MiddleEarthRespawn.choose(
                    DEFAULTS, death, Optional.of(BED), SPAWN, RandomSource.create(seed), (x, z) -> true);
            helper.assertTrue(choice.kind() == Kind.FAR_OFFSET, "Expected far offset, got " + choice.kind());
            double d = choice.spot().distanceTo(death);
            helper.assertTrue(d >= 499 && d <= 1502, "Offset " + d + " not within 500-1,500 of the death point");
        }
        // Only some spots are land: the chosen one must pass the check.
        Choice picky = MiddleEarthRespawn.choose(
                DEFAULTS, death, Optional.of(BED), SPAWN, RandomSource.create(7), (x, z) -> x > death.x());
        helper.assertTrue(
                picky.kind() == Kind.FAR_OFFSET && picky.spot().x() > death.x(), "Chosen spot must be land");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nearDeathRespawnsAtBed(GameTestHelper helper) {
        Spot death = new Spot(BED.x() + 1000, BED.z());
        Choice choice = MiddleEarthRespawn.choose(
                DEFAULTS, death, Optional.of(BED), SPAWN, RandomSource.create(1), (x, z) -> true);
        helper.assertTrue(choice.kind() == Kind.BED && choice.spot().equals(BED), "Expected the bed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void noMiddleEarthBedRespawnsAtSpawn(GameTestHelper helper) {
        // An overworld bed is passed in as no bed at all.
        Spot near = new Spot(SPAWN.x() + 1000, SPAWN.z());
        Choice choice = MiddleEarthRespawn.choose(
                DEFAULTS, near, Optional.empty(), SPAWN, RandomSource.create(1), (x, z) -> true);
        helper.assertTrue(choice.kind() == Kind.SPAWN && choice.spot().equals(SPAWN), "Expected Middle-earth spawn");

        Spot far = new Spot(SPAWN.x() + 3000, SPAWN.z());
        Choice farChoice = MiddleEarthRespawn.choose(
                DEFAULTS, far, Optional.empty(), SPAWN, RandomSource.create(1), (x, z) -> true);
        helper.assertTrue(farChoice.kind() == Kind.FAR_OFFSET, "Far from spawn without a bed uses the offset");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fallsBackWhenNoLandOrDisabled(GameTestHelper helper) {
        Spot death = new Spot(BED.x() + 6000, BED.z());
        int[] tries = {0};
        Choice sea = MiddleEarthRespawn.choose(DEFAULTS, death, Optional.of(BED), SPAWN, RandomSource.create(3), (x, z) -> {
            tries[0]++;
            return false;
        });
        helper.assertTrue(sea.kind() == Kind.BED, "All-sea tries must fall back to the bed");
        helper.assertTrue(tries[0] == MiddleEarthRespawn.MAX_TRIES, "Expected 16 tries, got " + tries[0]);

        Settings off = new Settings(false, 5000, 2000, 500, 1500);
        Choice disabled = MiddleEarthRespawn.choose(
                off, death, Optional.of(BED), SPAWN, RandomSource.create(3), (x, z) -> true);
        helper.assertTrue(disabled.kind() == Kind.BED, "Disabled far-death rule keeps the bed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void configDefaultsMatchSpec(GameTestHelper helper) {
        helper.assertTrue(WorldConfig.RESPAWN_BED_DISTANCE.getDefault() == 5000, "bed_distance");
        helper.assertTrue(WorldConfig.RESPAWN_SPAWN_DISTANCE.getDefault() == 2000, "spawn_distance");
        helper.assertTrue(WorldConfig.RESPAWN_MIN_OFFSET.getDefault() == 500, "min_offset");
        helper.assertTrue(WorldConfig.RESPAWN_MAX_OFFSET.getDefault() == 1500, "max_offset");
        helper.assertTrue(WorldConfig.WAYPOINTS_CUSTOM_MIN_Y.getDefault() == -2048, "custom_min_y");
        helper.assertTrue(!WorldConfig.PORTAL_BLAST_BREAKS_BLOCKS.getDefault(), "blast_breaks_blocks");
        helper.succeed();
    }
}
