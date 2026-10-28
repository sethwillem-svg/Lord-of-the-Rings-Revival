package io.github.sethwillemsvg.lotrrevival.core.brewing;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity of the brewing barrel. Holds 9 slots (0-5 ingredients, 6-8 water) in an item handler and
 * runs the Empty / Brewing / Full state machine. Deliberately not a vanilla {@code Container} and exposes
 * no item-handler capability in the pilot.
 */
public class BrewingBarrelBlockEntity extends BlockEntity implements MenuProvider, Nameable {
    public static final int INGREDIENT_SLOTS = 6;
    public static final int WATER_SLOTS = 3;
    public static final int SLOTS = INGREDIENT_SLOTS + WATER_SLOTS;
    public static final int SERVINGS = 16;
    public static final int TIER_TICKS = 6000;
    public static final int TOTAL_TICKS = TIER_TICKS * (DrinkTier.MAX_INDEX + 1);

    /** The 9-slot handler. Items go in only while the barrel is Empty; water slots take one water bucket. */
    public static final class BarrelInventory extends ItemStackHandler {
        private final Runnable onChange;
        private final BooleanSupplier open;

        public BarrelInventory(Runnable onChange, BooleanSupplier open) {
            super(SLOTS);
            this.onChange = onChange;
            this.open = open;
        }

        @Override
        protected void onContentsChanged(int slot) {
            onChange.run();
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= INGREDIENT_SLOTS ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return open.getAsBoolean() && (slot < INGREDIENT_SLOTS || stack.is(Items.WATER_BUCKET));
        }
    }

