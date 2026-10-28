package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.ArmorPieces;
import io.github.sethwillemsvg.lotrrevival.core.combat.ArmorSet;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.ShireBreeGear;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTableRecipes;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTables;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Shire and Bree armour: point tables, durability, faction tag and faction-table recipes (#112). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShireBreeGearTest {
    private ShireBreeGearTest() {}

    private static void check(GameTestHelper helper, ArmorPieces pieces, ArmorSet set, int[] points, int[] durability) {
        List<ArmorItem> items =
                List.of(pieces.helmet().get(), pieces.chestplate().get(), pieces.leggings().get(), pieces.boots().get());
        for (int i = 0; i < 4; i++) {
            ArmorItem item = items.get(i);
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(item.getDefense() == points[i], set.id() + " piece " + i + " defense " + item.getDefense());
            helper.assertTrue(
                    stack.getMaxDamage() == durability[i],
                    set.id() + " piece " + i + " durability " + stack.getMaxDamage());
            helper.assertTrue(item.getToughness() == 0.0f, set.id() + " toughness");
            helper.assertTrue(stack.is(ModTags.Items.armor(set.faction())), set.id() + " piece " + i + " missing armor tag");
        }
    }

    @GameTest(template = "empty")
    public static void shireArmourMatchesSpec(GameTestHelper helper) {
        // p 0.4 -> 1/4/3/1; 200 uses -> multiplier 12 -> 11/16/15/13 x 12
        check(helper, ShireBreeGear.SHIRE, ShireBreeGear.SHIRE_SET, new int[] {1, 4, 3, 1}, new int[] {132, 192, 180, 156});
        helper.assertTrue(ShireBreeGear.SHIRE_SET.enchantability() == 15, "leather set enchantability 15");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breeArmourMatchesSpec(GameTestHelper helper) {
        // p 0.5 -> 2/5/4/2; 250 uses -> multiplier 15 -> 11/16/15/13 x 15
        check(helper, ShireBreeGear.BREE, ShireBreeGear.BREE_SET, new int[] {2, 5, 4, 2}, new int[] {165, 240, 225, 195});
        helper.assertTrue(ShireBreeGear.BREE_SET.enchantability() == 9, "metal set enchantability 9");
        helper.succeed();
    }

    private static CraftingInput helmetGrid(Item material) {
        ItemStack m = new ItemStack(material);
        ItemStack e = ItemStack.EMPTY;
        return CraftingInput.of(3, 3, List.of(m, m.copy(), m.copy(), m.copy(), e, m.copy(), e, e, e));
    }

    private static void recipeScoped(
            GameTestHelper helper, String id, Item material, FactionTables.Entry own, FactionTables.Entry other) {
        var level = helper.getLevel();
        var manager = level.getServer().getRecipeManager();
        List<RecipeHolder<CraftingRecipe>> faction = manager.getAllRecipesFor(FactionTableRecipes.TYPE.get());
        CraftingInput grid = helmetGrid(material);
        var onOwn = FactionTableRecipes.match(faction, grid, level, own.block().get());
        helper.assertTrue(onOwn.isPresent(), id + " must craft on its own table");
        helper.assertTrue(
                onOwn.get().value().getResultItem(level.registryAccess()).is(BuiltInRegistries.ITEM.get(LOTRRevival.id(id))),
                id + " result");
        helper.assertTrue(
                FactionTableRecipes.match(faction, grid, level, other.block().get()).isEmpty(),
                id + " must not craft on another faction table");
        helper.assertTrue(
                FactionTableRecipes.match(faction, grid, level, Blocks.CRAFTING_TABLE).isEmpty(),
                id + " must not craft on the vanilla table");
        // the same grid is a vanilla armour recipe (leather/iron helmet); the vanilla table must not give ours
        helper.assertTrue(
                manager.getRecipeFor(RecipeType.CRAFTING, grid, level)
                        .map(r -> !r.value().getResultItem(level.registryAccess()).is(onOwn.get().value().getResultItem(level.registryAccess()).getItem()))
                        .orElse(true),
                id + " must not be a vanilla crafting recipe");
    }

    @GameTest(template = "empty")
    public static void shireHelmetCraftsOnlyOnHobbitTable(GameTestHelper helper) {
        recipeScoped(helper, "shire_helmet", Items.LEATHER, FactionTables.HOBBIT, FactionTables.BREE);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breeHelmetCraftsOnlyOnBreeTable(GameTestHelper helper) {
        recipeScoped(helper, "bree_helmet", Items.IRON_INGOT, FactionTables.BREE, FactionTables.HOBBIT);
        helper.succeed();
    }
}
