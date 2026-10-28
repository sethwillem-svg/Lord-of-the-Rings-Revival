package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BarrelBrew;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BarrelState;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingBarrelBlock;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingBarrelBlockEntity;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingBarrelMenu;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingBlocks;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingComponents;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingItems;
import io.github.sethwillemsvg.lotrrevival.core.brewing.DrinkTier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Brewing GameTests: drinking and the barrel (state machine, recipe matching, tapping, keeping the brew). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BrewingBarrelTests {
    private BrewingBarrelTests() {}

    private static ItemStack drink(net.minecraft.world.item.Item item, DrinkTier tier) {
        ItemStack stack = new ItemStack(item);
        BrewingComponents.setTier(stack, tier);
        return stack;
    }

    /**
     * Mock players join the server's player list and all share one name, so a leftover one makes
     * other tests' by-name commands target the wrong player. Always remove them.
     */
    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    @GameTest(template = "empty")
    public static void drink_scales_food_and_effects(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            drinkScalesFoodAndEffects(helper, player);
        } finally {
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    private static void drinkScalesFoodAndEffects(GameTestHelper helper, ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(0);
        player.getFoodData().setSaturation(0);

        ItemStack stack = drink(BrewingItems.MIRUVOR.get(), DrinkTier.STOUT);
        ItemStack result = stack.getItem().finishUsingItem(stack, helper.getLevel(), player);

        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(strength != null && strength.getAmplifier() == 0, "missing Strength I");
        helper.assertTrue(speed != null && speed.getAmplifier() == 0, "missing Speed I");
        helper.assertTrue(Math.abs(strength.getDuration() - 1600) <= 1, "strength duration " + strength.getDuration());
        helper.assertTrue(Math.abs(speed.getDuration() - 1600) <= 1, "speed duration " + speed.getDuration());
        helper.assertTrue(player.getFoodData().getFoodLevel() == 10, "food level " + player.getFoodData().getFoodLevel());
        helper.assertTrue(result.is(BrewingItems.WOODEN_MUG.get()), "did not get an empty mug back");
        helper.assertTrue(!player.hasEffect(MobEffects.CONFUSION), "Miruvor must not cause nausea");
    }

    @GameTest(template = "empty")
    public static void strong_alcohol_always_nauseates(GameTestHelper helper) {
        for (int i = 0; i < 20; i++) {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            try {
                ItemStack stack = drink(BrewingItems.MEAD.get(), DrinkTier.MIGHTY);
                stack.getItem().finishUsingItem(stack, helper.getLevel(), player);
                MobEffectInstance nausea = player.getEffect(MobEffects.CONFUSION);
                helper.assertTrue(nausea != null, "Mighty Mead did not cause nausea");
                int duration = nausea.getDuration();
                helper.assertTrue(duration >= 2160 && duration <= 3240, "nausea duration " + duration);
            } finally {
                removePlayer(helper, player);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tier_math_matches_spec(GameTestHelper helper) {
        helper.assertTrue(DrinkTier.THIN.scaleNutrition(8) == 4, "thin miruvor nutrition");
        helper.assertTrue(DrinkTier.THIN.scaleDuration(800) == 200, "thin miruvor duration");
        helper.assertTrue(DrinkTier.MIGHTY.scaleNutrition(8) == 12, "mighty miruvor nutrition");
        helper.assertTrue(DrinkTier.STOUT.scaleDuration(4800) == 9600, "stout tonic duration");
        helper.assertTrue(DrinkTier.STOUT.scaleNutrition(4) == 5, "stout tonic nutrition");
        helper.assertTrue(DrinkTier.THIN.scaleNutrition(3) == 2, "round half up");
        helper.assertTrue(
                BrewingComponents.tierOf(new ItemStack(BrewingItems.ALE.get())) == DrinkTier.HEARTY,
                "missing component must behave as tier 2");
        helper.succeed();
    }

    // ---- barrel helpers ----

    private static final BlockPos BARREL_POS = new BlockPos(0, 1, 0);

    private static BrewingBarrelBlockEntity placeBarrel(GameTestHelper helper, Direction facing) {
        helper.setBlock(
                BARREL_POS,
                BrewingBlocks.BARREL.get().defaultBlockState().setValue(BrewingBarrelBlock.FACING, facing));
        return (BrewingBarrelBlockEntity) helper.getBlockEntity(BARREL_POS);
    }

    private static void loadAle(BrewingBarrelBlockEntity barrel) {
        barrel.inventory().setStackInSlot(0, new ItemStack(Items.WHEAT, 2));
        for (int i = 1; i < BrewingBarrelBlockEntity.INGREDIENT_SLOTS; i++) {
            barrel.inventory().setStackInSlot(i, new ItemStack(Items.WHEAT));
        }
        loadWater(barrel, 3);
    }

    private static void loadWater(BrewingBarrelBlockEntity barrel, int buckets) {
        for (int i = 0; i < 3; i++) {
            barrel.inventory().setStackInSlot(
                    BrewingBarrelBlockEntity.INGREDIENT_SLOTS + i,
                    i < buckets ? new ItemStack(Items.WATER_BUCKET) : ItemStack.EMPTY);
        }
    }

    private static void tick(GameTestHelper helper, BrewingBarrelBlockEntity barrel, int ticks) {
        BlockPos abs = helper.absolutePos(BARREL_POS);
        BlockState state = helper.getLevel().getBlockState(abs);
        for (int i = 0; i < ticks; i++) {
            BrewingBarrelBlockEntity.serverTick(helper.getLevel(), abs, state, barrel);
        }
    }

    private static List<ItemStack> snapshot(BrewingBarrelBlockEntity barrel) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < BrewingBarrelBlockEntity.SLOTS; i++) {
            list.add(barrel.inventory().getStackInSlot(i).copy());
        }
        return list;
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static net.minecraft.world.item.Item aleItem() {
        return BrewingItems.ALE.get();
    }

    private static BarrelBrew aleBrew(BarrelState state, int tier, int servings, int elapsed) {
        return new BarrelBrew(state, BuiltInRegistries.ITEM.getKey(aleItem()), tier, servings, elapsed);
    }

    private static BlockHitResult hit(GameTestHelper helper, Direction face) {
        BlockPos abs = helper.absolutePos(BARREL_POS);
        return new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
    }

    // ---- barrel tests ----

    @GameTest(template = "empty")
    public static void ale_starts_on_brew(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            BrewingBarrelBlockEntity barrel = placeBarrel(helper, Direction.NORTH);
            loadAle(barrel);
            BrewingBarrelMenu menu = new BrewingBarrelMenu(1, player.getInventory(), barrel);
            menu.updatePreview();
            helper.assertTrue(
                    menu.getSlot(BrewingBarrelMenu.SLOT_RESULT).getItem().is(aleItem()),
                    "result slot should preview Ale");

            menu.clickMenuButton(player, 0);

            helper.assertTrue(barrel.state() == BarrelState.BREWING, "state " + barrel.state());
            helper.assertTrue(barrel.displayTier() == DrinkTier.THIN, "tier " + barrel.displayTier());
            helper.assertTrue(barrel.servings() == 16, "servings " + barrel.servings());
            helper.assertTrue(
                    barrel.inventory().getStackInSlot(0).is(Items.WHEAT)
                            && barrel.inventory().getStackInSlot(0).getCount() == 1,
                    "slot 0 should hold 1 wheat");
            for (int i = 1; i < 6; i++) {
                helper.assertTrue(barrel.inventory().getStackInSlot(i).isEmpty(), "ingredient slot " + i + " not empty");
            }
            for (int i = 6; i < 9; i++) {
                helper.assertTrue(
                        barrel.inventory().getStackInSlot(i).is(Items.BUCKET), "water slot " + i + " not a bucket");
            }
            // Pressing again before 5 minutes does nothing.
            menu.clickMenuButton(player, 0);
            helper.assertTrue(barrel.state() == BarrelState.BREWING, "early press must not stop the brew");
        } finally {
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void incomplete_or_wrong_load_does_not_brew(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            BrewingBarrelBlockEntity barrel = placeBarrel(helper, Direction.NORTH);
            BrewingBarrelMenu menu = new BrewingBarrelMenu(1, player.getInventory(), barrel);

            // (a) 6 wheat and only 2 water buckets
            for (int i = 0; i < 6; i++) {
                barrel.inventory().setStackInSlot(i, new ItemStack(Items.WHEAT));
            }
            loadWater(barrel, 2);
            assertNoBrew(helper, barrel, menu, player, "2 water buckets");

            // (b) 5 wheat and a dirt
            barrel.inventory().setStackInSlot(5, new ItemStack(Items.DIRT));
            loadWater(barrel, 3);
            assertNoBrew(helper, barrel, menu, player, "dirt in a slot");

            // (c) 5 wheat, one slot empty
            barrel.inventory().setStackInSlot(5, ItemStack.EMPTY);
            assertNoBrew(helper, barrel, menu, player, "an empty ingredient slot");
        } finally {
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    private static void assertNoBrew(
            GameTestHelper helper,
            BrewingBarrelBlockEntity barrel,
            BrewingBarrelMenu menu,
            ServerPlayer player,
            String what) {
        List<ItemStack> before = snapshot(barrel);
        menu.clickMenuButton(player, 0);
        helper.assertTrue(barrel.state() == BarrelState.EMPTY, "brewed with " + what);
        List<ItemStack> after = snapshot(barrel);
        for (int i = 0; i < before.size(); i++) {
            helper.assertTrue(ItemStack.matches(before.get(i), after.get(i)), "consumed slot " + i + " with " + what);
        }
    }

    @GameTest(template = "empty")
    public static void brew_runs_to_mighty(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            BrewingBarrelBlockEntity barrel = placeBarrel(helper, Direction.NORTH);
            loadAle(barrel);
            new BrewingBarrelMenu(1, player.getInventory(), barrel).clickMenuButton(player, 0);

            tick(helper, barrel, BrewingBarrelBlockEntity.TOTAL_TICKS - 1);
            helper.assertTrue(barrel.state() == BarrelState.BREWING, "should still be brewing, was " + barrel.state());
            helper.assertTrue(barrel.displayTier() == DrinkTier.MIGHTY, "tier in progress " + barrel.displayTier());

            tick(helper, barrel, 1);
            helper.assertTrue(barrel.state() == BarrelState.FULL, "should be full, was " + barrel.state());
            helper.assertTrue(barrel.displayTier() == DrinkTier.MIGHTY, "final tier " + barrel.displayTier());
            helper.assertTrue(barrel.servings() == 16, "servings " + barrel.servings());
        } finally {
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stop_early_locks_last_completed_tier(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            BrewingBarrelBlockEntity barrel = placeBarrel(helper, Direction.NORTH);
            loadAle(barrel);
            BrewingBarrelMenu menu = new BrewingBarrelMenu(1, player.getInventory(), barrel);
            menu.clickMenuButton(player, 0);

            tick(helper, barrel, 5999);
            menu.clickMenuButton(player, 0);
            helper.assertTrue(barrel.state() == BarrelState.BREWING, "stop before 5 minutes must do nothing");

            tick(helper, barrel, 15000 - 5999);
            helper.assertTrue(barrel.displayTier() == DrinkTier.HEARTY, "tier in progress " + barrel.displayTier());
            menu.clickMenuButton(player, 0);
            helper.assertTrue(barrel.state() == BarrelState.FULL, "state " + barrel.state());
            helper.assertTrue(barrel.displayTier() == DrinkTier.FAIR, "locked tier " + barrel.displayTier());
            helper.assertTrue(barrel.servings() == 16, "servings " + barrel.servings());
            helper.assertTrue(barrel.elapsed() == 0, "elapsed resets");
        } finally {
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tap_draws_servings(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            BrewingBarrelBlockEntity barrel = placeBarrel(helper, Direction.NORTH);
            barrel.applyBrew(aleBrew(BarrelState.FULL, 3, 16, 0));
            BlockPos abs = helper.absolutePos(BARREL_POS);
            BlockState state = helper.getLevel().getBlockState(abs);

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BrewingItems.WOODEN_MUG.get()));
            state.useItemOn(
                    player.getItemInHand(InteractionHand.MAIN_HAND),
                    helper.getLevel(),
                    player,
                    InteractionHand.MAIN_HAND,
                    hit(helper, Direction.NORTH));
            ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
            helper.assertTrue(held.is(aleItem()), "should hold Ale, holds " + held);
            helper.assertTrue(
                    BrewingComponents.tierOf(held) == DrinkTier.STOUT, "tier " + BrewingComponents.tierOf(held));
            helper.assertTrue(barrel.servings() == 15, "servings " + barrel.servings());

            // A side face draws nothing.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BrewingItems.WOODEN_MUG.get()));
            state.useItemOn(
                    player.getItemInHand(InteractionHand.MAIN_HAND),
                    helper.getLevel(),
                    player,
                    InteractionHand.MAIN_HAND,
                    hit(helper, Direction.EAST));
            helper.assertTrue(
                    player.getItemInHand(InteractionHand.MAIN_HAND).is(BrewingItems.WOODEN_MUG.get()),
                    "side face must not draw");
            helper.assertTrue(barrel.servings() == 15, "side face changed servings");

            for (int i = 0; i < 15; i++) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BrewingItems.WOODEN_MUG.get()));
                state.useItemOn(
                        player.getItemInHand(InteractionHand.MAIN_HAND),
                        helper.getLevel(),
                        player,
                        InteractionHand.MAIN_HAND,
                        hit(helper, Direction.NORTH));
            }
            helper.assertTrue(barrel.state() == BarrelState.EMPTY, "state after last serving " + barrel.state());
            helper.assertTrue(barrel.drink() == null, "drink should be cleared");
        } finally {
            removePlayer(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void broken_barrel_keeps_brew(GameTestHelper helper) {
        // Full Ale, tier 2, 10 servings, 1 wheat left over.
        BrewingBarrelBlockEntity barrel = placeBarrel(helper, Direction.NORTH);
        barrel.applyBrew(aleBrew(BarrelState.FULL, 2, 10, 0));
        barrel.inventory().setStackInSlot(0, new ItemStack(Items.WHEAT));
        ItemStack carried = breakAndCollect(helper, true);
        BarrelBrew stored = carried.get(BrewingComponents.BARREL_BREW.get());
        helper.assertTrue(
                stored != null && stored.equals(aleBrew(BarrelState.FULL, 2, 10, 0)), "stored brew " + stored);
        helper.assertTrue(carried.getMaxStackSize() == 1, "brew barrel must not stack");

        BrewingBarrelBlockEntity placed = placeFromItem(helper, carried);
        helper.assertTrue(placed.state() == BarrelState.FULL && placed.drink() == aleItem(), "restored state/drink");
        helper.assertTrue(placed.displayTier() == DrinkTier.HEARTY, "restored tier " + placed.displayTier());
        helper.assertTrue(placed.servings() == 10, "restored servings " + placed.servings());

        // Brewing at 15,000 resumes and finishes at 30,000.
        placed.applyBrew(aleBrew(BarrelState.BREWING, 0, 16, 15000));
        ItemStack brewing = breakAndCollect(helper, false);
        BarrelBrew storedBrewing = brewing.get(BrewingComponents.BARREL_BREW.get());
        helper.assertTrue(
                storedBrewing != null && storedBrewing.equals(aleBrew(BarrelState.BREWING, 0, 16, 15000)),
                "stored brewing " + storedBrewing);
        BrewingBarrelBlockEntity resumed = placeFromItem(helper, brewing);
        helper.assertTrue(
                resumed.state() == BarrelState.BREWING && resumed.elapsed() == 15000, "resumed at " + resumed.elapsed());
        tick(helper, resumed, 14999);
        helper.assertTrue(resumed.state() == BarrelState.BREWING, "should still brew at 29,999");
        tick(helper, resumed, 1);
        helper.assertTrue(
                resumed.state() == BarrelState.FULL && resumed.displayTier() == DrinkTier.MIGHTY,
                "should be Full Mighty");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void empty_barrel_drops_plain_item(GameTestHelper helper) {
        placeBarrel(helper, Direction.NORTH);
        ItemStack plain = breakAndCollect(helper, false);
        helper.assertTrue(plain.is(BrewingBlocks.BARREL_ITEM.get()), "should drop the barrel");
        helper.assertTrue(plain.get(BrewingComponents.BARREL_BREW.get()) == null, "empty barrel carries no brew");
        helper.assertTrue(plain.getMaxStackSize() == 64, "plain barrels stack to 64");
        helper.succeed();
    }

    /** Breaks the barrel with drops and returns the dropped barrel item; checks the wheat drop. */
    private static ItemStack breakAndCollect(GameTestHelper helper, boolean expectWheat) {
        BlockPos abs = helper.absolutePos(BARREL_POS);
        helper.getLevel().destroyBlock(abs, true);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3));
        ItemStack barrelItem = ItemStack.EMPTY;
        int wheat = 0;
        int barrels = 0;
        for (ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            if (stack.is(BrewingBlocks.BARREL_ITEM.get())) {
                barrels++;
                barrelItem = stack.copy();
            } else if (stack.is(Items.WHEAT)) {
                wheat += stack.getCount();
            }
            entity.discard();
        }
        helper.assertTrue(barrels == 1, "expected exactly one barrel item, got " + barrels);
        helper.assertTrue(wheat == (expectWheat ? 1 : 0), "wheat dropped: " + wheat);
        return barrelItem;
    }

    private static BrewingBarrelBlockEntity placeFromItem(GameTestHelper helper, ItemStack item) {
        BrewingBarrelBlockEntity barrel = placeBarrel(helper, Direction.NORTH);
        barrel.applyComponentsFromItemStack(item);
        return barrel;
    }
}
