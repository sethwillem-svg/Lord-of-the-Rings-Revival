package io.github.sethwillemsvg.lotrrevival.core.brewing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.RecipeMatcher;

/**
 * A barrel recipe: exactly six ingredients matched one-to-one in any order (shapeless) and a drink
 * result. Water, servings and timing are fixed constants, not recipe fields.
 */
public record BrewingRecipe(List<Ingredient> ingredients, Item result) implements Recipe<BrewingInput> {
    public static final int INGREDIENT_COUNT = BrewingBarrelBlockEntity.INGREDIENT_SLOTS;

    public BrewingRecipe {
        ingredients = List.copyOf(ingredients);
    }

    private static DataResult<List<Ingredient>> validateIngredients(List<Ingredient> list) {
        return list.size() == INGREDIENT_COUNT
                ? DataResult.success(list)
                : DataResult.error(() -> "Brewing recipe needs exactly " + INGREDIENT_COUNT + " ingredients, got "
                        + list.size());
    }

    private static DataResult<Item> validateResult(Item item) {
        return item instanceof DrinkItem
                ? DataResult.success(item)
                : DataResult.error(() -> "Brewing recipe result is not a registered drink item: "
                        + BuiltInRegistries.ITEM.getKey(item));
    }

    @Override
    public boolean matches(BrewingInput input, Level level) {
        if (input.size() != INGREDIENT_COUNT) {
            return false;
        }
        List<ItemStack> stacks = new ArrayList<>(input.items());
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                return false;
            }
        }
        return RecipeMatcher.findMatches(stacks, ingredients) != null;
    }

    @Override
    public ItemStack assemble(BrewingInput input, HolderLookup.Provider registries) {
        return new ItemStack(result);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(result);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, ingredients.toArray(Ingredient[]::new));
    }

    /** Keeps these recipes out of the vanilla recipe book. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BrewingRecipes.SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return BrewingRecipes.TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<BrewingRecipe> {
        private static final MapCodec<BrewingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY
                                .listOf()
                                .flatXmap(BrewingRecipe::validateIngredients, BrewingRecipe::validateIngredients)
                                .fieldOf("ingredients")
                                .forGetter(BrewingRecipe::ingredients),
                        BuiltInRegistries.ITEM
                                .byNameCodec()
                                .flatXmap(BrewingRecipe::validateResult, BrewingRecipe::validateResult)
                                .fieldOf("result")
                                .forGetter(BrewingRecipe::result))
                .apply(instance, BrewingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, BrewingRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), BrewingRecipe::ingredients,
                ByteBufCodecs.registry(Registries.ITEM), BrewingRecipe::result,
                BrewingRecipe::new);

        @Override
        public MapCodec<BrewingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BrewingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
