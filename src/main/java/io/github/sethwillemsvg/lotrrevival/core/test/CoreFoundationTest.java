package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Proves the mod loaded and the G1 registers and keys exist. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreFoundationTest {
    @GameTest(template = "empty")
    public static void modLoadedAndRegistersExist(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(LOTRRevival.MOD_ID), "mod not loaded");
        helper.assertTrue(ModRegistries.BLOCKS.getRegistryName() != null, "blocks register missing");
        helper.assertTrue(
                ModRegistries.ATTACHMENT_TYPES.getRegistryName() != null, "attachment register missing");
        helper.assertTrue(ModRegistries.DATA_COMPONENTS.getRegistryName() != null, "data component register missing");
        helper.assertTrue(
                ModDimensions.MIDDLE_EARTH.location().equals(LOTRRevival.id("middle_earth")),
                "dimension key wrong");
        helper.succeed();
    }
}
