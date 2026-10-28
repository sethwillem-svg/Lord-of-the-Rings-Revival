package io.github.sethwillemsvg.lotrrevival.core.forge;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Recipe type and serializer for {@code lotrrevival:forging}. */
public final class ForgingRecipes {
    private ForgingRecipes() {}

    public static final DeferredHolder<RecipeType<?>, RecipeType<ForgingRecipe>> TYPE =
            ModRegistries.RECIPE_TYPES.register("forging", () -> RecipeType.simple(LOTRRevival.id("forging")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ForgingRecipe>> SERIALIZER =
            ModRegistries.RECIPE_SERIALIZERS.register("forging", ForgingRecipe.Serializer::new);

    static void init() {}
}
