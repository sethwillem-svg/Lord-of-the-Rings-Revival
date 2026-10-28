package io.github.sethwillemsvg.lotrrevival.datagen;

import io.github.sethwillemsvg.lotrrevival.core.table.FactionShapedRecipe;
import io.github.sethwillemsvg.lotrrevival.core.table.TableRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.ItemLike;

/** Datagen builder for {@code lotrrevival:faction_shaped} recipes (no advancement; table recipes are not unlocked). */
public final class FactionShapedRecipeBuilder {
    private final TableRef table;
    private final ItemStack result;
    private final List<String> rows = new ArrayList<>();
    private final Map<Character, Ingredient> key = new LinkedHashMap<>();
    private String group = "";
    private CraftingBookCategory category = CraftingBookCategory.MISC;

    private FactionShapedRecipeBuilder(TableRef table, ItemLike result, int count) {
        this.table = table;
        this.result = new ItemStack(result, count);
    }

    /** {@code table} is a block id or a {@code #} block tag. */
    public static FactionShapedRecipeBuilder shaped(TableRef table, ItemLike result, int count) {
        return new FactionShapedRecipeBuilder(table, result, count);
    }

    public FactionShapedRecipeBuilder define(char symbol, TagKey<Item> tag) {
        return define(symbol, Ingredient.of(tag));
    }

    public FactionShapedRecipeBuilder define(char symbol, ItemLike item) {
        return define(symbol, Ingredient.of(item));
    }

    public FactionShapedRecipeBuilder define(char symbol, Ingredient ingredient) {
        if (key.containsKey(symbol)) {
            throw new IllegalArgumentException("Symbol '" + symbol + "' is already defined");
        }
        key.put(symbol, ingredient);
        return this;
    }

    public FactionShapedRecipeBuilder pattern(String row) {
        rows.add(row);
        return this;
    }

    public FactionShapedRecipeBuilder group(String group) {
        this.group = group;
        return this;
    }

    public FactionShapedRecipeBuilder category(RecipeCategory category) {
        this.category = switch (category) {
            case BUILDING_BLOCKS -> CraftingBookCategory.BUILDING;
            case TOOLS, COMBAT -> CraftingBookCategory.EQUIPMENT;
            case REDSTONE -> CraftingBookCategory.REDSTONE;
            default -> CraftingBookCategory.MISC;
        };
        return this;
    }

    public void save(RecipeOutput out, ResourceLocation id) {
        out.accept(
                id,
                new FactionShapedRecipe(table, group, category, ShapedRecipePattern.of(key, rows), result, true),
                null);
    }
}
