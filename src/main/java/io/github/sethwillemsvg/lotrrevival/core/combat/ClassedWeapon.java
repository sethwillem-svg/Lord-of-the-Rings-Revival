package io.github.sethwillemsvg.lotrrevival.core.combat;

/** Implemented by every item made through {@link Gear#weapon}; {@link WeaponStats} reads it. */
public interface ClassedWeapon {
    WeaponClass weaponClass();

    CombatMaterial combatMaterial();
}
