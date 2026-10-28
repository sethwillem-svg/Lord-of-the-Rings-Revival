package io.github.sethwillemsvg.lotrrevival.core.combat.item;

import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.PebbleProjectile;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * The hobbit sling (specs/core/combat-items.md, "Ranged weapons"): hold to wind up, release to throw
 * one pebble. Under {@link #MIN_WIND_UP} ticks nothing is thrown; the throw reaches full damage
 * ({@link PebbleProjectile#FULL_DAMAGE}) and speed ({@link #FULL_VELOCITY}) at {@link #FULL_WIND_UP}
 * ticks and scales linearly below that.
 */
public class SlingItem extends Item {
    public static final int MIN_WIND_UP = 4;
    public static final int FULL_WIND_UP = 10;
    public static final float FULL_VELOCITY = 1.5F;

    public SlingItem(int durability) {
        super(new Item.Properties().durability(durability));
    }

    /** Fraction of full power for a wind-up of {@code ticks}, 0 to 1; 0 below the minimum. */
    public static float power(int ticks) {
        if (ticks < MIN_WIND_UP) {
            return 0.0F;
        }
        return Math.min(ticks, FULL_WIND_UP) / (float) FULL_WIND_UP;
    }

    private static ItemStack findPebble(Player player) {
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(ModTags.Items.PEBBLES)) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModTags.Items.PEBBLES)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack sling = player.getItemInHand(hand);
        if (!player.hasInfiniteMaterials() && findPebble(player).isEmpty()) {
            return InteractionResultHolder.fail(sling);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(sling);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity shooter, int timeLeft) {
        if (!(shooter instanceof Player player)) {
            return;
        }
        float power = power(getUseDuration(stack, shooter) - timeLeft);
        if (power <= 0.0F) {
            return;
        }
        ItemStack pebble = findPebble(player);
        boolean infinite = player.hasInfiniteMaterials();
        if (pebble.isEmpty() && !infinite) {
            return;
        }
        if (level instanceof ServerLevel server) {
            PebbleProjectile projectile = new PebbleProjectile(server, player);
            projectile.setDamage(PebbleProjectile.FULL_DAMAGE * power);
            projectile.shootFromRotation(
                    player, player.getXRot(), player.getYRot(), 0.0F, FULL_VELOCITY * power, 1.0F);
            server.addFreshEntity(projectile);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!infinite) {
            pebble.shrink(1);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }
}
