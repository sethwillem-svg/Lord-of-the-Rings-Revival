package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.poison.PoisonHits;
import io.github.sethwillemsvg.lotrrevival.core.combat.poison.PoisonItems;
import io.github.sethwillemsvg.lotrrevival.core.combat.poison.PoisonWeaponRecipe;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Bottle of poison, the poison_weapon special recipe and the poisoned-hit listener (ticket #125). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PoisonTest {
    private static Item bronzeDagger() {
        return BuiltInRegistries.ITEM.get(LOTRRevival.id("bronze_dagger"));
    }

    private static ItemStack damagedNamedDagger() {
        ItemStack dagger = new ItemStack(bronzeDagger());
        dagger.set(DataComponents.CUSTOM_NAME, Component.literal("Sting"));
        dagger.setDamageValue(37);
        return dagger;
    }

    private static CraftingInput grid(ItemStack... stacks) {
        return CraftingInput.of(3, 3, List.of(stacks));
    }

    private static CraftingInput grid3x3(ItemStack... first) {
        ItemStack[] all = new ItemStack[9];
        java.util.Arrays.fill(all, ItemStack.EMPTY);
        System.arraycopy(first, 0, all, 0, first.length);
        return grid(all);
    }

    @GameTest(template = "empty")
    public static void poisonableTagHoldsDaggers(GameTestHelper helper) {
        helper.assertTrue(new ItemStack(bronzeDagger()).is(ModTags.Items.POISONABLE), "bronze_dagger is in #poisonable");
        helper.assertTrue(!new ItemStack(Items.IRON_SWORD).is(ModTags.Items.POISONABLE), "a sword is not poisonable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bottleBasics(GameTestHelper helper) {
        ItemStack bottle = new ItemStack(PoisonItems.BOTTLE_OF_POISON.get());
        helper.assertTrue(bottle.getMaxStackSize() == 1, "bottle of poison stacks to 1");
        helper.assertTrue(bottle.getUseAnimation() == net.minecraft.world.item.UseAnim.NONE, "bottle is not drinkable");
        CraftingInput input = grid3x3(
                new ItemStack(Items.GLASS_BOTTLE), new ItemStack(Items.SPIDER_EYE), new ItemStack(Items.POISONOUS_POTATO));
        var found = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(found.isPresent(), "stand-in recipe exists");
        ItemStack result = found.get().value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(PoisonItems.BOTTLE_OF_POISON.get()), "stand-in makes a bottle of poison, got " + result);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poisoningKeepsNameAndDamageAndReturnsBottle(GameTestHelper helper) {
        ItemStack dagger = damagedNamedDagger();
        ItemStack bottle = new ItemStack(PoisonItems.BOTTLE_OF_POISON.get());
        // Spread across the grid: the match is position independent.
        CraftingInput input = grid(
                ItemStack.EMPTY, ItemStack.EMPTY, bottle,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                dagger, ItemStack.EMPTY, ItemStack.EMPTY);
        var found = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(found.isPresent() && found.get().value() instanceof PoisonWeaponRecipe, "poison_weapon matches");
        CraftingRecipe recipe = found.get().value();
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(bronzeDagger()), "still a bronze dagger");
        helper.assertTrue(PoisonItems.isPoisoned(result), "result carries the poisoned component");
        helper.assertTrue(result.getDamageValue() == 37, "damage kept, got " + result.getDamageValue());
        helper.assertTrue(
                Component.literal("Sting").equals(result.get(DataComponents.CUSTOM_NAME)), "custom name kept");
        helper.assertTrue(!PoisonItems.isPoisoned(dagger), "input dagger was not modified");
        var remaining = recipe.getRemainingItems(input);
        helper.assertTrue(remaining.get(2).is(Items.GLASS_BOTTLE), "bottle slot returns a glass bottle");
        helper.assertTrue(remaining.get(6).isEmpty(), "weapon slot returns nothing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poisonedDaggerDoesNotMatchAgain(GameTestHelper helper) {
        ItemStack poisoned = damagedNamedDagger();
        poisoned.set(PoisonItems.POISONED.get(), net.minecraft.util.Unit.INSTANCE);
        ItemStack bottle = new ItemStack(PoisonItems.BOTTLE_OF_POISON.get());
        PoisonWeaponRecipe recipe = new PoisonWeaponRecipe(CraftingBookCategory.EQUIPMENT);
        helper.assertTrue(!recipe.matches(grid3x3(poisoned, bottle), helper.getLevel()), "poisoned dagger does not match");
        var found = helper.getLevel().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, grid3x3(poisoned, bottle), helper.getLevel());
        helper.assertTrue(found.isEmpty(), "no recipe at all for a poisoned dagger + bottle");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeRejectsWrongInputs(GameTestHelper helper) {
        PoisonWeaponRecipe recipe = new PoisonWeaponRecipe(CraftingBookCategory.EQUIPMENT);
        ItemStack bottle = new ItemStack(PoisonItems.BOTTLE_OF_POISON.get());
        ItemStack dagger = new ItemStack(bronzeDagger());
        var level = helper.getLevel();
        helper.assertTrue(recipe.matches(grid3x3(dagger, bottle), level), "dagger + bottle matches");
        helper.assertTrue(!recipe.matches(grid3x3(dagger), level), "dagger alone does not");
        helper.assertTrue(!recipe.matches(grid3x3(bottle), level), "bottle alone does not");
        helper.assertTrue(!recipe.matches(grid3x3(dagger, dagger.copy(), bottle), level), "two daggers do not");
        helper.assertTrue(!recipe.matches(grid3x3(dagger, bottle, bottle.copy()), level), "two bottles do not");
        helper.assertTrue(!recipe.matches(grid3x3(dagger, bottle, new ItemStack(Items.STICK)), level), "extra item does not");
        helper.assertTrue(!recipe.matches(grid3x3(new ItemStack(Items.IRON_SWORD), bottle), level), "non-poisonable does not");
        helper.succeed();
    }

    private static void withDifficulty(GameTestHelper helper, Difficulty difficulty, Runnable body) {
        var server = helper.getLevel().getServer();
        Difficulty before = server.getWorldData().getDifficulty();
        server.setDifficulty(difficulty, true);
        try {
            body.run();
        } finally {
            server.setDifficulty(before, true);
        }
    }

    private static MobEffectInstance hit(GameTestHelper helper, ItemStack weapon) {
        Pillager attacker = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 1, 2, 1);
        Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 2, 2, 1);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        DamageSource source = helper.getLevel().damageSources().mobAttack(attacker);
        target.hurt(source, 2.0f);
        return target.getEffect(MobEffects.POISON);
    }

    @GameTest(template = "empty")
    public static void poisonedHitAppliesPoisonOnNormal(GameTestHelper helper) {
        withDifficulty(helper, Difficulty.NORMAL, () -> {
            ItemStack dagger = new ItemStack(bronzeDagger());
            dagger.set(PoisonItems.POISONED.get(), net.minecraft.util.Unit.INSTANCE);
            MobEffectInstance effect = hit(helper, dagger);
            helper.assertTrue(effect != null, "poisoned dagger hit applies Poison");
            helper.assertTrue(effect.getAmplifier() == 0, "Poison I");
            int ticks = effect.getDuration();
            helper.assertTrue(ticks >= 60 && ticks <= 140, "60-140 ticks on Normal, got " + ticks);
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unpoisonedHitAppliesNothing(GameTestHelper helper) {
        withDifficulty(helper, Difficulty.NORMAL, () -> {
            MobEffectInstance effect = hit(helper, new ItemStack(bronzeDagger()));
            helper.assertTrue(effect == null, "unpoisoned dagger applies no Poison");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void immuneTargetGetsNoPoison(GameTestHelper helper) {
        withDifficulty(helper, Difficulty.NORMAL, () -> {
            Pillager attacker = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 1, 2, 1);
            Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 2, 2, 1);
            ItemStack dagger = new ItemStack(bronzeDagger());
            dagger.set(PoisonItems.POISONED.get(), net.minecraft.util.Unit.INSTANCE);
            attacker.setItemSlot(EquipmentSlot.MAINHAND, dagger);
            DamageSource source = helper.getLevel().damageSources().mobAttack(attacker);
            helper.assertTrue(!PoisonHits.tryPoison(source, target, e -> true), "immune target is skipped");
            helper.assertTrue(target.getEffect(MobEffects.POISON) == null, "immune target has no Poison");
            helper.assertTrue(
                    PoisonHits.tryPoison(source, target, PoisonHits::isImmune), "a cow is not in the (unfilled) immune tag");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rangedDamageDoesNotPoison(GameTestHelper helper) {
        withDifficulty(helper, Difficulty.NORMAL, () -> {
            Pillager attacker = helper.spawnWithNoFreeWill(EntityType.PILLAGER, 1, 2, 1);
            Cow target = helper.spawnWithNoFreeWill(EntityType.COW, 2, 2, 1);
            ItemStack dagger = new ItemStack(bronzeDagger());
            dagger.set(PoisonItems.POISONED.get(), net.minecraft.util.Unit.INSTANCE);
            attacker.setItemSlot(EquipmentSlot.MAINHAND, dagger);
            DamageSource thorns = helper.getLevel().damageSources().thorns(attacker);
            helper.assertTrue(!PoisonHits.tryPoison(thorns, target, e -> false), "thorns damage does not poison");
            DamageSource magic = helper.getLevel().damageSources().magic();
            helper.assertTrue(!PoisonHits.tryPoison(magic, target, e -> false), "attackerless damage does not poison");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void durationScalesWithDifficulty(GameTestHelper helper) {
        RandomSource random = RandomSource.create(1234L);
        int minNormal = Integer.MAX_VALUE;
        int maxNormal = Integer.MIN_VALUE;
        for (int i = 0; i < 2000; i++) {
            int normal = PoisonHits.durationTicks(random, Difficulty.NORMAL);
            int easy = PoisonHits.durationTicks(random, Difficulty.EASY);
            int hard = PoisonHits.durationTicks(random, Difficulty.HARD);
            minNormal = Math.min(minNormal, normal);
            maxNormal = Math.max(maxNormal, normal);
            helper.assertTrue(normal >= 60 && normal <= 140, "Normal 60-140, got " + normal);
            helper.assertTrue(easy >= 30 && easy <= 70, "Easy 30-70 (x0.5), got " + easy);
            helper.assertTrue(hard >= 90 && hard <= 210, "Hard 90-210 (x1.5), got " + hard);
        }
        helper.assertTrue(minNormal < 70 && maxNormal > 130, "Normal range is actually spread: " + minNormal + ".." + maxNormal);
        helper.succeed();
    }
}
