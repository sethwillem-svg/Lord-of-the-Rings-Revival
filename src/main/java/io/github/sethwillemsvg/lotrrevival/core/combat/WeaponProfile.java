package io.github.sethwillemsvg.lotrrevival.core.combat;

/**
 * What the NPC pod needs to know about a held weapon. NPC melee cooldown is 20 ticks / {@link #speedFactor}
 * and the attack reach is multiplied by {@link #reachFactor}.
 *
 * @param weaponClass the class
 * @param material the material
 * @param damage total damage per hit in half-hearts (player base 1 included)
 * @param speedFactor swing speed relative to a sword
 * @param reachFactor reach relative to the base 3.0 blocks (0.75 to 2.0)
 * @param knockback extra knockback
 */
public record WeaponProfile(
        WeaponClass weaponClass,
        CombatMaterial material,
        double damage,
        double speedFactor,
        double reachFactor,
        double knockback) {
    public static WeaponProfile of(WeaponClass weaponClass, CombatMaterial material) {
        return new WeaponProfile(
                weaponClass,
                material,
                weaponClass.damage(material),
                weaponClass.speedFactor(),
                weaponClass.reachFactor(),
                weaponClass.knockback());
    }
}
