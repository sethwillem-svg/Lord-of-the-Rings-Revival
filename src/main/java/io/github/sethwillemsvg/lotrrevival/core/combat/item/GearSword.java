package io.github.sethwillemsvg.lotrrevival.core.combat.item;

import io.github.sethwillemsvg.lotrrevival.core.combat.ClassedWeapon;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;

/** The sword class: a vanilla sword (sweeps, cobweb speed) with our attribute numbers. */
public class GearSword extends SwordItem implements ClassedWeapon {
    private final CombatMaterial material;

    public GearSword(CombatMaterial material) {
        super(material, new Item.Properties().attributes(WeaponClass.SWORD.attributes(material)));
        this.material = material;
    }

    @Override
    public WeaponClass weaponClass() {
        return WeaponClass.SWORD;
    }

    @Override
    public CombatMaterial combatMaterial() {
        return material;
    }
}
