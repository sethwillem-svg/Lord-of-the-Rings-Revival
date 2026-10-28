package io.github.sethwillemsvg.lotrrevival.core.combat.gear;

import io.github.sethwillemsvg.lotrrevival.core.combat.ArmorPieces;
import io.github.sethwillemsvg.lotrrevival.core.combat.ArmorSet;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.CraftTable;
import io.github.sethwillemsvg.lotrrevival.core.combat.Gear;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearRecipe;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearSet;
import io.github.sethwillemsvg.lotrrevival.core.combat.RecipeKey;
import io.github.sethwillemsvg.lotrrevival.core.combat.ToolKind;
import io.github.sethwillemsvg.lotrrevival.core.combat.WeaponClass;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Ticket #110: the generic bronze and iron weapon sets, bronze tools and bronze armour, all crafted
 * on the vanilla table (specs/core/combat-items.md, "Weapon sets in this spec"). Iron has no sword
 * (vanilla's is the sword) and no tools or armour of its own.
 */
public final class GeneralGear {
    private GeneralGear() {}

    public static final GearSet BRONZE = new GearSet("bronze", "Bronze", CombatMaterial.BRONZE, CraftTable.VANILLA);
    public static final GearSet IRON = new GearSet("iron", "Iron", CombatMaterial.IRON, CraftTable.VANILLA);

    public static final ArmorSet BRONZE_ARMOR =
            ArmorSet.metal("bronze", 0.5, 230, () -> Ingredient.of(CombatMaterial.BRONZE.ingotTag()), null);

    static {
        Gear.weapons(
                BRONZE,
                WeaponClass.DAGGER,
                WeaponClass.SWORD,
                WeaponClass.BATTLEAXE,
                WeaponClass.THROWING_AXE);
        // The spec's spear shape (M, S, S) is identical to the vanilla shovel shape, which the bronze set
        // also crafts on the vanilla table. The bronze spear takes an offset shape instead.
        Gear.weapon(
                BRONZE,
                WeaponClass.SPEAR,
                GearRecipe.shaped(
                        CraftTable.VANILLA,
                        List.of(" M", " S", "S "),
                        Map.of('M', BRONZE.metal(), 'S', RecipeKey.of(Items.STICK))));
        Gear.tool(BRONZE, ToolKind.PICKAXE);
        Gear.tool(BRONZE, ToolKind.AXE);
        Gear.tool(BRONZE, ToolKind.SHOVEL);
        Gear.tool(BRONZE, ToolKind.HOE);

        Gear.weapons(
                IRON,
                WeaponClass.DAGGER,
                WeaponClass.SPEAR,
                WeaponClass.PIKE,
                WeaponClass.BATTLEAXE,
                WeaponClass.HAMMER,
                WeaponClass.THROWING_AXE);
    }

    public static final ArmorPieces BRONZE_PIECES =
            Gear.armor(BRONZE_ARMOR, "Bronze", CraftTable.VANILLA, RecipeKey.of(CombatMaterial.BRONZE.ingotTag()));

    /** Loads the class so its static gear fields register. Called from {@code CombatItems.init}. */
    public static void init() {}
}
