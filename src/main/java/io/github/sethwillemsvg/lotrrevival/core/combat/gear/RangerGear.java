package io.github.sethwillemsvg.lotrrevival.core.combat.gear;

import io.github.sethwillemsvg.lotrrevival.core.combat.ArmorPieces;
import io.github.sethwillemsvg.lotrrevival.core.combat.ArmorSet;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.CraftTable;
import io.github.sethwillemsvg.lotrrevival.core.combat.Gear;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearSet;
import io.github.sethwillemsvg.lotrrevival.core.combat.RecipeKey;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Ranger gear (specs/core/combat-items.md, ticket #114): the {@code ranger} weapon set, the leather
 * {@code ranger} armour and the iron {@code arnor} armour. All of it is crafted on the ranger table
 * and the armour joins {@code armor/dunedain_north}.
 */
public final class RangerGear {
    private RangerGear() {}

    public static final CraftTable TABLE = CraftTable.culture("ranger");

    public static final GearSet WEAPON_SET = new GearSet("ranger", "Ranger", CombatMaterial.RANGER, TABLE);

    static {
        Gear.weapons(WEAPON_SET, WeaponClass.DAGGER, WeaponClass.SWORD, WeaponClass.SPEAR, WeaponClass.BATTLEAXE);
    }

    /** Leather Ranger armour: p 0.48, 350 uses. */
    public static final ArmorSet RANGER_ARMOR = ArmorSet.cloth(
            "ranger", 0.48, 350, () -> Ingredient.of(Items.LEATHER), Factions.DUNEDAIN_NORTH);

    public static final ArmorPieces RANGER_PIECES =
            Gear.armor(RANGER_ARMOR, "Ranger", TABLE, RecipeKey.of(Items.LEATHER));

    /** Iron Arnorian armour: p 0.6, 450 uses. */
    public static final ArmorSet ARNOR_ARMOR = ArmorSet.metal(
            "arnor", 0.6, 450, () -> Ingredient.of(CombatMaterial.IRON.ingotTag()), Factions.DUNEDAIN_NORTH);

    public static final ArmorPieces ARNOR_PIECES =
            Gear.armor(ARNOR_ARMOR, "Arnorian", TABLE, RecipeKey.of(CombatMaterial.IRON.ingotTag()));

    /** Loads the class so its static gear fields register. Called from {@code CombatItems.init}. */
    public static void init() {}
}
