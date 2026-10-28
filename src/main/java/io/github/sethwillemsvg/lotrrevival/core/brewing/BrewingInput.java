package io.github.sethwillemsvg.lotrrevival.core.brewing;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** The six ingredient stacks of a barrel, in slot order. */
public record BrewingInput(List<ItemStack> items) implements RecipeInput {
    public BrewingInput {
        items = List.copyOf(items);
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public int size() {
        return items.size();
    }
}
