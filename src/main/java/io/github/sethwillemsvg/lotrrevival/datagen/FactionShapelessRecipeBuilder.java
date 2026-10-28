package io.github.sethwillemsvg.lotrrevival.datagen;

import io.github.sethwillemsvg.lotrrevival.core.table.FactionShapelessRecipe;
import io.github.sethwillemsvg.lotrrevival.core.table.TableRef;
import net.minecraft.core.NonNullList;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

/** Datagen builder for {@code lotrrevival:faction_shapeless} recipes. */
public final class FactionShapelessRecipeBuilder {
    private final TableRef table;
    private final ItemStack result;
    private final NonNullList<Ingredient> ingredients = NonNullList.create();
    private String group = "";
    private CraftingBookCategory category = CraftingBookCategory.MISC;

    private FactionShapelessRecipeBuilder(TableRef table, ItemLike result, int count) {
        this.table = table;
        this.result = new ItemStack(result, count);
    }

    /** {@code table} is a block id or a {@code #} block tag. */
    public static FactionShapelessRecipeBuilder shapeless(TableRef table, ItemLike result, int count) {
        return new FactionShapelessRecipeBuilder(table, result, count);
    }

    public FactionShapelessRecipeBuilder requires(TagKey<Item> tag) {
        return requires(Ingredient.of(tag));
    }

    public FactionShapelessRecipeBuilder requires(ItemLike item) {
        return requires(Ingredient.of(item));
    }

    public FactionShapelessRecipeBuilder requires(Ingredient ingredient) {
        ingredients.add(ingredient);
        return this;
    }

    public FactionShapelessRecipeBuilder group(String group) {
        this.group = group;
        return this;
    }

    public FactionShapelessRecipeBuilder category(RecipeCategory category) {
        this.category = switch (category) {
            case BUILDING_BLOCKS -> CraftingBookCategory.BUILDING;
            case TOOLS, COMBAT -> CraftingBookCategory.EQUIPMENT;
            case REDSTONE -> CraftingBookCategory.REDSTONE;
            default -> CraftingBookCategory.MISC;
        };
        return this;
    }

    public void save(RecipeOutput out, ResourceLocation id) {
        out.accept(id, new FactionShapelessRecipe(table, group, category, result, ingredients), null);
    }
}
