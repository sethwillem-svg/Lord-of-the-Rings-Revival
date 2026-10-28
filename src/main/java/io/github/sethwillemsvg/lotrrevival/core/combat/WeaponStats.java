package io.github.sethwillemsvg.lotrrevival.core.combat;

import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** Read-only weapon lookup for other pods (NPC AI, loadouts). */
public final class WeaponStats {
    private WeaponStats() {}

    /** The profile of a stack made through {@link Gear#weapon}, or empty for anything else. */
    public static Optional<WeaponProfile> of(ItemStack stack) {
        if (stack.getItem() instanceof ClassedWeapon weapon) {
            return Optional.of(WeaponProfile.of(weapon.weaponClass(), weapon.combatMaterial()));
        }
        return Optional.empty();
    }
}
