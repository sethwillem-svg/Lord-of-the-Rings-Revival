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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Gondor gear (specs/core/combat-items.md): the {@code gondor} weapon set (including the lance), the
 * {@code gondor} iron armour and the {@code gondor_levy} white wool armour, all crafted on the Gondor table.
 */
public final class GondorGear {
    private GondorGear() {}

    public static final String ID = "gondor";

    public static final GearSet WEAPONS =
            new GearSet(ID, "Gondorian", CombatMaterial.GONDOR, CraftTable.culture("gondor"));

    public static final DeferredItem<Item> DAGGER = Gear.weapon(WEAPONS, WeaponClass.DAGGER);
    public static final DeferredItem<Item> SWORD = Gear.weapon(WEAPONS, WeaponClass.SWORD);
    public static final DeferredItem<Item> SPEAR = Gear.weapon(WEAPONS, WeaponClass.SPEAR);
    public static final DeferredItem<Item> PIKE = Gear.weapon(WEAPONS, WeaponClass.PIKE);
    public static final DeferredItem<Item> BATTLEAXE = Gear.weapon(WEAPONS, WeaponClass.BATTLEAXE);
    public static final DeferredItem<Item> HAMMER = Gear.weapon(WEAPONS, WeaponClass.HAMMER);
    public static final DeferredItem<Item> LANCE = Gear.weapon(WEAPONS, WeaponClass.LANCE);

    /** Iron-tier plate: p 0.6, uses 450, iron ingots. */
    public static final ArmorSet ARMOR_SET =
            ArmorSet.metal(ID, 0.6, 450, () -> Ingredient.of(CombatMaterial.GONDOR.ingotTag()), Factions.GONDOR);
    public static final ArmorPieces ARMOR = Gear.armor(
            ARMOR_SET, "Gondorian", CraftTable.culture("gondor"), RecipeKey.of(CombatMaterial.GONDOR.ingotTag()));

    /** Levy gambeson: p 0.4, uses 200, white wool. */
    public static final ArmorSet LEVY_SET =
            ArmorSet.cloth("gondor_levy", 0.4, 200, () -> Ingredient.of(Items.WHITE_WOOL), Factions.GONDOR);
    public static final ArmorPieces LEVY = Gear.armor(
            LEVY_SET, "Gondorian Levy", CraftTable.culture("gondor"), RecipeKey.of(Items.WHITE_WOOL));

    /** Loads the class so its static gear fields register. Called from {@code CombatItems.init}. */
    public static void init() {}
}
