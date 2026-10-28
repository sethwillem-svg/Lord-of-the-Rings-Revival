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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Ticket #118: orc steel weapons and Mordor armour (specs/core/combat-items.md, "Weapon sets in this
 * spec" and "Armour"). Everything is crafted on the Mordor table. {@code CombatItems.init} already
 * calls {@link #init()}.
 */
public final class MordorGear {
    private MordorGear() {}

    /** Weapon set {@code mordor}: orc steel, Mordor table, English adjective "Orcish". */
    public static final GearSet SET =
            new GearSet("mordor", "Orcish", CombatMaterial.ORC_STEEL, CraftTable.culture("mordor"));

    public static final DeferredItem<Item> DAGGER = Gear.weapon(SET, WeaponClass.DAGGER);
    public static final DeferredItem<Item> SWORD = Gear.weapon(SET, WeaponClass.SWORD);
    public static final DeferredItem<Item> SPEAR = Gear.weapon(SET, WeaponClass.SPEAR);
    public static final DeferredItem<Item> PIKE = Gear.weapon(SET, WeaponClass.PIKE);
    public static final DeferredItem<Item> BATTLEAXE = Gear.weapon(SET, WeaponClass.BATTLEAXE);
    public static final DeferredItem<Item> HAMMER = Gear.weapon(SET, WeaponClass.HAMMER);

    /** Armour set {@code mordor}: p 0.6, 350 uses, orc steel ingot repair, tagged {@code armor/mordor}. */
    public static final ArmorSet ARMOR_SET = ArmorSet.metal(
            "mordor", 0.6, 350, () -> Ingredient.of(CombatMaterial.ORC_STEEL.ingotTag()), Factions.MORDOR);

    public static final ArmorPieces ARMOR =
            Gear.armor(ARMOR_SET, "Orcish", SET.table(), RecipeKey.of(CombatMaterial.ORC_STEEL.ingotTag()));

    /** Loads the class so its static gear fields register. Called from {@code CombatItems.init}. */
    public static void init() {}
}
