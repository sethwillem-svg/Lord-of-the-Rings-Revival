package io.github.sethwillemsvg.lotrrevival.core.combat;

import java.util.function.Supplier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

/**
 * One crafting ingredient of a gear recipe: an item tag or a (lazily resolved) item. Lazy so gear can
 * name items that are registered later.
 */
public record RecipeKey(@Nullable TagKey<Item> tag, @Nullable Supplier<? extends ItemLike> item) {
    public static RecipeKey of(TagKey<Item> tag) {
        return new RecipeKey(tag, null);
    }

    public static RecipeKey of(ItemLike item) {
        return new RecipeKey(null, () -> item);
    }

    /** For items from a {@code DeferredItem} that is not resolvable yet. */
    public static RecipeKey ofLazy(Supplier<? extends ItemLike> item) {
        return new RecipeKey(null, item);
    }

    public Ingredient toIngredient() {
        return tag != null ? Ingredient.of(tag) : Ingredient.of(item.get());
    }
}
