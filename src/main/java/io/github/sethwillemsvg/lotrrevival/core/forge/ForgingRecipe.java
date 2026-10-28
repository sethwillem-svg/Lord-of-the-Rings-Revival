package io.github.sethwillemsvg.lotrrevival.core.forge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.table.TableRef;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * {@code lotrrevival:forging}: one forge lane turns an input (plus an optional alloy partner) into a result.
 *
 * <p>{@code forges} is a forge block id or a {@code #} block tag, like a faction recipe's {@code table}. With an
 * {@code alloy}, the two ingredients may sit in either slot; without one, the alloy slot is ignored.
 */
public record ForgingRecipe(
        TableRef forges, Ingredient input, Optional<Ingredient> alloy, ItemStack result, float experience, int cookingTime)
        implements Recipe<ForgingInput> {
    public static final int DEFAULT_COOKING_TIME = 200;

    @Override
    public boolean matches(ForgingInput in, Level level) {
        if (!forges.matches(in.forge())) {
            return false;
        }
        if (alloy.isEmpty()) {
            return input.test(in.input());
        }
        Ingredient second = alloy.get();
        return (input.test(in.input()) && second.test(in.alloy()))
                || (input.test(in.alloy()) && second.test(in.input()));
    }

    /** True if the stack could be the input of this recipe in the given forge (the alloy partner counts, either order). */
    public boolean usesInInputSlot(Block forge, ItemStack stack) {
        return forges.matches(forge) && (input.test(stack) || alloy.map(a -> a.test(stack)).orElse(false));
    }

    /** True if the stack could sit in the alloy slot for this recipe in the given forge. */
    public boolean usesInAlloySlot(Block forge, ItemStack stack) {
        return alloy.isPresent() && usesInInputSlot(forge, stack);
    }

    @Override
    public ItemStack assemble(ForgingInput in, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(input);
        alloy.ifPresent(list::add);
        return list;
    }

    /** Keeps these recipes out of the vanilla recipe book. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ForgingRecipes.SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ForgingRecipes.TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<ForgingRecipe> {
        private static final MapCodec<ForgingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        TableRef.CODEC.fieldOf("forges").forGetter(ForgingRecipe::forges),
                        Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(ForgingRecipe::input),
                        Ingredient.CODEC_NONEMPTY.optionalFieldOf("alloy").forGetter(ForgingRecipe::alloy),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ForgingRecipe::result),
                        Codec.FLOAT.optionalFieldOf("experience", 0.0F).forGetter(ForgingRecipe::experience),
                        Codec.INT.optionalFieldOf("cooking_time", DEFAULT_COOKING_TIME)
                                .forGetter(ForgingRecipe::cookingTime))
                .apply(i, ForgingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, ForgingRecipe> STREAM_CODEC = StreamCodec.composite(
                TableRef.STREAM_CODEC, ForgingRecipe::forges,
                Ingredient.CONTENTS_STREAM_CODEC, ForgingRecipe::input,
                ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC), ForgingRecipe::alloy,
                ItemStack.STREAM_CODEC, ForgingRecipe::result,
                ByteBufCodecs.FLOAT, ForgingRecipe::experience,
                ByteBufCodecs.VAR_INT, ForgingRecipe::cookingTime,
                ForgingRecipe::new);

        @Override
        public MapCodec<ForgingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ForgingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
