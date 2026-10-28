package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.material.MaterialBlocks;
import io.github.sethwillemsvg.lotrrevival.core.material.MaterialItems;
import io.github.sethwillemsvg.lotrrevival.core.material.Metal;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Metals and ores: recipes and tool requirements. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialsTest {
    private static ItemStack craft(GameTestHelper helper, Item... items) {
        List<ItemStack> stacks = java.util.Arrays.stream(items).map(ItemStack::new).toList();
        CraftingInput input = CraftingInput.of(items.length, 1, stacks);
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(holder -> holder.value().assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    private static <T extends net.minecraft.world.item.crafting.AbstractCookingRecipe> boolean cooks(
            GameTestHelper helper, RecipeType<T> type, Item in, Item out, float xp) {
        var found = helper.getLevel().getRecipeManager().getRecipeFor(type, new SingleRecipeInput(new ItemStack(in)), helper.getLevel());
        if (found.isEmpty()) {
            return false;
        }
        RecipeHolder<T> holder = found.get();
        return holder.value().assemble(new SingleRecipeInput(new ItemStack(in)), helper.getLevel().registryAccess()).is(out)
                && holder.value().getExperience() == xp;
    }

    @GameTest(template = "empty")
    public static void bronzeFromCopperAndTin(GameTestHelper helper) {
        Item bronze = MaterialItems.INGOT.get(Metal.BRONZE).get();
        ItemStack result = craft(helper, Items.COPPER_INGOT, MaterialItems.INGOT.get(Metal.TIN).get());
        helper.assertTrue(result.is(bronze) && result.getCount() == 1, "copper + tin ingot gives 1 bronze, got " + result);
        ItemStack swapped = craft(helper, MaterialItems.INGOT.get(Metal.TIN).get(), Items.COPPER_INGOT);
        helper.assertTrue(swapped.is(bronze), "order does not matter, got " + swapped);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smeltingRecipes(GameTestHelper helper) {
        Item tin = MaterialItems.RAW.get(Metal.TIN).get();
        Item tinIngot = MaterialItems.INGOT.get(Metal.TIN).get();
        helper.assertTrue(cooks(helper, RecipeType.SMELTING, tin, tinIngot, 0.7f), "raw tin smelts to tin ingot, 0.7 xp");
        helper.assertTrue(cooks(helper, RecipeType.BLASTING, tin, tinIngot, 0.7f), "raw tin blasts to tin ingot");
        helper.assertTrue(
                cooks(helper, RecipeType.SMELTING, MaterialItems.RAW.get(Metal.SILVER).get(),
                        MaterialItems.INGOT.get(Metal.SILVER).get(), 1.0f),
                "raw silver smelts, 1.0 xp");
        helper.assertTrue(
                cooks(helper, RecipeType.BLASTING, MaterialItems.RAW.get(Metal.MITHRIL).get(),
                        MaterialItems.INGOT.get(Metal.MITHRIL).get(), 2.0f),
                "raw mithril blasts, 2.0 xp");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rawMorgulIronHasNoCookingRecipe(GameTestHelper helper) {
        Item raw = MaterialItems.RAW.get(Metal.MORGUL_IRON).get();
        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(raw));
        var manager = helper.getLevel().getRecipeManager();
        helper.assertTrue(manager.getRecipeFor(RecipeType.SMELTING, input, helper.getLevel()).isEmpty(), "no smelting");
        helper.assertTrue(manager.getRecipeFor(RecipeType.BLASTING, input, helper.getLevel()).isEmpty(), "no blasting");
        helper.assertTrue(manager.getRecipeFor(RecipeType.SMOKING, input, helper.getLevel()).isEmpty(), "no smoking");
        helper.assertTrue(manager.getRecipeFor(RecipeType.CAMPFIRE_COOKING, input, helper.getLevel()).isEmpty(), "no campfire");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compressionRecipes(GameTestHelper helper) {
        Item nugget = MaterialItems.NUGGET.get(Metal.MITHRIL).get();
        Item ingot = MaterialItems.INGOT.get(Metal.MITHRIL).get();
        Item block = MaterialBlocks.BLOCK_ITEM.get(Metal.MITHRIL).get();
        Item[] nine = new Item[9];
        java.util.Arrays.fill(nine, nugget);
        CraftingInput ninePerRow = CraftingInput.of(3, 3, java.util.Arrays.stream(nine).map(ItemStack::new).toList());
        ItemStack up = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, ninePerRow, helper.getLevel())
                .map(h -> h.value().assemble(ninePerRow, helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(up.is(ingot), "9 nuggets make an ingot, got " + up);
        ItemStack down = craft(helper, block);
        helper.assertTrue(down.is(ingot) && down.getCount() == 9, "block makes 9 ingots, got " + down);
        helper.succeed();
    }

    private static boolean harvests(ItemStack tool, BlockState state) {
        return tool.isCorrectToolForDrops(state);
    }

    @GameTest(template = "empty")
    public static void toolTiers(GameTestHelper helper) {
        BlockState tin = MaterialBlocks.ORE.get(Metal.TIN).get().defaultBlockState();
        BlockState silver = MaterialBlocks.ORE.get(Metal.SILVER).get().defaultBlockState();
        BlockState mithril = MaterialBlocks.ORE.get(Metal.MITHRIL).get().defaultBlockState();
        BlockState morgul = MaterialBlocks.DEEPSLATE_ORE.get(Metal.MORGUL_IRON).get().defaultBlockState();
        ItemStack wood = new ItemStack(Items.WOODEN_PICKAXE);
        ItemStack stone = new ItemStack(Items.STONE_PICKAXE);
        ItemStack iron = new ItemStack(Items.IRON_PICKAXE);
        ItemStack diamond = new ItemStack(Items.DIAMOND_PICKAXE);
        helper.assertTrue(!harvests(wood, tin) && harvests(stone, tin), "tin ore needs a stone pickaxe");
        helper.assertTrue(!harvests(stone, silver) && harvests(iron, silver), "silver ore needs an iron pickaxe");
        helper.assertTrue(!harvests(iron, mithril) && harvests(diamond, mithril), "mithril ore needs a diamond pickaxe");
        helper.assertTrue(!harvests(wood, morgul) && harvests(stone, morgul), "Morgul iron ore needs a stone pickaxe");
        helper.assertTrue(mithril.getDestroySpeed(helper.getLevel(), net.minecraft.core.BlockPos.ZERO) == 5.0f, "mithril hardness 5");
        helper.succeed();
    }
}
