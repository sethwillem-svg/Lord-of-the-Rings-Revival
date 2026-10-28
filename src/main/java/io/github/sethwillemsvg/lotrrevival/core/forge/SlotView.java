package io.github.sethwillemsvg.lotrrevival.core.forge;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** A window onto a run of slots of another handler, with insert and extract switched on or off (for hoppers). */
final class SlotView implements IItemHandler {
    private final IItemHandler handler;
    private final int start;
    private final int count;
    private final boolean canInsert;
    private final boolean canExtract;

    SlotView(IItemHandler handler, int start, int count, boolean canInsert, boolean canExtract) {
        this.handler = handler;
        this.start = start;
        this.count = count;
        this.canInsert = canInsert;
        this.canExtract = canExtract;
    }

    @Override
    public int getSlots() {
        return count;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return handler.getStackInSlot(start + slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return canInsert ? handler.insertItem(start + slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return canExtract ? handler.extractItem(start + slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return handler.getSlotLimit(start + slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return canInsert && handler.isItemValid(start + slot, stack);
    }
}
