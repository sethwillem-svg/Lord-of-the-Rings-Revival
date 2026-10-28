package io.github.sethwillemsvg.lotrrevival.datagen.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearCatalog;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearEntry;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearKind;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearRecipe;
import io.github.sethwillemsvg.lotrrevival.core.combat.RecipeKey;
import io.github.sethwillemsvg.lotrrevival.core.table.TableRef;
import io.github.sethwillemsvg.lotrrevival.datagen.FactionShapedRecipeBuilder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Crafting recipes for every {@link GearEntry}. Vanilla-table recipes become vanilla
 * {@code crafting_shaped}/{@code crafting_shapeless}.
 *
 * <p>Shaped recipes on a faction table or table group become {@code lotrrevival:faction_shaped}; shapeless
 * ones on such tables are skipped until a faction gear recipe needs them.
 */
final class CombatRecipes extends RecipeProvider {
    CombatRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        for (GearEntry entry : GearCatalog.entries()) {
            List<GearRecipe> recipes = entry.recipes();
            for (int i = 0; i < recipes.size(); i++) {
                GearRecipe recipe = recipes.get(i);
                String path = i == 0 ? entry.id() : entry.id() + "_" + (i + 1);
                if (!recipe.table().isVanilla()) {
                    if (recipe.isShaped()) {
                        saveFaction(out, entry, recipe, LOTRRevival.id(path));
                    }
                    continue;
                }
                save(out, entry, recipe, LOTRRevival.id(path));
            }
        }
    }

    /** Shaped recipe on a faction table or table group: {@code lotrrevival:faction_shaped}. */
    private static void saveFaction(RecipeOutput out, GearEntry entry, GearRecipe recipe, ResourceLocation id) {
        RecipeCategory category = entry.kind() == GearKind.TOOL ? RecipeCategory.TOOLS : RecipeCategory.COMBAT;
        FactionShapedRecipeBuilder builder = FactionShapedRecipeBuilder.shaped(
                new TableRef(recipe.table().id(), recipe.table().tag()), entry.item().get(), recipe.count());
        recipe.pattern().forEach(builder::pattern);
        for (Map.Entry<Character, RecipeKey> key : recipe.keys().entrySet()) {
            builder.define(key.getKey(), key.getValue().toIngredient());
        }
        builder.category(category).save(out, id);
    }

    private static void save(RecipeOutput out, GearEntry entry, GearRecipe recipe, ResourceLocation id) {
        Item result = entry.item().get();
        RecipeCategory category = entry.kind() == GearKind.TOOL ? RecipeCategory.TOOLS : RecipeCategory.COMBAT;
        if (recipe.isShaped()) {
            ShapedRecipeBuilder builder = ShapedRecipeBuilder.shaped(category, result, recipe.count());
            recipe.pattern().forEach(builder::pattern);
            for (Map.Entry<Character, RecipeKey> key : recipe.keys().entrySet()) {
                builder.define(key.getKey(), key.getValue().toIngredient());
            }
            RecipeKey unlock = recipe.keys().values().iterator().next();
            builder.unlockedBy("has_ingredient", criterion(unlock)).save(out, id);
        } else {
            ShapelessRecipeBuilder builder = ShapelessRecipeBuilder.shapeless(category, result, recipe.count());
            recipe.ingredients().forEach(key -> builder.requires(key.toIngredient()));
            builder.unlockedBy("has_ingredient", criterion(recipe.ingredients().get(0))).save(out, id);
        }
    }

    private static Criterion<InventoryChangeTrigger.TriggerInstance> criterion(RecipeKey key) {
        return key.tag() != null ? has(key.tag()) : has(key.item().get());
    }
}
