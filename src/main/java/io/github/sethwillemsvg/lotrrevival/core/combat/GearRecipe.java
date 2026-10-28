package io.github.sethwillemsvg.lotrrevival.core.combat;

import java.util.List;
import java.util.Map;

/**
 * Crafting recipe description for one gear entry. The datagen provider turns it into vanilla
 * {@code crafting_shaped} / {@code crafting_shapeless}, or {@code lotrrevival:faction_shaped} /
 * {@code faction_shapeless} when {@link #table()} is not the vanilla table.
 *
 * @param table where it is crafted
 * @param pattern shaped rows (empty for shapeless)
 * @param keys pattern key to ingredient (shaped)
 * @param ingredients shapeless ingredients (empty for shaped)
 * @param count result count
 */
public record GearRecipe(
        CraftTable table, List<String> pattern, Map<Character, RecipeKey> keys, List<RecipeKey> ingredients, int count) {
    public static GearRecipe shaped(CraftTable table, List<String> pattern, Map<Character, RecipeKey> keys) {
        return new GearRecipe(table, List.copyOf(pattern), Map.copyOf(keys), List.of(), 1);
    }

    public static GearRecipe shapeless(CraftTable table, int count, List<RecipeKey> ingredients) {
        return new GearRecipe(table, List.of(), Map.of(), List.copyOf(ingredients), count);
    }

    public boolean isShaped() {
        return !pattern.isEmpty();
    }
}
