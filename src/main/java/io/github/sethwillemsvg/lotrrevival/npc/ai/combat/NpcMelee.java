package io.github.sethwillemsvg.lotrrevival.npc.ai.combat;

import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponProfile;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponStats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Weapon-class melee timing and reach for NPCs (specs/core/combat-items.md, "Weapon classes"): the swing cooldown is
 * 20 ticks divided by the weapon's speed factor, and the horizontal attack reach is multiplied by its reach factor.
 * Anything that is not a classed core weapon (bare hands, vanilla items) behaves as a sword: 20 ticks, factor 1.
 */
public final class NpcMelee {
    /** Ticks between swings with a sword-speed weapon. */
    public static final int BASE_COOLDOWN_TICKS = 20;
    /** How far past its own hitbox a vanilla mob reaches horizontally (the vanilla mob melee reach). */
    public static final double BASE_REACH = Math.sqrt(2.04F) - 0.6F;

    private NpcMelee() {}

    /** Ticks between swings for the held stack, at least 1. */
    public static int cooldownTicks(ItemStack held) {
        double speed = WeaponStats.of(held).map(WeaponProfile::speedFactor).orElse(1.0);
        return Math.max(1, (int) Math.round(BASE_COOLDOWN_TICKS / speed));
    }

    /** Reach multiplier of the held stack (0.75 dagger to 2.0 pike), 1 for anything unclassed. */
    public static double reachFactor(ItemStack held) {
        return WeaponStats.of(held).map(WeaponProfile::reachFactor).orElse(1.0);
    }

    /** The horizontal reach in blocks past the NPC's hitbox for the held stack. */
    public static double reach(ItemStack held) {
        return BASE_REACH * reachFactor(held);
    }

    /** Whether the target's hitbox is inside the reach box around the attacker's hitbox. */
    public static boolean inReach(Mob attacker, LivingEntity target) {
        double reach = reach(attacker.getMainHandItem());
        AABB box = attacker.getBoundingBox().inflate(reach, 0.0, reach);
        return box.intersects(target.getHitbox());
    }
}
