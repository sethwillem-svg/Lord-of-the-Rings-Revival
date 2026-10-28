package io.github.sethwillemsvg.lotrrevival.core.combat.item;

import io.github.sethwillemsvg.lotrrevival.core.combat.ClassedWeapon;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;

/**
 * The battleaxe class: an axe, so it mines {@code mineable/axe} at the material's tier speed, strips
 * logs and disables shields (NeoForge's default shield rule is "is an AxeItem").
 */
public class GearBattleaxe extends AxeItem implements ClassedWeapon {
    private final CombatMaterial material;

    public GearBattleaxe(CombatMaterial material) {
        super(material, new Item.Properties().attributes(WeaponClass.BATTLEAXE.attributes(material)));
        this.material = material;
    }

    @Override
    public WeaponClass weaponClass() {
        return WeaponClass.BATTLEAXE;
    }

    @Override
    public CombatMaterial combatMaterial() {
        return material;
    }
}
