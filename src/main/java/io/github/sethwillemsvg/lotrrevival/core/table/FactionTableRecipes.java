package io.github.sethwillemsvg.lotrrevival.core.table;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

/** The {@code lotrrevival:faction_crafting} recipe type, the two serializers, and recipe lookup for a table. */
public final class FactionTableRecipes {
    private FactionTableRecipes() {}

    public static final DeferredHolder<RecipeType<?>, RecipeType<CraftingRecipe>> TYPE =
            ModRegistries.RECIPE_TYPES.register(
                    "faction_crafting", () -> RecipeType.simple(LOTRRevival.id("faction_crafting")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FactionShapedRecipe>> SHAPED_SERIALIZER =
            ModRegistries.RECIPE_SERIALIZERS.register("faction_shaped", FactionShapedRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FactionShapelessRecipe>>
            SHAPELESS_SERIALIZER =
                    ModRegistries.RECIPE_SERIALIZERS.register("faction_shapeless", FactionShapelessRecipe.Serializer::new);

    static void init() {}

    /** First faction recipe (for this table) that matches, in the given order. */
    public static Optional<RecipeHolder<CraftingRecipe>> match(
            List<RecipeHolder<CraftingRecipe>> recipes, CraftingInput input, Level level, Block table) {
        for (RecipeHolder<CraftingRecipe> holder : recipes) {
            if (holder.value() instanceof FactionTableRecipe faction
                    && faction.table().matches(table)
                    && holder.value().matches(input, level)) {
                return Optional.of(holder);
            }
        }
        return Optional.empty();
    }

    /** Faction recipes of this table first, then any vanilla crafting recipe. */
    public static Optional<RecipeHolder<CraftingRecipe>> find(
            RecipeManager manager,
            CraftingInput input,
            Level level,
            Block table,
            @Nullable RecipeHolder<CraftingRecipe> last) {
        Optional<RecipeHolder<CraftingRecipe>> faction = match(manager.getAllRecipesFor(TYPE.get()), input, level, table);
        if (faction.isPresent()) {
            return faction;
        }
        return manager.getRecipeFor(RecipeType.CRAFTING, input, level, last);
    }
}
