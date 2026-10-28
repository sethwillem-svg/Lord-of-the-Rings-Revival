package io.github.sethwillemsvg.lotrrevival.core.forge;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity shared by every forge variant. Thirteen slots in one handler: 4 inputs (0-3), the 4 alloy slots above
 * them (4-7), 4 outputs (8-11) and 1 fuel slot (12). Lane {@code i} uses input {@code i}, alloy {@code i} and output
 * {@code 8 + i}. Lanes run in parallel; one burning fuel item keeps every lane going, and burns only while at least
 * one lane is making progress.
 */
public class ForgeBlockEntity extends BlockEntity implements MenuProvider {
    public static final int LANES = 4;
    public static final int INPUT_START = 0;
    public static final int ALLOY_START = LANES;
    public static final int OUTPUT_START = 2 * LANES;
    public static final int FUEL_SLOT = 3 * LANES;
    public static final int SLOTS = FUEL_SLOT + 1;

    /** Container-data layout shared with the menu: burn, burn total, then progress and total per lane. */
    public static final int DATA_BURN = 0;
    public static final int DATA_BURN_TOTAL = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_TOTAL = DATA_PROGRESS + LANES;
    public static final int DATA_COUNT = DATA_TOTAL + LANES;

    /** Slot rules, usable on the client with a dummy handler that only needs the level and the forge block. */
    public static final class ForgeInventory extends ItemStackHandler {
        private final Runnable onChange;
        private final Supplier<@Nullable Level> level;
        private final Supplier<Block> forge;

        public ForgeInventory(Runnable onChange, Supplier<@Nullable Level> level, Supplier<Block> forge) {
            super(SLOTS);
            this.onChange = onChange;
            this.level = level;
            this.forge = forge;
        }

