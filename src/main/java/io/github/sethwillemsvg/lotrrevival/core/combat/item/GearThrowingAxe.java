package io.github.sethwillemsvg.lotrrevival.core.combat.item;

import io.github.sethwillemsvg.lotrrevival.core.combat.ClassedWeapon;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import net.minecraft.world.item.Item;

/**
 * Throwing axes: ammo-like (stack 16, no durability) with weak melee numbers. The throw itself
 * belongs to the throwing ticket.
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
}
