package io.github.sethwillemsvg.lotrrevival.core.forge;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.Block;

/** One forge lane as a recipe input: the input slot, the alloy slot above it, and the forge block doing the work. */
public record ForgingInput(ItemStack input, ItemStack alloy, Block forge) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return index == 0 ? input : alloy;
    }

    @Override
    public int size() {
        return 2;
    }
}
