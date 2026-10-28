package io.github.sethwillemsvg.lotrrevival.core.table;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/** {@code lotrrevival:faction_shaped}: a shaped recipe that only works on the faction table(s) in {@code table}. */
public class FactionShapedRecipe extends ShapedRecipe implements FactionTableRecipe {
    private final TableRef table;
    private final ShapedRecipePattern pattern;
    private final ItemStack result;

    public FactionShapedRecipe(
            TableRef table,
            String group,
            CraftingBookCategory category,
            ShapedRecipePattern pattern,
            ItemStack result,
            boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.table = table;
        this.pattern = pattern;
        this.result = result;
    }

    @Override
    public TableRef table() {
        return table;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FactionTableRecipes.SHAPED_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return FactionTableRecipes.TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<FactionShapedRecipe> {
        private static final MapCodec<FactionShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        TableRef.CODEC.fieldOf("table").forGetter(r -> r.table),
                        Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
                        CraftingBookCategory.CODEC
                                .fieldOf("category")
                                .orElse(CraftingBookCategory.MISC)
                                .forGetter(ShapedRecipe::category),
                        ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
                        Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification))
                .apply(i, FactionShapedRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, FactionShapedRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        TableRef.STREAM_CODEC, r -> r.table,
                        ByteBufCodecs.STRING_UTF8, ShapedRecipe::getGroup,
                        CraftingBookCategory.STREAM_CODEC, ShapedRecipe::category,
                        ShapedRecipePattern.STREAM_CODEC, r -> r.pattern,
                        ItemStack.STREAM_CODEC, r -> r.result,
                        ByteBufCodecs.BOOL, ShapedRecipe::showNotification,
                        FactionShapedRecipe::new);

        @Override
        public MapCodec<FactionShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FactionShapedRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
