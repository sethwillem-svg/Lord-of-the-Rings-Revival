package io.github.sethwillemsvg.lotrrevival.core.combat;

import java.util.Map;

/**
 * A named weapon set: an item id prefix, an English adjective, the material and the table that
 * crafts it (specs/core/combat-items.md, "Weapon sets in this spec"). Item ids are
 * {@code <id>_<class>}, English names "<adjective> <Class>".
 *
 * @param id item id prefix (for example {@code gondor})
 * @param adjective English adjective (for example {@code Gondorian})
 * @param material the material
 * @param table crafting table
 */
public record GearSet(String id, String adjective, CombatMaterial material, CraftTable table) {
    /** The set's metal as a recipe key ({@code c:ingots/<metal>}). */
    public RecipeKey metal() {
        return RecipeKey.of(material.ingotTag());
    }

    /** Recipe for a weapon class: the class shape with {@code M} the set's metal and {@code S} a stick. */
    public GearRecipe recipe(WeaponClass weaponClass) {
        return GearRecipe.shaped(
                table,
                weaponClass.recipeShape(),
                Map.of('M', metal(), 'S', RecipeKey.of(net.minecraft.world.item.Items.STICK)));
    }
}