    private final BarrelInventory inventory = new BarrelInventory(this::setChanged, () -> this.state == BarrelState.EMPTY);
    private BarrelState state = BarrelState.EMPTY;
    @Nullable private Item drink;
    private int tier;
    private int servings;
    private int elapsed;
    @Nullable private Component customName;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> state.ordinal();
                case 1 -> elapsed;
                case 2 -> displayTier().index();
                case 3 -> servings;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 4;
        }
    };

    public BrewingBarrelBlockEntity(BlockPos pos, BlockState blockState) {
        super(BrewingBlocks.BARREL_BE.get(), pos, blockState);
    }

    // ---- accessors ----

    public ItemStackHandler inventory() {
        return inventory;
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    public BarrelState state() {
        return state;
    }

    @Nullable
    public Item drink() {
        return drink;
    }

    /** Stored tier (Full) or the tier in progress (Brewing). */
    public DrinkTier displayTier() {
        if (state == BarrelState.BREWING) {
            return DrinkTier.byIndex(Math.min(DrinkTier.MAX_INDEX, elapsed / TIER_TICKS));
        }
        return DrinkTier.byIndex(tier);
    }

    public int servings() {
        return servings;
    }

    public int elapsed() {
        return elapsed;
    }

    // ---- brewing logic ----

    public BrewingInput ingredientInput() {
        List<ItemStack> stacks = new ArrayList<>(INGREDIENT_SLOTS);
        for (int i = 0; i < INGREDIENT_SLOTS; i++) {
            stacks.add(inventory.getStackInSlot(i));
        }
        return new BrewingInput(stacks);
    }

    public Optional<RecipeHolder<BrewingRecipe>> findRecipe() {
        if (level == null) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(BrewingRecipes.TYPE.get(), ingredientInput(), level);
    }

    public boolean hasWater() {
        for (int i = INGREDIENT_SLOTS; i < SLOTS; i++) {
            if (!inventory.getStackInSlot(i).is(Items.WATER_BUCKET)) {
                return false;
            }
        }
        return true;
    }

    /** What the display-only result slot shows. */
    public ItemStack previewResult() {
        if (state == BarrelState.EMPTY) {
            return findRecipe().map(holder -> new ItemStack(holder.value().result())).orElse(ItemStack.EMPTY);
        }
        if (drink == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(drink);
        BrewingComponents.setTier(stack, displayTier());
        return stack;
    }

    /**
     * The Brew button. Empty with a matching recipe and three water buckets: start. Brewing with at least one
     * completed tier: stop early at the last completed tier. Anything else does nothing.
     *
     * @return true if the state changed
     */
    public boolean pressBrew() {
        if (level == null || level.isClientSide) {
            return false;
        }
        switch (state) {
            case EMPTY -> {
                if (!hasWater()) {
                    return false;
                }
                Optional<RecipeHolder<BrewingRecipe>> recipe = findRecipe();
                if (recipe.isEmpty()) {
                    return false;
                }
                for (int i = 0; i < INGREDIENT_SLOTS; i++) {
                    ItemStack rest = inventory.getStackInSlot(i).copy();
                    rest.shrink(1);
                    inventory.setStackInSlot(i, rest);
                }
                for (int i = INGREDIENT_SLOTS; i < SLOTS; i++) {
                    inventory.setStackInSlot(i, new ItemStack(Items.BUCKET));
                }
                state = BarrelState.BREWING;
                drink = recipe.get().value().result();
                tier = 0;
                servings = SERVINGS;
                elapsed = 0;
                setChanged();
                return true;
            }
            case BREWING -> {
                int inProgress = elapsed / TIER_TICKS;
                if (inProgress < 1) {
                    return false;
                }
                finishBrewing(inProgress - 1);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void finishBrewing(int finalTier) {
        state = BarrelState.FULL;
        tier = finalTier;
        servings = SERVINGS;
        elapsed = 0;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState blockState, BrewingBarrelBlockEntity barrel) {
        if (barrel.state != BarrelState.BREWING) {
            return;
        }
        barrel.elapsed++;
        if (barrel.elapsed >= TOTAL_TICKS) {
            barrel.finishBrewing(DrinkTier.MAX_INDEX);
        } else {
            barrel.setChanged();
        }
    }

    public boolean canTap() {
        return state == BarrelState.FULL && servings > 0 && drink != null;
    }

    /** Takes one serving, returning the filled drink (empty if there is none to draw). */
    public ItemStack drawServing() {
        if (!canTap() || drink == null) {
            return ItemStack.EMPTY;
        }
        ItemStack filled = new ItemStack(drink);
        BrewingComponents.setTier(filled, DrinkTier.byIndex(tier));
        servings--;
        if (servings <= 0) {
            resetBrew();
        }
        setChanged();
        return filled;
    }

    private void resetBrew() {
        state = BarrelState.EMPTY;
        drink = null;
        tier = 0;
        servings = 0;
        elapsed = 0;
    }

    /** Drops every slot's contents (leftover ingredients and buckets). */
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < SLOTS; i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(i));
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    // ---- brew <-> component ----

    /** The brew as a component value, or null when the barrel is Empty. */
    @Nullable
    public BarrelBrew brew() {
        if (state == BarrelState.EMPTY || drink == null) {
            return null;
        }
        return new BarrelBrew(state, BuiltInRegistries.ITEM.getKey(drink), tier, servings, elapsed);
    }

    /** Restores a brew; an unknown drink id leaves the barrel Empty and logs one warning. */
    public void applyBrew(BarrelBrew brew) {
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(brew.drink());
        if (item.isEmpty() || !(item.get() instanceof DrinkItem) || brew.state() == BarrelState.EMPTY) {
            LOTRRevival.LOGGER.warn("Brewing barrel at {} has unknown drink '{}'; resetting to Empty", worldPosition,
                    brew.drink());
            resetBrew();
            return;
        }
        state = brew.state();
        drink = item.get();
        tier = brew.tier();
        servings = brew.servings();
        elapsed = brew.elapsed();
        setChanged();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        BarrelBrew brew = brew();
        if (brew != null) {
            builder.set(BrewingComponents.BARREL_BREW.get(), brew);
            builder.set(DataComponents.MAX_STACK_SIZE, 1);
        }
        if (customName != null) {
            builder.set(DataComponents.CUSTOM_NAME, customName);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        customName = input.get(DataComponents.CUSTOM_NAME);
        BarrelBrew brew = input.get(BrewingComponents.BARREL_BREW.get());
        if (brew != null) {
            applyBrew(brew);
        }
    }

    // ---- persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putString("State", state.getSerializedName());
        if (drink != null) {
            tag.putString("Drink", BuiltInRegistries.ITEM.getKey(drink).toString());
        }
        tag.putInt("Tier", tier);
        tag.putInt("Servings", servings);
        tag.putInt("Elapsed", elapsed);
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        customName = tag.contains("CustomName")
                ? Component.Serializer.fromJson(tag.getString("CustomName"), registries)
                : null;
        resetBrew();
        BarrelState saved = BarrelState.EMPTY;
        for (BarrelState candidate : BarrelState.values()) {
            if (candidate.getSerializedName().equals(tag.getString("State"))) {
                saved = candidate;
            }
        }
        if (saved == BarrelState.EMPTY) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Drink"));
        Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty() || !(item.get() instanceof DrinkItem)) {
            LOTRRevival.LOGGER.warn("Brewing barrel at {} has unknown drink '{}'; resetting to Empty", worldPosition,
                    tag.getString("Drink"));
            return;
        }
        state = saved;
        drink = item.get();
        tier = Math.max(DrinkTier.MIN_INDEX, Math.min(DrinkTier.MAX_INDEX, tag.getInt("Tier")));
        servings = Math.max(0, Math.min(SERVINGS, tag.getInt("Servings")));
        elapsed = Math.max(0, Math.min(TOTAL_TICKS, tag.getInt("Elapsed")));
    }

    // ---- naming and menu ----

    @Override
    public Component getName() {
        return Component.translatable("container.lotrrevival.brewing_barrel");
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return customName;
    }

    @Override
    public Component getDisplayName() {
        return customName != null ? customName : getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BrewingBarrelMenu(containerId, playerInventory, this);
    }
}
