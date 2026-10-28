package io.github.sethwillemsvg.lotrrevival.core.forge;

import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** What a forge accepts and what it makes: forging recipes first, then vanilla smelting of non-edible items. */
public final class ForgeRules {
    /** Ticks per item for vanilla smelting recipes run in a forge (ours). */
    public static final int SMELTING_TIME = 200;

    private ForgeRules() {}

    /** One lane's job: the result, the experience, the time, and whether the alloy slot is consumed too. */
    public record Job(ItemStack result, float experience, int cookingTime, boolean usesAlloy) {}

    public static boolean isFood(ItemStack stack) {
        return stack.has(DataComponents.FOOD);
    }

    /** What the lane would make from these stacks in this forge, if anything. */
    public static Optional<Job> find(Level level, Block forge, ItemStack input, ItemStack alloy) {
        if (input.isEmpty() || isFood(input)) {
            return Optional.empty();
        }
        Optional<RecipeHolder<ForgingRecipe>> forging = level.getRecipeManager()
                .getRecipeFor(ForgingRecipes.TYPE.get(), new ForgingInput(input, alloy, forge), level);
        if (forging.isPresent()) {
            ForgingRecipe recipe = forging.get().value();
            return Optional.of(new Job(
                    recipe.result().copy(), recipe.experience(), recipe.cookingTime(), recipe.alloy().isPresent()));
        }
        SingleRecipeInput single = new SingleRecipeInput(input);
        Optional<RecipeHolder<SmeltingRecipe>> smelting =
                level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, single, level);
        return smelting.map(holder -> new Job(
                holder.value().assemble(single, level.registryAccess()),
                holder.value().getExperience(),
                SMELTING_TIME,
                false));
    }

    /** Input slots take anything a recipe here could use in either slot order, and any non-edible smelting input. */
    public static boolean acceptsInput(Level level, Block forge, ItemStack stack) {
        if (stack.isEmpty() || isFood(stack)) {
            return false;
        }
        for (RecipeHolder<ForgingRecipe> holder :
                level.getRecipeManager().getAllRecipesFor(ForgingRecipes.TYPE.get())) {
            if (holder.value().usesInInputSlot(forge, stack)) {
                return true;
            }
        }
        return level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level)
                .isPresent();
    }

    /** Alloy slots only take items that partner in some alloy recipe of this forge. */
    public static boolean acceptsAlloy(Level level, Block forge, ItemStack stack) {
        if (stack.isEmpty() || isFood(stack)) {
            return false;
        }
        for (RecipeHolder<ForgingRecipe> holder :
                level.getRecipeManager().getAllRecipesFor(ForgingRecipes.TYPE.get())) {
            if (holder.value().usesInAlloySlot(forge, stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getBurnTime(RecipeType.SMELTING) > 0;
    }
}
