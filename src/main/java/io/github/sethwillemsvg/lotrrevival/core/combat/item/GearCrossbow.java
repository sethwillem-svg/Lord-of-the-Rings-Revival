package io.github.sethwillemsvg.lotrrevival.core.combat.item;

import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.CrossbowGear;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Bronze and iron crossbows (specs/core/combat-items.md, "Ranged weapons"). A vanilla crossbow that
 * loads only {@code #lotrrevival:bolts}, takes 50 ticks to charge (Quick Charge takes 5 off per
 * level, through the vanilla enchantment effect) and fires one bolt whose base damage is 2.0 times
 * the crossbow's damage factor.
 */
public class GearCrossbow extends CrossbowItem {
    /** Ticks to charge with no enchantments. */
    public static final int BASE_CHARGE_TICKS = 50;

    /** Launch speed of a bolt, the same as a vanilla crossbow arrow. */
    public static final float BOLT_VELOCITY = 3.15F;

    /** Only bolts fit: vanilla arrows and fireworks are not accepted. */
    public static final Predicate<ItemStack> BOLT_ONLY = stack -> stack.is(ModTags.Items.BOLTS);

    private static final CrossbowItem.ChargingSounds DEFAULT_SOUNDS = new CrossbowItem.ChargingSounds(
            Optional.of(SoundEvents.CROSSBOW_LOADING_START),
            Optional.of(SoundEvents.CROSSBOW_LOADING_MIDDLE),
            Optional.of(SoundEvents.CROSSBOW_LOADING_END));

    private final CombatMaterial material;
    private final double damageFactor;

    public GearCrossbow(CombatMaterial material, double damageFactor) {
        super(new Item.Properties().durability(material.durability()));
        this.material = material;
        this.damageFactor = damageFactor;
    }

    public CombatMaterial material() {
        return material;
    }

    /** Multiplier on a bolt's base damage of 2.0. */
    public double damageFactor() {
        return damageFactor;
    }

    /** Ticks to charge this stack for {@code shooter}, after Quick Charge and other enchantments. */
    public int chargeDuration(ItemStack stack, LivingEntity shooter) {
        float seconds = EnchantmentHelper.modifyCrossbowChargingTime(stack, shooter, BASE_CHARGE_TICKS / 20.0F);
        return Mth.floor(seconds * 20.0F);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return chargeDuration(stack, entity) + 3;
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return BOLT_ONLY;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return BOLT_ONLY;
    }

    @Override
    public ItemStack getDefaultCreativeAmmo(@Nullable Player player, ItemStack projectileWeaponItem) {
        return new ItemStack(CrossbowGear.BOLT.get());
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        arrow.setBaseDamage(arrow.getBaseDamage() * damageFactor);
        return arrow;
    }

    @Override
    public int getEnchantmentValue() {
        return material.enchantability();
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(material.ingotTag());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
        if (charged != null && !charged.isEmpty()) {
            performShooting(level, player, hand, stack, BOLT_VELOCITY, 1.0F, null);
            return InteractionResultHolder.consume(stack);
        }
        if (!player.getProjectile(stack).isEmpty()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int elapsed = getUseDuration(stack, entity) - timeLeft;
        if (elapsed >= chargeDuration(stack, entity) && !isCharged(stack) && tryLoad(entity, stack)) {
            sounds(stack).end().ifPresent(sound -> level.playSound(
                    null, entity.getX(), entity.getY(), entity.getZ(), sound.value(), entity.getSoundSource(), 1.0F,
                    1.0F / (level.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F));
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
        if (level.isClientSide) {
            return;
        }
        int charge = chargeDuration(stack, entity);
        int elapsed = getUseDuration(stack, entity) - count;
        if (elapsed == Mth.ceil(0.2F * charge)) {
            sounds(stack).start().ifPresent(sound -> level.playSound(
                    null, entity.getX(), entity.getY(), entity.getZ(), sound.value(), SoundSource.PLAYERS, 0.5F, 1.0F));
        }
        if (elapsed == Mth.ceil(0.5F * charge)) {
            sounds(stack).mid().ifPresent(sound -> level.playSound(
                    null, entity.getX(), entity.getY(), entity.getZ(), sound.value(), SoundSource.PLAYERS, 0.5F, 1.0F));
        }
    }

    private static boolean tryLoad(LivingEntity shooter, ItemStack crossbow) {
        List<ItemStack> drawn = draw(crossbow, shooter.getProjectile(crossbow), shooter);
        if (drawn.isEmpty()) {
            return false;
        }
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(drawn));
        return true;
    }

    private static CrossbowItem.ChargingSounds sounds(ItemStack stack) {
        return EnchantmentHelper.pickHighestLevel(stack, EnchantmentEffectComponents.CROSSBOW_CHARGING_SOUNDS)
                .orElse(DEFAULT_SOUNDS);
    }
}
