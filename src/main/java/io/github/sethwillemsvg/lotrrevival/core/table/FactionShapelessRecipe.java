package io.github.sethwillemsvg.lotrrevival.core.table;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/** {@code lotrrevival:faction_shapeless}: a shapeless recipe that only works on the faction table(s) in {@code table}. */
public class FactionShapelessRecipe extends ShapelessRecipe implements FactionTableRecipe {
    private final TableRef table;
    private final ItemStack result;

    public FactionShapelessRecipe(
            TableRef table,
            String group,
            CraftingBookCategory category,
            ItemStack result,
            NonNullList<Ingredient> ingredients) {
        super(group, category, result, ingredients);
        this.table = table;
        this.result = result;
    }

    @Override
    public TableRef table() {
        return table;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FactionTableRecipes.SHAPELESS_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return FactionTableRecipes.TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<FactionShapelessRecipe> {
        private static final MapCodec<FactionShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        TableRef.CODEC.fieldOf("table").forGetter(r -> r.table),
                        Codec.STRING.optionalFieldOf("group", "").forGetter(ShapelessRecipe::getGroup),
                        CraftingBookCategory.CODEC
                                .fieldOf("category")
                                .orElse(CraftingBookCategory.MISC)
                                .forGetter(ShapelessRecipe::category),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
                        Ingredient.CODEC_NONEMPTY
                                .listOf()
                                .fieldOf("ingredients")
                                .flatXmap(
                                        list -> {
                                            Ingredient[] array = list.stream()
                                                    .filter(ingredient -> !ingredient.isEmpty())
                                                    .toArray(Ingredient[]::new);
                                            if (array.length == 0) {
                                                return DataResult.<NonNullList<Ingredient>>error(
                                                        () -> "No ingredients for faction_shapeless recipe");
                                            }
                                            return array.length > 9
                                                    ? DataResult.<NonNullList<Ingredient>>error(
                                                            () -> "Too many ingredients for faction_shapeless recipe")
                                                    : DataResult.success(NonNullList.of(Ingredient.EMPTY, array));
                                        },
                                        DataResult::success)
                                .forGetter(ShapelessRecipe::getIngredients))
                .apply(i, FactionShapelessRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, FactionShapelessRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        TableRef.STREAM_CODEC, r -> r.table,
                        ByteBufCodecs.STRING_UTF8, ShapelessRecipe::getGroup,
                        CraftingBookCategory.STREAM_CODEC, ShapelessRecipe::category,
                        ItemStack.STREAM_CODEC, r -> r.result,
                        Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.collection(NonNullList::createWithCapacity)),
                        ShapelessRecipe::getIngredients,
                        FactionShapelessRecipe::new);

        @Override
        public MapCodec<FactionShapelessRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FactionShapelessRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
