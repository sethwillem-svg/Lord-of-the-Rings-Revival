package io.github.sethwillemsvg.lotrrevival.core.brewing;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Recipe type and serializer for {@code lotrrevival:brewing}. */
public final class BrewingRecipes {
    private BrewingRecipes() {}

    public static final DeferredHolder<RecipeType<?>, RecipeType<BrewingRecipe>> TYPE =
            ModRegistries.RECIPE_TYPES.register("brewing", () -> RecipeType.simple(LOTRRevival.id("brewing")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BrewingRecipe>> SERIALIZER =
            ModRegistries.RECIPE_SERIALIZERS.register("brewing", BrewingRecipe.Serializer::new);

    static void init() {}
}
