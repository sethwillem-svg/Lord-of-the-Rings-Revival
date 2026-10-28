package io.github.sethwillemsvg.lotrrevival.core.combat.gear;

import io.github.sethwillemsvg.lotrrevival.core.combat.CombatMaterial;
import io.github.sethwillemsvg.lotrrevival.core.combat.CraftTable;
import io.github.sethwillemsvg.lotrrevival.core.combat.Gear;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearRecipe;
import io.github.sethwillemsvg.lotrrevival.core.combat.RecipeKey;
import io.github.sethwillemsvg.lotrrevival.core.combat.item.BoltItem;
import io.github.sethwillemsvg.lotrrevival.core.combat.item.GearCrossbow;
import io.github.sethwillemsvg.lotrrevival.core.combat.ranged.BoltEntities;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Crossbows and bolts (specs/core/combat-items.md, "Ranged weapons", ticket #121), kept in a sibling
 * of {@link RangedGear} so the bows and sling tickets do not collide with it.
 *
 * <p>Damage factors follow the spec table: bronze x1.95, iron x2.0. (The spec's formula
 * {@code (1 + 0.1 x (material damage - 2)) x 2} gives 1.9 for bronze; the table and the acceptance
 * numbers, bronze bolt 3.9, win.)
 */
public final class CrossbowGear {
    private CrossbowGear() {}

    public static final double BRONZE_FACTOR = 1.95;
    public static final double IRON_FACTOR = 2.0;

    /** 1 iron or bronze ingot + stick + feather, shapeless, gives 4. */
    public static final DeferredItem<Item> BOLT = Gear.ammo(
            "bolt",
            "Bolt",
            () -> new BoltItem(new Item.Properties()),
            List.of(ModTags.Items.BOLTS),
            boltRecipe(CombatMaterial.IRON),
            boltRecipe(CombatMaterial.BRONZE));

    public static final DeferredItem<Item> BRONZE_CROSSBOW = Gear.crossbow(
            "bronze_crossbow",
            "Bronze Crossbow",
            () -> new GearCrossbow(CombatMaterial.BRONZE, BRONZE_FACTOR),
            crossbowRecipe(CombatMaterial.BRONZE));

    public static final DeferredItem<Item> IRON_CROSSBOW = Gear.crossbow(
            "iron_crossbow",
            "Iron Crossbow",
            () -> new GearCrossbow(CombatMaterial.IRON, IRON_FACTOR),
            crossbowRecipe(CombatMaterial.IRON));

    private static GearRecipe boltRecipe(CombatMaterial material) {
        return GearRecipe.shapeless(
                CraftTable.VANILLA,
                4,
                List.of(RecipeKey.of(material.ingotTag()), RecipeKey.of(Items.STICK), RecipeKey.of(Items.FEATHER)));
    }

    /** Distinct from the vanilla crossbow shape (ingots and sticks swapped) so the two never clash. */
    private static GearRecipe crossbowRecipe(CombatMaterial material) {
        return GearRecipe.shaped(
                CraftTable.VANILLA,
                List.of("MSM", "THT", " S "),
                Map.of(
                        'M', RecipeKey.of(material.ingotTag()),
                        'S', RecipeKey.of(Items.STICK),
                        'T', RecipeKey.of(Items.STRING),
                        'H', RecipeKey.of(Items.TRIPWIRE_HOOK)));
    }

    /** Loads the class so the gear and the bolt entity register. Called from {@link RangedGear#init()}. */
    public static void init() {
        BoltEntities.init();
    }
}
