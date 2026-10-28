package io.github.sethwillemsvg.lotrrevival.core.brewing;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A filled drink. Food value, effect durations and alcohol scale with the stack's
 * {@code drink_strength} tier. Stats are Java constants for the pilot.
 */
public class DrinkItem extends Item {
    public static final int USE_TICKS = 32;
    private static final int NAUSEA_MIN_TICKS = 1200;
    private static final int NAUSEA_MAX_TICKS = 1800;

    /** One effect at Hearty strength. Amplifier never scales. */
    public record DrinkEffect(Holder<MobEffect> effect, int baseDurationTicks, int amplifier) {}

    private final double alcohol;
    private final int nutrition;
    private final float saturationModifier;
    private final List<DrinkEffect> effects;

    public DrinkItem(
            Properties properties,
            double alcohol,
            int nutrition,
            float saturationModifier,
            List<DrinkEffect> effects) {
        super(properties);
        this.alcohol = alcohol;
        this.nutrition = nutrition;
        this.saturationModifier = saturationModifier;
        this.effects = List.copyOf(effects);
    }

    public double alcohol() {
        return alcohol;
    }

    public int baseNutrition() {
        return nutrition;
    }

    public float saturationModifier() {
        return saturationModifier;
    }

    public List<DrinkEffect> effects() {
        return effects;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // All pilot drinks can be drunk on a full hunger bar.
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        DrinkTier tier = BrewingComponents.tierOf(stack);
        if (!level.isClientSide) {
            applyDrink(level, entity, tier);
        }
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_DRINK,
                SoundSource.PLAYERS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);

        if (entity instanceof Player player) {
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            if (player.hasInfiniteMaterials()) {
                return stack;
            }
            ItemStack mug = new ItemStack(BrewingItems.WOODEN_MUG.get());
            stack.shrink(1);
            if (stack.isEmpty()) {
                return mug;
            }
            if (!player.getInventory().add(mug)) {
                player.drop(mug, false);
            }
            return stack;
        }
        stack.shrink(1);
        return stack;
    }

    /** Applies food, effects and the nausea roll for one finished drink. Server side only. */
    public void applyDrink(Level level, LivingEntity entity, DrinkTier tier) {
        if (entity instanceof Player player) {
            player.getFoodData().eat(tier.scaleNutrition(nutrition), saturationModifier);
        }
        for (DrinkEffect e : effects) {
            entity.addEffect(new MobEffectInstance(
                    e.effect(), tier.scaleDuration(e.baseDurationTicks()), e.amplifier()));
        }
        double power = tier.alcoholPower(alcohol);
        if (power > 0) {
            RandomSource random = entity.getRandom();
            if (random.nextDouble() < Math.min(1.0, power)) {
                int base = NAUSEA_MIN_TICKS + random.nextInt(NAUSEA_MAX_TICKS - NAUSEA_MIN_TICKS + 1);
                int duration = (int) Math.floor(base * power);
                if (duration > 0) {
                    entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 0));
                }
            }
        }
    }

    @Override
    public void appendHoverText(
            ItemStack stack, TooltipContext context, List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        DrinkTier tier = BrewingComponents.tierOf(stack);
        tooltip.add(tier.displayName());
        double power = tier.alcoholPower(alcohol);
        if (power > 0) {
            double percent = power * 10.0;
            tooltip.add(Component.translatable(
                            "tooltip.lotrrevival.alcohol", String.format(Locale.ROOT, "%.1f", percent))
                    .withStyle(alcoholColor(percent)));
        }
        float tickRate = context.tickRate();
        for (DrinkEffect e : effects) {
            MobEffectInstance instance =
                    new MobEffectInstance(e.effect(), tier.scaleDuration(e.baseDurationTicks()), e.amplifier());
            MutableComponent line = Component.translatable(instance.getDescriptionId());
            if (instance.getAmplifier() > 0) {
                line = Component.translatable(
                        "potion.withAmplifier", line, Component.translatable("potion.potency." + instance.getAmplifier()));
            }
            if (!instance.endsWithin(20)) {
                line = Component.translatable(
                        "potion.withDuration", line, MobEffectUtil.formatDuration(instance, 1.0F, tickRate));
            }
            tooltip.add(line.withStyle(e.effect().value().getCategory().getTooltipFormatting()));
        }
    }

    /** Color band for the alcohol percentage line. */
    public static ChatFormatting alcoholColor(double percent) {
        if (percent < 2) {
            return ChatFormatting.GREEN;
        } else if (percent < 5) {
            return ChatFormatting.YELLOW;
        } else if (percent < 10) {
            return ChatFormatting.GOLD;
        } else if (percent < 20) {
            return ChatFormatting.RED;
        }
        return ChatFormatting.DARK_RED;
    }
}
