package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearCatalog;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearEntry;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearRecipe;
import io.github.sethwillemsvg.lotrrevival.core.combat.RecipeKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Bronze and iron gear (ticket #110): item numbers, mining, stripping and vanilla-table recipes. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GeneralGearTest {
    private static final double EPS = 0.01;

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LOTRRevival.id(id));
    }

    private static double total(ItemStack stack, Holder<Attribute> attribute, double base) {
        ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        Optional<Double> sum = mods.modifiers().stream()
                .filter(e -> e.attribute().equals(attribute))
                .map(e -> e.modifier().amount())
                .reduce(Double::sum);
        return base + sum.orElse(0.0);
    }

    private static void stats(
            GameTestHelper helper, String id, double damage, double speed, double reach, double knockback) {
        ItemStack stack = new ItemStack(item(id));
        helper.assertTrue(!stack.isEmpty(), id + " is registered");
        double gotDamage = total(stack, Attributes.ATTACK_DAMAGE, 1.0);
        double gotSpeed = total(stack, Attributes.ATTACK_SPEED, 4.0);
        double gotReach = total(stack, Attributes.ENTITY_INTERACTION_RANGE, 0.0);
        double gotKnockback = total(stack, Attributes.ATTACK_KNOCKBACK, 0.0);
        helper.assertTrue(Math.abs(gotDamage - damage) < EPS, id + " damage " + gotDamage + " != " + damage);
        helper.assertTrue(Math.abs(gotSpeed - speed) < EPS, id + " speed " + gotSpeed + " != " + speed);
        helper.assertTrue(Math.abs(gotReach - reach) < EPS, id + " reach " + gotReach + " != " + reach);
        helper.assertTrue(Math.abs(gotKnockback - knockback) < EPS, id + " knockback " + gotKnockback + " != " + knockback);
    }

    @GameTest(template = "empty")
    public static void weaponNumbers(GameTestHelper helper) {
        stats(helper, "bronze_dagger", 2.5, 2.4, -0.75, 0);
        stats(helper, "bronze_sword", 5.5, 1.6, 0, 0);
        stats(helper, "iron_pike", 6.0, 0.8, 3.0, 0);
        stats(helper, "iron_hammer", 8.0, 1.07, 0, 1);
        stats(helper, "iron_battleaxe", 8.0, 1.2, 0, 0);
        stats(helper, "bronze_throwing_axe", 3.5, 1.6, 0, 0);
        helper.assertTrue(new ItemStack(item("bronze_throwing_axe")).getMaxStackSize() == 16, "throwing axes stack to 16");
        helper.assertTrue(new ItemStack(item("bronze_sword")).getMaxDamage() == 230, "bronze uses 230");
        helper.assertTrue(new ItemStack(item("iron_dagger")).getMaxDamage() == 250, "iron uses 250");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bronzeArmourPoints(GameTestHelper helper) {
        int[] want = {2, 5, 4, 2};
        String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
        for (int i = 0; i < 4; i++) {
            Item piece = item("bronze_" + pieces[i]);
            helper.assertTrue(piece instanceof ArmorItem, "bronze_" + pieces[i] + " is armour");
            int got = ((ArmorItem) piece).getDefense();
            helper.assertTrue(got == want[i], "bronze_" + pieces[i] + " defense " + got + " != " + want[i]);
            helper.assertTrue(
                    new ItemStack(piece).getMaxDamage() == ((ArmorItem) piece).getType().getDurability(14),
                    "bronze_" + pieces[i] + " durability");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void battleaxeMinesLikeAnAxeAndStrips(GameTestHelper helper) {
        ItemStack axe = new ItemStack(item("iron_battleaxe"));
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        helper.assertTrue(
                axe.getDestroySpeed(log) == new ItemStack(item("bronze_axe")).getDestroySpeed(log),
                "battleaxe mines logs at tier speed " + axe.getDestroySpeed(log));
        helper.assertTrue(axe.getDestroySpeed(log) > 1.0f, "battleaxe is faster than bare hands on logs");
        helper.assertTrue(axe.canPerformAction(ItemAbilities.AXE_STRIP), "battleaxe can strip");

        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, Blocks.OAK_LOG);
        Player player = helper.makeMockServerPlayerInLevel();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, axe);
            BlockPos abs = helper.absolutePos(pos);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            InteractionResult result = axe.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            helper.assertTrue(result.consumesAction(), "stripping consumed the action");
            helper.assertBlock(pos, b -> b == Blocks.STRIPPED_OAK_LOG, () -> "log is stripped");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bronzePickaxeMinesIronOre(GameTestHelper helper) {
        ItemStack pick = new ItemStack(item("bronze_pickaxe"));
        BlockState ore = Blocks.IRON_ORE.defaultBlockState();
        helper.assertTrue(pick.isCorrectToolForDrops(ore), "bronze pickaxe harvests iron ore");
        helper.assertTrue(pick.getDestroySpeed(ore) > 1.0f, "and mines it fast");
        helper.assertTrue(!pick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "but not obsidian");
        helper.assertTrue(new ItemStack(item("bronze_shovel")).canPerformAction(ItemAbilities.SHOVEL_FLATTEN), "shovel");
        helper.assertTrue(new ItemStack(item("bronze_hoe")).canPerformAction(ItemAbilities.HOE_TILL), "hoe");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyRecipeResolvesOnTheVanillaTable(GameTestHelper helper) {
        int checked = 0;
        for (GearEntry entry : GearCatalog.entries()) {
            if (!entry.id().startsWith("bronze_") && !entry.id().startsWith("iron_")) {
                continue;
            }
            // Iron tools and vanilla-only extras are not ours; only entries from this ticket carry recipes here.
            for (GearRecipe recipe : entry.recipes()) {
                if (!recipe.table().isVanilla() || !recipe.isShaped()) {
                    continue;
                }
                int height = recipe.pattern().size();
                int width = recipe.pattern().stream().mapToInt(String::length).max().orElse(0);
                List<ItemStack> stacks = new ArrayList<>();
                for (String row : recipe.pattern()) {
                    for (int x = 0; x < width; x++) {
                        char c = x < row.length() ? row.charAt(x) : ' ';
                        if (c == ' ') {
                            stacks.add(ItemStack.EMPTY);
                        } else {
                            RecipeKey key = recipe.keys().get(c);
                            ItemStack[] options = key.toIngredient().getItems();
                            helper.assertTrue(options.length > 0, entry.id() + " key " + c + " has no items");
                            stacks.add(options[0].copy());
                        }
                    }
                }
                CraftingInput input = CraftingInput.of(width, height, stacks);
                Optional<RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> found =
                        helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
                helper.assertTrue(found.isPresent(), entry.id() + " recipe does not resolve");
                ItemStack out = found.get().value().assemble(input, helper.getLevel().registryAccess());
                helper.assertTrue(out.is(entry.item().get()), entry.id() + " crafted " + out);
                checked++;
            }
        }
        // 5 + 4 bronze weapons/tools + 6 iron weapons + 4 armour pieces = 19
        helper.assertTrue(checked >= 19, "checked " + checked + " recipes");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaIronSwordUntouched(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.ITEM.get(LOTRRevival.id("iron_sword")) == Items.AIR, "no iron_sword of ours");
        helper.succeed();
    }
}
