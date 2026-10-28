package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.building.ArnorBlocks;
import io.github.sethwillemsvg.lotrrevival.core.building.GoldRingItems;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

/** Gold ring item properties and recipe, and the Arnor brick set's stats and road tag membership. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArnorBlocksTest {
    @GameTest(template = "empty")
    public static void goldRingProperties(GameTestHelper helper) {
        ItemStack ring = new ItemStack(GoldRingItems.GOLD_RING.get());
        helper.assertTrue(ring.getMaxStackSize() == 16, "ring stack size should be 16");
        helper.assertTrue(ring.getItem().components().has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT)
                        || ring.has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT),
                "ring should be fire resistant");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void goldRingRecipe(GameTestHelper helper) {
        ItemStack nugget = new ItemStack(Items.GOLD_NUGGET);
        ItemStack e = ItemStack.EMPTY;
        CraftingInput good = CraftingInput.of(3, 3, List.of(e, nugget, e, nugget, e, nugget, e, nugget, e));
        var recipes = helper.getLevel().getRecipeManager();
        var match = recipes.getRecipeFor(RecipeType.CRAFTING, good, helper.getLevel());
        helper.assertTrue(match.isPresent(), "ring recipe should match");
        helper.assertTrue(
                match.get().value().assemble(good, helper.getLevel().registryAccess()).is(GoldRingItems.GOLD_RING.get()),
                "recipe should yield a gold ring");
        CraftingInput filled = CraftingInput.of(3, 3, List.of(e, nugget, e, nugget, nugget, nugget, e, nugget, e));
        helper.assertTrue(
                recipes.getRecipeFor(RecipeType.CRAFTING, filled, helper.getLevel()).isEmpty(),
                "filled centre should not craft a ring");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arnorSetStatsAndTags(GameTestHelper helper) {
        for (ArnorBlocks.Variant v : ArnorBlocks.VARIANTS) {
            for (Block b : new Block[] {v.block().get(), v.slab().get(), v.stairs().get()}) {
                helper.assertTrue(
                        b.defaultBlockState().getDestroySpeed(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO))
                                == Blocks.STONE_BRICKS.defaultBlockState().getDestroySpeed(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO)),
                        v.name() + " hardness should match stone bricks");
                helper.assertTrue(
                        b.getExplosionResistance() == Blocks.STONE_BRICKS.getExplosionResistance(),
                        v.name() + " resistance should match stone bricks");
            }
            helper.assertTrue(v.block().get().defaultBlockState().is(ModTags.Blocks.ROAD_SURFACES), v.name() + " not a road surface");
            helper.assertTrue(v.slab().get().defaultBlockState().is(ModTags.Blocks.ROAD_SURFACES), v.name() + " slab not a road surface");
        }
        helper.succeed();
    }
}
