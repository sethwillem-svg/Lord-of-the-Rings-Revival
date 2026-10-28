package io.github.sethwillemsvg.lotrrevival.core.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingComponents;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingItems;
import io.github.sethwillemsvg.lotrrevival.core.brewing.DrinkTier;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Brewing GameTests. Ticket 1 covers drinking; the barrel tests land with ticket 2. */
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
}
