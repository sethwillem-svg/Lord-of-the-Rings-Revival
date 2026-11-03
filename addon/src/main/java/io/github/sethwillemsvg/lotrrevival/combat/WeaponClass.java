package io.github.sethwillemsvg.lotrrevival.combat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.sevenstars.middleearth.item.items.HeldBannerItem;
import net.sevenstars.middleearth.item.items.weapons.CustomAxeWeaponItem;
import net.sevenstars.middleearth.item.items.weapons.CustomDaggerWeaponItem;
import net.sevenstars.middleearth.item.items.weapons.CustomLongswordWeaponItem;
import net.sevenstars.middleearth.item.items.weapons.CustomSpearWeaponItem;
import net.sevenstars.middleearth.item.items.weapons.CustomSwordWeaponItem;
import net.sevenstars.middleearth.item.items.weapons.ReachWeaponItem;
import net.sevenstars.middleearth.item.items.weapons.ranged.CustomBowWeaponItem;
import net.sevenstars.middleearth.item.items.weapons.ranged.CustomCrossbowWeaponItem;
import net.sevenstars.middleearth.item.items.weapons.ranged.CustomLongbowWeaponItem;
import org.jetbrains.annotations.Nullable;

/** Weapon classes of held items, from Middle-earth's weapon item classes (and vanilla's): how they are held and swung. */
public final class WeaponClass {
    private WeaponClass() {}

    /** The weapon class of a held item (dagger, sword, longsword, axe, spear, bow, longbow, crossbow, shield, banner), or null. */
    @Nullable
    public static String of(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof CustomDaggerWeaponItem) return "dagger";
        // Middle-earth files its scimitars with the longswords; they are one-handed curved swords
        if (item instanceof CustomLongswordWeaponItem) return BuiltInRegistries.ITEM.getKey(item).getPath().contains("scimitar") ? "sword" : "longsword";
        if (item instanceof CustomSpearWeaponItem || item instanceof ReachWeaponItem || item instanceof TridentItem) return "spear";
        if (item instanceof CustomAxeWeaponItem || item instanceof AxeItem) return "axe";
        if (item instanceof CustomSwordWeaponItem || item instanceof SwordItem) return "sword";
        if (item instanceof CustomLongbowWeaponItem) return "longbow";
        if (item instanceof CustomCrossbowWeaponItem || item instanceof CrossbowItem) return "crossbow";
        if (item instanceof CustomBowWeaponItem || item instanceof BowItem) return "bow";
        if (item instanceof ShieldItem) return "shield";
        if (item instanceof HeldBannerItem) return "banner";
        return null;
    }

}