        @Override
        protected void onContentsChanged(int slot) {
            onChange.run();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot >= OUTPUT_START && slot < FUEL_SLOT) {
                return false;
            }
            if (slot == FUEL_SLOT) {
                return ForgeRules.isFuel(stack);
            }
            Level current = level.get();
            if (current == null) {
                return !ForgeRules.isFood(stack);
            }
            return slot < ALLOY_START
                    ? ForgeRules.acceptsInput(current, forge.get(), stack)
                    : ForgeRules.acceptsAlloy(current, forge.get(), stack);
        }
    }

    private final ForgeInventory inventory =
            new ForgeInventory(this::setChanged, () -> this.level, () -> this.getBlockState().getBlock());
    private int burn;
    private int burnTotal;
    private final int[] progress = new int[LANES];
    private final int[] total = new int[LANES];
    /** Last (input, alloy) item pair seen per lane, so swapping items restarts the lane. */
    private final ItemStack[] lastInput = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final ItemStack[] lastAlloy = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private float storedExperience;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            if (index == DATA_BURN) {
                return burn;
            }
            if (index == DATA_BURN_TOTAL) {
                return burnTotal;
            }
            if (index >= DATA_PROGRESS && index < DATA_TOTAL) {
                return progress[index - DATA_PROGRESS];
            }
            if (index >= DATA_TOTAL && index < DATA_COUNT) {
                return total[index - DATA_TOTAL];
            }
            return 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeBlocks.BLOCK_ENTITY.get(), pos, state);
    }

    public ForgeInventory inventory() {
        return inventory;
    }

    public ContainerData dataAccess() {
        return dataAccess;
    }

    public boolean isBurning() {
        return burn > 0;
    }

    // ---- ticking ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, ForgeBlockEntity forge) {
        forge.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        boolean wasBurning = burn > 0;
        if (wasBurning) {
            burn--;
        }

        Block block = getBlockState().getBlock();
        ForgeRules.Job[] jobs = new ForgeRules.Job[LANES];
        boolean anyWork = false;
        for (int lane = 0; lane < LANES; lane++) {
            jobs[lane] = jobFor(level, block, lane);
            anyWork |= jobs[lane] != null;
        }

        if (burn <= 0 && anyWork) {
            ItemStack fuel = inventory.getStackInSlot(FUEL_SLOT);
            int time = fuel.isEmpty() ? 0 : fuel.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING);
            if (time > 0) {
                burn = time;
                burnTotal = time;
                ItemStack remainder = fuel.getCraftingRemainingItem();
                ItemStack rest = fuel.copy();
                rest.shrink(1);
                inventory.setStackInSlot(FUEL_SLOT, rest.isEmpty() ? remainder : rest);
            }
        }

        boolean changed = false;
        for (int lane = 0; lane < LANES; lane++) {
            ForgeRules.Job job = jobs[lane];
            if (job != null && burn > 0) {
                total[lane] = job.cookingTime();
                progress[lane]++;
                changed = true;
                if (progress[lane] >= job.cookingTime()) {
                    finish(lane, job);
                    progress[lane] = 0;
                }
            } else if (progress[lane] > 0) {
                progress[lane] = Mth.clamp(progress[lane] - 2, 0, total[lane]);
                changed = true;
            }
        }

        boolean burning = burn > 0;
        BlockState state = getBlockState();
        if (state.hasProperty(ForgeBlock.LIT) && state.getValue(ForgeBlock.LIT) != burning) {
            level.setBlock(pos, state.setValue(ForgeBlock.LIT, burning), Block.UPDATE_ALL);
            changed = true;
        }
        if (burning != wasBurning) {
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    /** The lane's job if it can run right now (recipe found and the output has room), else null. */
    @Nullable
    private ForgeRules.Job jobFor(Level level, Block block, int lane) {
        ItemStack input = inventory.getStackInSlot(INPUT_START + lane);
        ItemStack alloy = inventory.getStackInSlot(ALLOY_START + lane);
        if (!ItemStack.isSameItem(input, lastInput[lane]) || !ItemStack.isSameItem(alloy, lastAlloy[lane])) {
            progress[lane] = 0;
            lastInput[lane] = input.copyWithCount(1);
            lastAlloy[lane] = alloy.copyWithCount(1);
        }
        if (input.isEmpty()) {
            return null;
        }
        Optional<ForgeRules.Job> job = ForgeRules.find(level, block, input, alloy);
        if (job.isEmpty() || !fitsOutput(lane, job.get().result())) {
            return null;
        }
        return job.get();
    }

    private boolean fitsOutput(int lane, ItemStack result) {
        ItemStack out = inventory.getStackInSlot(OUTPUT_START + lane);
        if (result.isEmpty()) {
            return false;
        }
        return out.isEmpty()
                || (ItemStack.isSameItemSameComponents(out, result)
                        && out.getCount() + result.getCount() <= out.getMaxStackSize());
    }

    private void finish(int lane, ForgeRules.Job job) {
        int outSlot = OUTPUT_START + lane;
        ItemStack out = inventory.getStackInSlot(outSlot);
        if (out.isEmpty()) {
            inventory.setStackInSlot(outSlot, job.result().copy());
        } else {
            ItemStack grown = out.copy();
            grown.grow(job.result().getCount());
            inventory.setStackInSlot(outSlot, grown);
        }
        shrinkSlot(INPUT_START + lane);
        if (job.usesAlloy()) {
            shrinkSlot(ALLOY_START + lane);
        }
        storedExperience += job.experience();
    }

    private void shrinkSlot(int slot) {
        ItemStack stack = inventory.getStackInSlot(slot).copy();
        stack.shrink(1);
        inventory.setStackInSlot(slot, stack);
    }

    /** Pays out the experience earned since the last time an output was taken (fractions roll like a furnace). */
    public void awardExperience(ServerPlayer player) {
        if (storedExperience <= 0.0F) {
            return;
        }
        int whole = Mth.floor(storedExperience);
        float fraction = Mth.frac(storedExperience);
        if (fraction != 0.0F && Math.random() < fraction) {
            whole++;
        }
        storedExperience = 0.0F;
        setChanged();
        ExperienceOrb.award(player.serverLevel(), player.position(), whole);
    }

    /** Drops every slot's contents. */
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < SLOTS; i++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(i));
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    // ---- item handler views (hoppers and other automation) ----

    /** Top: inputs, insert only. Sides: fuel, insert only. Bottom: outputs, extract only. No side: everything. */
    public IItemHandler handlerFor(@Nullable Direction side) {
        if (side == null) {
            return inventory;
        }
        return switch (side) {
            case UP -> new SlotView(inventory, INPUT_START, LANES, true, false);
            case DOWN -> new SlotView(inventory, OUTPUT_START, LANES, false, true);
            default -> new SlotView(inventory, FUEL_SLOT, 1, true, false);
        };
    }

    // ---- persistence ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("Burn", burn);
        tag.putInt("BurnTotal", burnTotal);
        tag.putIntArray("Progress", progress);
        tag.putIntArray("Total", total);
        tag.putFloat("Experience", storedExperience);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        burn = Math.max(0, tag.getInt("Burn"));
        burnTotal = Math.max(0, tag.getInt("BurnTotal"));
        copyInts(tag.getIntArray("Progress"), progress);
        copyInts(tag.getIntArray("Total"), total);
        storedExperience = Math.max(0.0F, tag.getFloat("Experience"));
    }

    private static void copyInts(int[] from, int[] to) {
        for (int i = 0; i < to.length; i++) {
            to[i] = i < from.length ? Math.max(0, from[i]) : 0;
        }
    }

    // ---- naming and menu ----

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ForgeMenu(containerId, playerInventory, this);
    }
}
