package io.github.sethwillemsvg.lotrrevival.world.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.world.WorldHeights;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression: arrivals in unloaded chunks were placed at the bottom of the world and fell out. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public class WorldHeightsTest {
    @GameTest(template = "empty")
    public static void surfaceLoadsTheChunkFirst(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int x = 400_000 + 7;
        int z = -400_000 + 3;
        helper.assertFalse(level.hasChunk(x >> 4, z >> 4), "The far chunk must start unloaded");
        int unsafe = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        helper.assertTrue(unsafe == level.getMinBuildHeight(),
                "Level.getHeight on an unloaded chunk should report the world bottom (the bug), got " + unsafe);
        int safe = WorldHeights.surface(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        helper.assertTrue(safe > level.getMinBuildHeight(),
                "WorldHeights.surface must load the chunk and find real ground, got " + safe);
        helper.assertTrue(safe == level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
                "Once loaded both lookups must agree");
        helper.succeed();
    }
}
