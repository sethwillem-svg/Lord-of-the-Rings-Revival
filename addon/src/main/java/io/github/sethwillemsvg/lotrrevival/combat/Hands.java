package io.github.sethwillemsvg.lotrrevival.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What an NPC fights with. Middle-earth sometimes gives a unit its shield in the main hand and its weapon in the off hand
 * (Gondor soldiers: "Gondorian Shield + Gondorian Axe"); those are read swapped, so the weapon swings and the shield guards.
 */
public final class Hands {
    private Hands() {}

    /** A shield in the main hand and a weapon in the off hand. */
    public static boolean swapped(LivingEntity e) {
        String main = WeaponClass.of(e.getMainHandItem()), off = WeaponClass.of(e.getOffhandItem());
        return "shield".equals(main) && off != null && !"shield".equals(off);
    }

    /** The item it attacks with. */
    public static ItemStack weapon(LivingEntity e) {
        return swapped(e) ? e.getOffhandItem() : e.getMainHandItem();
    }

    /** The item in its other hand (a shield, a second dagger, ...). */
    public static ItemStack other(LivingEntity e) {
        return swapped(e) ? e.getMainHandItem() : e.getOffhandItem();
    }
}
