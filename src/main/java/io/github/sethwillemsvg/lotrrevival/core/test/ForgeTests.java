package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.forge.ForgeBlock;
import io.github.sethwillemsvg.lotrrevival.core.forge.ForgeBlockEntity;
import io.github.sethwillemsvg.lotrrevival.core.forge.ForgeBlocks;
import io.github.sethwillemsvg.lotrrevival.core.material.MaterialItems;
import io.github.sethwillemsvg.lotrrevival.core.material.Metal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** Alloy forge and orc forge: forging recipes, lanes sharing fuel, slot rules, hoppers and drops. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForgeTests {
    private ForgeTests() {}

    private static final BlockPos FIRST = new BlockPos(0, 1, 0);
    private static final BlockPos SECOND = new BlockPos(2, 1, 2);

    private static ForgeBlockEntity place(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block.defaultBlockState());
        return (ForgeBlockEntity) helper.getBlockEntity(pos);
    }

    private static Item ingot(Metal metal) {
        return MaterialItems.INGOT.get(metal).get();
    }

    private static Item raw(Metal metal) {
        return MaterialItems.RAW.get(metal).get();
    }

    private static void fuel(ForgeBlockEntity forge, int coal) {
        forge.inventory().setStackInSlot(ForgeBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL, coal));
    }

    private static ItemStack output(ForgeBlockEntity forge, int lane) {
        return forge.inventory().getStackInSlot(ForgeBlockEntity.OUTPUT_START + lane);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void copperOverTinGivesTwoBronze(GameTestHelper helper) {
        ForgeBlockEntity forge = place(helper, FIRST, ForgeBlocks.ALLOY_FORGE.get());
        // Lane 0: copper ingot in the input slot, tin ingot in the alloy slot above.
        forge.inventory().setStackInSlot(ForgeBlockEntity.INPUT_START, new ItemStack(Items.COPPER_INGOT));
        forge.inventory().setStackInSlot(ForgeBlockEntity.ALLOY_START, new ItemStack(ingot(Metal.TIN)));
        // Lane 1: the other way round, and raw instead of ingots.
        forge.inventory().setStackInSlot(ForgeBlockEntity.INPUT_START + 1, new ItemStack(raw(Metal.TIN)));
        forge.inventory().setStackInSlot(ForgeBlockEntity.ALLOY_START + 1, new ItemStack(Items.RAW_COPPER));
        fuel(forge, 1);

        helper.runAfterDelay(50, () -> {
            helper.assertTrue(forge.getBlockState().getValue(ForgeBlock.LIT), "forge is lit while burning");
            helper.assertTrue(forge.getBlockState().getLightEmission() == 13, "lit forge gives light 13");
            helper.assertTrue(output(forge, 0).isEmpty(), "nothing is done after 50 ticks");
        });
        helper.runAfterDelay(210, () -> {
            for (int lane = 0; lane < 2; lane++) {
                ItemStack out = output(forge, lane);
                helper.assertTrue(
                        out.is(ingot(Metal.BRONZE)) && out.getCount() == 2, "lane " + lane + " gives 2 bronze, got " + out);
                helper.assertTrue(
                        forge.inventory().getStackInSlot(ForgeBlockEntity.INPUT_START + lane).isEmpty()
                                && forge.inventory().getStackInSlot(ForgeBlockEntity.ALLOY_START + lane).isEmpty(),
                        "lane " + lane + " consumed one of each");
            }
            helper.assertTrue(output(forge, 2).isEmpty(), "idle lanes stay empty");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void rawMorgulIronOnlyInOrcForge(GameTestHelper helper) {
        ForgeBlockEntity alloy = place(helper, FIRST, ForgeBlocks.ALLOY_FORGE.get());
        ForgeBlockEntity orc = place(helper, SECOND, ForgeBlocks.ORC_FORGE.get());
        ItemStack morgul = new ItemStack(raw(Metal.MORGUL_IRON));

        helper.assertFalse(
                alloy.inventory().isItemValid(ForgeBlockEntity.INPUT_START, morgul),
                "alloy forge refuses raw Morgul iron");
        helper.assertTrue(
                orc.inventory().isItemValid(ForgeBlockEntity.INPUT_START, morgul), "orc forge accepts raw Morgul iron");
        helper.assertTrue(
                orc.inventory().isItemValid(ForgeBlockEntity.INPUT_START, new ItemStack(Items.COPPER_INGOT)),
                "orc forge also takes copper for bronze (forges/any)");

        // Force it in anyway: the alloy forge must still do nothing with it.
        alloy.inventory().setStackInSlot(ForgeBlockEntity.INPUT_START, morgul.copy());
        orc.inventory().setStackInSlot(ForgeBlockEntity.INPUT_START, morgul.copy());
        fuel(alloy, 1);
        fuel(orc, 1);

        helper.runAfterDelay(210, () -> {
            helper.assertTrue(output(alloy, 0).isEmpty(), "alloy forge makes nothing from raw Morgul iron");
            helper.assertTrue(
                    alloy.inventory().getStackInSlot(ForgeBlockEntity.INPUT_START).getCount() == 1,
                    "raw Morgul iron stays put in the alloy forge");
            helper.assertTrue(
                    alloy.inventory().getStackInSlot(ForgeBlockEntity.FUEL_SLOT).getCount() == 1,
                    "alloy forge does not burn fuel for nothing");
            ItemStack out = output(orc, 0);
            helper.assertTrue(
                    out.is(ingot(Metal.ORC_STEEL)) && out.getCount() == 1, "orc forge gives 1 orc steel, got " + out);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void forgeRejectsFoodAndOutputs(GameTestHelper helper) {
        ForgeBlockEntity forge = place(helper, FIRST, ForgeBlocks.ALLOY_FORGE.get());
        ItemStack bread = new ItemStack(Items.BREAD);
        for (int slot : new int[] {ForgeBlockEntity.INPUT_START, ForgeBlockEntity.ALLOY_START, ForgeBlockEntity.FUEL_SLOT}) {
            ItemStack left = forge.inventory().insertItem(slot, bread.copy(), false);
            helper.assertTrue(left.getCount() == 1, "bread is rejected by slot " + slot);
        }
        // Raw beef smelts in a furnace but is food, so the forge refuses it too.
        helper.assertFalse(
                forge.inventory().isItemValid(ForgeBlockEntity.INPUT_START, new ItemStack(Items.BEEF)),
                "raw food is refused");
        helper.assertTrue(
                forge.inventory().isItemValid(ForgeBlockEntity.INPUT_START, new ItemStack(Items.RAW_IRON)),
                "raw iron is accepted (non-edible smelting input)");
        helper.assertFalse(
                forge.inventory().isItemValid(ForgeBlockEntity.OUTPUT_START, new ItemStack(Items.IRON_INGOT)),
                "output slots take nothing");
        helper.assertTrue(
                forge.inventory().isItemValid(ForgeBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL)), "coal is fuel");
        helper.assertFalse(
                forge.inventory().isItemValid(ForgeBlockEntity.FUEL_SLOT, new ItemStack(Items.IRON_INGOT)),
                "an ingot is not fuel");
        helper.assertTrue(
                forge.inventory().insertItem(ForgeBlockEntity.FUEL_SLOT, new ItemStack(Items.COAL), false).isEmpty(),
                "coal goes into the fuel slot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void fourLanesFinishTogetherOnOneCoal(GameTestHelper helper) {
        ForgeBlockEntity forge = place(helper, FIRST, ForgeBlocks.ALLOY_FORGE.get());
        for (int lane = 0; lane < ForgeBlockEntity.LANES; lane++) {
            forge.inventory().setStackInSlot(ForgeBlockEntity.INPUT_START + lane, new ItemStack(Items.RAW_IRON, 2));
        }
        fuel(forge, 1);

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(
                    forge.inventory().getStackInSlot(ForgeBlockEntity.FUEL_SLOT).isEmpty(),
                    "the one coal is already burning");
            for (int lane = 0; lane < ForgeBlockEntity.LANES; lane++) {
                helper.assertTrue(output(forge, lane).isEmpty(), "lane " + lane + " has nothing yet at 100 ticks");
            }
        });
        helper.runAfterDelay(205, () -> {
            for (int lane = 0; lane < ForgeBlockEntity.LANES; lane++) {
                helper.assertTrue(
                        output(forge, lane).is(Items.IRON_INGOT) && output(forge, lane).getCount() == 1,
                        "lane " + lane + " finished its first item together with the others, got " + output(forge, lane));
            }
        });
        helper.runAfterDelay(405, () -> {
            for (int lane = 0; lane < ForgeBlockEntity.LANES; lane++) {
                helper.assertTrue(
                        output(forge, lane).is(Items.IRON_INGOT) && output(forge, lane).getCount() == 2,
                        "lane " + lane + " finished both items, got " + output(forge, lane));
                helper.assertTrue(
                        forge.inventory().getStackInSlot(ForgeBlockEntity.INPUT_START + lane).isEmpty(),
                        "lane " + lane + " used up its input");
            }
            helper.assertTrue(
                    forge.inventory().getStackInSlot(ForgeBlockEntity.FUEL_SLOT).isEmpty(),
                    "exactly one coal was used for 8 items");
            helper.assertTrue(forge.isBurning(), "the coal (1600 ticks) is still burning after 400 ticks");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void hoppersUseTopSidesAndBottom(GameTestHelper helper) {
        ForgeBlockEntity forge = place(helper, FIRST, ForgeBlocks.ALLOY_FORGE.get());
        BlockPos abs = helper.absolutePos(FIRST);
        IItemHandler top = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, Direction.UP);
        IItemHandler side = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, Direction.NORTH);
        IItemHandler bottom = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, Direction.DOWN);
        helper.assertTrue(top != null && side != null && bottom != null, "forge exposes an item handler on every side");

        helper.assertTrue(
                top.insertItem(0, new ItemStack(Items.RAW_IRON, 3), false).isEmpty()
                        && forge.inventory().getStackInSlot(ForgeBlockEntity.INPUT_START).getCount() == 3,
                "top feeds the input slots");
        helper.assertTrue(
                !top.insertItem(0, new ItemStack(Items.COAL), false).isEmpty(), "top refuses coal (not a smeltable input)");
        helper.assertTrue(
                side.insertItem(0, new ItemStack(Items.COAL), false).isEmpty()
                        && forge.inventory().getStackInSlot(ForgeBlockEntity.FUEL_SLOT).is(Items.COAL),
                "sides feed the fuel slot");
        helper.assertTrue(
                !side.insertItem(0, new ItemStack(Items.RAW_IRON), false).isEmpty(), "sides refuse ore");
        helper.assertTrue(
                bottom.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
                "bottom takes nothing in");

        forge.inventory().setStackInSlot(ForgeBlockEntity.OUTPUT_START + 2, new ItemStack(Items.IRON_INGOT, 5));
        helper.assertTrue(bottom.extractItem(2, 64, false).getCount() == 5, "bottom pulls outputs");
        helper.assertTrue(top.extractItem(0, 64, true).isEmpty(), "top does not pull");
        helper.assertTrue(side.extractItem(0, 64, true).isEmpty(), "sides do not pull");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingDropsContents(GameTestHelper helper) {
        ForgeBlockEntity forge = place(helper, FIRST, ForgeBlocks.ORC_FORGE.get());
        forge.inventory().setStackInSlot(ForgeBlockEntity.INPUT_START, new ItemStack(Items.COPPER_INGOT, 4));
        forge.inventory().setStackInSlot(ForgeBlockEntity.OUTPUT_START + 1, new ItemStack(ingot(Metal.BRONZE), 2));
        fuel(forge, 3);
        helper.setBlock(FIRST, Blocks.AIR);
        helper.assertItemEntityPresent(Items.COPPER_INGOT, FIRST, 2.0);
        helper.assertItemEntityPresent(ingot(Metal.BRONZE), FIRST, 2.0);
        helper.assertItemEntityPresent(Items.COAL, FIRST, 2.0);
        helper.succeed();
    }
}
