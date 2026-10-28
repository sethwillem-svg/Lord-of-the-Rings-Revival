package io.github.sethwillemsvg.lotrrevival.core.combat.item;

import io.github.sethwillemsvg.lotrrevival.core.combat.ClassedWeapon;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

/**
 * Daggers, spears, polearms, pikes, lances and hammers: melee-only tiered items that never sweep.
 * Spear and dagger specials (throwing, poison) are added by their own tickets.
 */
public class GearWeapon extends TieredItem implements ClassedWeapon {
    private final WeaponClass weaponClass;
    private final CombatMaterial material;

    public GearWeapon(WeaponClass weaponClass, CombatMaterial material) {
        super(material, new Item.Properties().attributes(weaponClass.attributes(material)));
        this.weaponClass = weaponClass;
        this.material = material;
    }

    @Override
    public WeaponClass weaponClass() {
        return weaponClass;
    }

    @Override
    public CombatMaterial combatMaterial() {
        return material;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }
}
