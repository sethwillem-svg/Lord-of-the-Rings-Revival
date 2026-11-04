package io.github.sethwillemsvg.lotrrevival.combat.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.combat.WeaponClass;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** combat.WeaponClass on real Middle-earth items: a troll's mace swings like an axe; ordinary weapons keep their class. */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WeaponClassTests {
    private WeaponClassTests() {}

    private static void expect(GameTestHelper h, String item, String cls) {
        var id = ResourceLocation.parse(item);
        h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), item + " is not registered");
        String got = WeaponClass.of(new ItemStack(BuiltInRegistries.ITEM.get(id)));
        h.assertTrue(cls.equals(got), item + " is " + got + ", expected " + cls);
    }

    @GameTest(template = "empty")
    public static void weaponClasses(GameTestHelper h) {
        expect(h, "middle-earth:troll_mace", "axe");
        expect(h, "middle-earth:bronze_axe", "axe");
        expect(h, "minecraft:iron_sword", "sword");
        h.assertTrue(WeaponClass.of(new ItemStack(Items.STICK)) == null, "a stick has a weapon class");
        h.succeed();
    }
}
