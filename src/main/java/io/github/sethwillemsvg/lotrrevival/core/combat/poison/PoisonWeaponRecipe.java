package io.github.sethwillemsvg.lotrrevival.core.combat.poison;

import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * {@code lotrrevival:poison_weapon}: exactly one unpoisoned {@code #lotrrevival:poisonable} item plus
 * exactly one bottle of poison, nothing else, anywhere in the grid. The result is a copy of the
 * weapon (durability, name, enchantments and every other component) with the poisoned mark; the
 * bottle comes back as an empty glass bottle.
 */
public final class PoisonWeaponRecipe extends CustomRecipe {
    public PoisonWeaponRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Slots slots = find(input);
        if (slots == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = input.getItem(slots.weapon).copyWithCount(1);
        result.set(PoisonItems.POISONED.get(), Unit.INSTANCE);
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        Slots slots = find(input);
        if (slots != null) {
            remaining.set(slots.bottle, new ItemStack(Items.GLASS_BOTTLE));
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return PoisonItems.POISON_WEAPON_SERIALIZER.get();
    }

    private record Slots(int weapon, int bottle) {}

    private static Slots find(CraftingInput input) {
        int weapon = -1;
        int bottle = -1;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(PoisonItems.BOTTLE_OF_POISON.get())) {
                if (bottle >= 0) {
                    return null;
                }
                bottle = i;
            } else if (stack.is(ModTags.Items.POISONABLE) && !PoisonItems.isPoisoned(stack)) {
                if (weapon >= 0) {
                    return null;
                }
                weapon = i;
            } else {
                return null;
            }
        }
        return weapon >= 0 && bottle >= 0 ? new Slots(weapon, bottle) : null;
    }
}
