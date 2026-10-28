package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.CrossbowGear;
import io.github.sethwillemsvg.lotrrevival.core.combat.item.GearCrossbow;
import io.github.sethwillemsvg.lotrrevival.core.combat.ranged.BoltEntity;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Bronze and iron crossbows and bolts (ticket #121): damage, ammunition, charge time and recipes. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrossbowTest {
    private static final double EPS = 1e-6;

    private static ItemStack loaded(Item crossbow) {
        ItemStack stack = new ItemStack(crossbow);
        stack.set(
                DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.of(List.of(new ItemStack(CrossbowGear.BOLT.get()))));
        return stack;
    }

    private static double shotBaseDamage(GameTestHelper helper, Item crossbow) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ItemStack stack = loaded(crossbow);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            ((GearCrossbow) crossbow)
                    .performShooting(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, GearCrossbow.BOLT_VELOCITY, 1.0F, null);
            helper.assertFalse(CrossbowItem.isCharged(stack), "firing empties the crossbow");
            List<BoltEntity> bolts = helper.getLevel().getEntitiesOfClass(BoltEntity.class, player.getBoundingBox().inflate(6));
            helper.assertTrue(bolts.size() == 1, "exactly one bolt in flight: " + bolts.size());
            BoltEntity bolt = bolts.get(0);
            double damage = bolt.getBaseDamage();
            helper.assertTrue(bolt.getOwner() == player, "the shooter owns the bolt");
            bolts.forEach(BoltEntity::discard);
            return damage;
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void boltBaseDamageFollowsTheCrossbow(GameTestHelper helper) {
        double iron = shotBaseDamage(helper, CrossbowGear.IRON_CROSSBOW.get());
        helper.assertTrue(Math.abs(iron - 4.0) < EPS, "iron crossbow bolt base damage 4.0, got " + iron);
        double bronze = shotBaseDamage(helper, CrossbowGear.BRONZE_CROSSBOW.get());
        helper.assertTrue(Math.abs(bronze - 3.9) < EPS, "bronze crossbow bolt base damage 3.9, got " + bronze);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ironFactorMatchesTheSpecFormula(GameTestHelper helper) {
        // (1 + 0.1 x (material damage - 2)) x 2 with iron material damage 2.
        double formula = (1 + 0.1 * (io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial.IRON.materialDamage() - 2)) * 2;
        helper.assertTrue(Math.abs(formula - CrossbowGear.IRON_FACTOR) < EPS, "iron factor " + formula);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyBoltsCanBeLoaded(GameTestHelper helper) {
        for (GearCrossbow crossbow : crossbows()) {
            ItemStack stack = new ItemStack(crossbow);
            helper.assertTrue(
                    crossbow.getAllSupportedProjectiles(stack).test(new ItemStack(CrossbowGear.BOLT.get())), "bolt accepted");
            helper.assertFalse(crossbow.getAllSupportedProjectiles(stack).test(new ItemStack(Items.ARROW)), "arrow refused");
            helper.assertFalse(
                    crossbow.getAllSupportedProjectiles(stack).test(new ItemStack(Items.SPECTRAL_ARROW)),
                    "spectral arrow refused");
            helper.assertFalse(crossbow.getSupportedHeldProjectiles(stack).test(new ItemStack(Items.ARROW)), "held arrow refused");
            helper.assertFalse(
                    crossbow.getSupportedHeldProjectiles(stack).test(new ItemStack(Items.FIREWORK_ROCKET)),
                    "fireworks refused");
        }
        // The vanilla crossbow does not take bolts either.
        helper.assertFalse(
                ((CrossbowItem) Items.CROSSBOW).getAllSupportedProjectiles(new ItemStack(Items.CROSSBOW))
                        .test(new ItemStack(CrossbowGear.BOLT.get())),
                "vanilla crossbow refuses bolts");
        helper.assertTrue(new ItemStack(CrossbowGear.BOLT.get()).is(ModTags.Items.BOLTS), "bolt is in the bolts tag");
        helper.assertFalse(new ItemStack(CrossbowGear.BOLT.get()).is(net.minecraft.tags.ItemTags.ARROWS), "bolt is not an arrow");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void chargeCompletesAtFiftyTicksOrFortyWithQuickChargeTwo(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            Holder<Enchantment> quickCharge =
                    helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.QUICK_CHARGE);
            for (GearCrossbow crossbow : crossbows()) {
                ItemStack plain = new ItemStack(crossbow);
                helper.assertTrue(crossbow.chargeDuration(plain, player) == 50, "plain charge " + crossbow.chargeDuration(plain, player));
                helper.assertTrue(releasedAfter(helper, player, crossbow, 0, 49) == false, "49 ticks: not loaded");
                helper.assertTrue(releasedAfter(helper, player, crossbow, 0, 50), "50 ticks: loaded");

                ItemStack one = new ItemStack(crossbow);
                one.enchant(quickCharge, 1);
                helper.assertTrue(crossbow.chargeDuration(one, player) == 45, "Quick Charge I charge " + crossbow.chargeDuration(one, player));

                ItemStack two = new ItemStack(crossbow);
                two.enchant(quickCharge, 2);
                helper.assertTrue(crossbow.chargeDuration(two, player) == 40, "Quick Charge II charge " + crossbow.chargeDuration(two, player));
                helper.assertTrue(crossbow.getUseDuration(two, player) == 43, "use duration is charge + 3");
                helper.assertTrue(releasedAfter(helper, player, crossbow, 2, 39) == false, "QC II 39 ticks: not loaded");
                helper.assertTrue(releasedAfter(helper, player, crossbow, 2, 40), "QC II 40 ticks: loaded");
            }
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** Charges a fresh crossbow (with Quick Charge {@code quickCharge}) for {@code ticks} and releases it. */
    private static boolean releasedAfter(
            GameTestHelper helper, ServerPlayer player, GearCrossbow crossbow, int quickCharge, int ticks) {
        ItemStack stack = new ItemStack(crossbow);
        if (quickCharge > 0) {
            stack.enchant(
                    helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.QUICK_CHARGE),
                    quickCharge);
        }
        int timeLeft = crossbow.getUseDuration(stack, player) - ticks;
        crossbow.releaseUsing(stack, helper.getLevel(), player, timeLeft);
        return CrossbowItem.isCharged(stack);
    }

    @GameTest(template = "empty")
    public static void statsMatchTheSpecTable(GameTestHelper helper) {
        ItemStack bronze = new ItemStack(CrossbowGear.BRONZE_CROSSBOW.get());
        ItemStack iron = new ItemStack(CrossbowGear.IRON_CROSSBOW.get());
        helper.assertTrue(bronze.getMaxDamage() == 230, "bronze uses " + bronze.getMaxDamage());
        helper.assertTrue(iron.getMaxDamage() == 250, "iron uses " + iron.getMaxDamage());
        helper.assertTrue(bronze.is(ModTags.Items.WEAPONS_CROSSBOWS), "bronze in weapons/crossbows");
        helper.assertTrue(iron.is(ModTags.Items.WEAPONS_CROSSBOWS), "iron in weapons/crossbows");
        helper.assertTrue(bronze.is(net.minecraft.tags.ItemTags.CROSSBOW_ENCHANTABLE), "bronze takes crossbow enchantments");
        helper.assertTrue(iron.is(net.minecraft.tags.ItemTags.CROSSBOW_ENCHANTABLE), "iron takes crossbow enchantments");
        helper.assertTrue(new ItemStack(CrossbowGear.BOLT.get()).getMaxStackSize() == 64, "bolts stack to 64");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void boltAndCrossbowRecipes(GameTestHelper helper) {
        for (Item ingot : List.of(Items.IRON_INGOT)) {
            ItemStack out = craft(helper, 3, 1, List.of(new ItemStack(ingot), new ItemStack(Items.STICK), new ItemStack(Items.FEATHER)));
            helper.assertTrue(out.is(CrossbowGear.BOLT.get()) && out.getCount() == 4, "iron ingot + stick + feather gives 4 bolts: " + out);
        }
        ItemStack out = craft(helper, 3, 3, crossbowGrid(Items.IRON_INGOT));
        helper.assertTrue(out.is(CrossbowGear.IRON_CROSSBOW.get()), "iron crossbow grid: " + out);
        // The vanilla crossbow shape still makes the vanilla crossbow.
        ItemStack vanilla = craft(
                helper,
                3,
                3,
                List.of(
                        new ItemStack(Items.STICK), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.STICK),
                        new ItemStack(Items.STRING), new ItemStack(Items.TRIPWIRE_HOOK), new ItemStack(Items.STRING),
                        ItemStack.EMPTY, new ItemStack(Items.STICK), ItemStack.EMPTY));
        helper.assertTrue(vanilla.is(Items.CROSSBOW), "vanilla crossbow grid: " + vanilla);
        helper.succeed();
    }

    private static List<ItemStack> crossbowGrid(Item ingot) {
        return List.of(
                new ItemStack(ingot), new ItemStack(Items.STICK), new ItemStack(ingot),
                new ItemStack(Items.STRING), new ItemStack(Items.TRIPWIRE_HOOK), new ItemStack(Items.STRING),
                ItemStack.EMPTY, new ItemStack(Items.STICK), ItemStack.EMPTY);
    }

    private static ItemStack craft(GameTestHelper helper, int width, int height, List<ItemStack> stacks) {
        CraftingInput input = CraftingInput.of(width, height, stacks);
        Optional<RecipeHolder<CraftingRecipe>> found =
                helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        return found.map(r -> r.value().assemble(input, helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
    }

    private static List<GearCrossbow> crossbows() {
        return List.of(
                (GearCrossbow) CrossbowGear.BRONZE_CROSSBOW.get(), (GearCrossbow) CrossbowGear.IRON_CROSSBOW.get());
    }
}
