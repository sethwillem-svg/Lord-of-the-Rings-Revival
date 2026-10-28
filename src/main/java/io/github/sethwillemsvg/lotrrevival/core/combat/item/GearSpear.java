package io.github.sethwillemsvg.lotrrevival.core.combat.item;

import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrownWeapons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A spear: a melee weapon that is also charged and thrown like a trident (charge up to 20 ticks, nothing under 5).
 * Loyalty and Riptide do not apply: they are not offered for these items.
 */
public class GearSpear extends GearWeapon {
    /** Same as a trident: the player holds until release. */
    private static final int USE_DURATION = 72000;

    public GearSpear(CombatMaterial material) {
        super(WeaponClass.SPEAR, material);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.hasInfiniteMaterials() && ThrownWeapons.wouldBreak(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int charge = Math.min(getUseDuration(stack, entity) - timeLeft, ThrownWeapons.FULL_SPEAR_CHARGE);
        if (!(level instanceof ServerLevel server) || charge < ThrownWeapons.MIN_SPEAR_CHARGE) {
            return;
        }
        ThrownWeapons.throwSpear(
                server, entity, stack, LivingEntity.getSlotForHand(entity.getUsedItemHand()), charge, 1.0f);
        if (entity instanceof Player player) {
            player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        }
    }
}
