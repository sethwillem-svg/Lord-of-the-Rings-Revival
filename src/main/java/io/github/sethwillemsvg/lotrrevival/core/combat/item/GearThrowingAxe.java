package io.github.sethwillemsvg.lotrrevival.core.combat.item;

import io.github.sethwillemsvg.lotrrevival.core.combat.ClassedWeapon;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrownWeapons;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Throwing axes: ammo-like (stack 16, no durability) with weak melee numbers. Using one throws it at once; it always
 * lands as a pickup.
 */
public class GearThrowingAxe extends Item implements ClassedWeapon {
    private final CombatMaterial material;

    public GearThrowingAxe(CombatMaterial material) {
        super(new Item.Properties().stacksTo(16).attributes(WeaponClass.THROWING_AXE.attributes(material)));
        this.material = material;
    }

    @Override
    public WeaponClass weaponClass() {
        return WeaponClass.THROWING_AXE;
    }

    @Override
    public CombatMaterial combatMaterial() {
        return material;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            ThrownWeapons.throwAxe(server, player, stack, 1.0f);
            player.getCooldowns().addCooldown(this, ThrownWeapons.AXE_COOLDOWN_TICKS);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
