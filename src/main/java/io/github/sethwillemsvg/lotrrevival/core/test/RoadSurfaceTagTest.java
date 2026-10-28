package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The vanilla road blocks in {@code #lotrrevival:road_surfaces}. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoadSurfaceTagTest {
    @GameTest(template = "empty")
    public static void vanillaRoadSurfaces(GameTestHelper helper) {
        helper.assertTrue(Blocks.DIRT_PATH.defaultBlockState().is(ModTags.Blocks.ROAD_SURFACES), "dirt_path missing");
        helper.assertTrue(
                Blocks.COARSE_DIRT.defaultBlockState().is(ModTags.Blocks.ROAD_SURFACES), "coarse_dirt missing");
        helper.assertTrue(
                !Blocks.GRAVEL.defaultBlockState().is(ModTags.Blocks.ROAD_SURFACES), "gravel must stay out");
        helper.assertTrue(
                !Blocks.GRASS_BLOCK.defaultBlockState().is(ModTags.Blocks.ROAD_SURFACES), "grass must stay out");
        helper.succeed();
    }
}
