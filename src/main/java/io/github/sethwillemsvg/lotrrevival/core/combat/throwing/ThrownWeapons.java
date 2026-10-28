package io.github.sethwillemsvg.lotrrevival.core.combat.throwing;

import io.github.sethwillemsvg.lotrrevival.core.combat.ClassedWeapon;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Throwing rules and numbers (specs/core/combat-items.md, "Thrown weapons"). The items call these from {@code use} and
 * {@code releaseUsing}; the NPC pod may call them too, since the thrower can be any living entity.
 */
public final class ThrownWeapons {
    /** A spear released before this many ticks of charge is not thrown. */
    public static final int MIN_SPEAR_CHARGE = 5;
    /** Charge ticks of a full throw. */
    public static final int FULL_SPEAR_CHARGE = 20;
    /** Thrown spear damage as a fraction of the melee damage at full charge, before the critical. */
    public static final float SPEAR_DAMAGE_FRACTION = 0.7f;
    /** Full-charge spear multiplier. */
    public static final float SPEAR_CRIT_MULTIPLIER = 1.5f;
    /** Thrown axe damage as a fraction of the material's sword damage. */
    public static final float AXE_DAMAGE_FRACTION = 0.5f;
    /** Ticks before another axe can be thrown. */
    public static final int AXE_COOLDOWN_TICKS = 10;

    private static final float SPEAR_MIN_SPEED = 1.5f;
    private static final float SPEAR_FULL_SPEED = 2.5f;
    private static final float AXE_SPEED = 2.0f;

    private ThrownWeapons() {}

    /** Damage of a spear thrown after {@code charge} ticks (clamped to 0..20). */
    public static float spearDamage(CombatMaterial material, int charge) {
        int clamped = Math.max(0, Math.min(charge, FULL_SPEAR_CHARGE));
        float damage = (float) WeaponClass.SPEAR.damage(material) * SPEAR_DAMAGE_FRACTION * clamped / FULL_SPEAR_CHARGE;
        return clamped >= FULL_SPEAR_CHARGE ? damage * SPEAR_CRIT_MULTIPLIER : damage;
    }

    /** Damage of a thrown axe. */
    public static float axeDamage(CombatMaterial material) {
        return (float) material.swordDamage() * AXE_DAMAGE_FRACTION;
    }

    /** Whether the thrower is a player whose items are not used up. */
    public static boolean keepsItem(LivingEntity thrower) {
        return thrower instanceof Player player && player.hasInfiniteMaterials();
    }

    /** True when throwing {@code stack} would break it (such a spear is not thrown, as with a trident). */
    public static boolean wouldBreak(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /**
     * Throws a spear held in {@code slot}: damages the held stack by 1, removes it unless the thrower keeps items, and
     * spawns the projectile. Returns the projectile, or {@code null} when nothing was thrown (not a spear, charge under
     * {@value #MIN_SPEAR_CHARGE} ticks, or the throw would break it).
     */
    @Nullable
    public static ThrownSpear throwSpear(
            ServerLevel level, LivingEntity thrower, ItemStack held, EquipmentSlot slot, int charge, float inaccuracy) {
        if (charge < MIN_SPEAR_CHARGE
                || !(held.getItem() instanceof ClassedWeapon weapon)
                || weapon.weaponClass() != WeaponClass.SPEAR) {
            return null;
        }
        boolean keeps = keepsItem(thrower);
        if (!keeps && wouldBreak(held)) {
            return null;
        }
        int clamped = Math.min(charge, FULL_SPEAR_CHARGE);
        float damage = spearDamage(weapon.combatMaterial(), clamped);
        if (!keeps) {
            held.hurtAndBreak(1, thrower, slot);
        }
        ItemStack thrownStack = held.copyWithCount(1);
        if (!keeps) {
            held.shrink(1);
        }
        ThrownSpear spear = new ThrownSpear(level, thrower);
        spear.configure(thrownStack, damage, clamped >= FULL_SPEAR_CHARGE, keeps);
        float speed = SPEAR_MIN_SPEED + (SPEAR_FULL_SPEED - SPEAR_MIN_SPEED) * clamped / FULL_SPEAR_CHARGE;
        spear.shootFromRotation(thrower, thrower.getXRot(), thrower.getYRot(), 0.0f, speed, inaccuracy);
        level.addFreshEntity(spear);
        level.playSound(null, spear, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
        return spear;
    }

    /**
     * Throws one axe from {@code held}: removes one from the stack unless the thrower keeps items, and spawns the
     * projectile. Returns {@code null} when {@code held} is not a throwing axe. The caller applies the cooldown.
     */
    @Nullable
    public static ThrownAxe throwAxe(ServerLevel level, LivingEntity thrower, ItemStack held, float inaccuracy) {
        if (!(held.getItem() instanceof ClassedWeapon weapon) || weapon.weaponClass() != WeaponClass.THROWING_AXE) {
            return null;
        }
        boolean keeps = keepsItem(thrower);
        ItemStack thrownStack = held.copyWithCount(1);
        if (!keeps) {
            held.shrink(1);
        }
        ThrownAxe axe = new ThrownAxe(level, thrower);
        axe.configure(thrownStack, axeDamage(weapon.combatMaterial()), false, keeps);
        axe.shootFromRotation(thrower, thrower.getXRot(), thrower.getYRot(), 0.0f, AXE_SPEED, inaccuracy);
        level.addFreshEntity(axe);
        level.playSound(null, axe, SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8f, 0.6f);
        return axe;
    }
}
