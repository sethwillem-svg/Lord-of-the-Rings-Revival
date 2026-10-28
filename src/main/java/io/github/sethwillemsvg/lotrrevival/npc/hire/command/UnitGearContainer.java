package io.github.sethwillemsvg.lotrrevival.npc.hire.command;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Server-side view of the unit's player-given gear as the five gear slots of the command menu. */
final class UnitGearContainer implements Container {
    private final NpcEntity unit;

    UnitGearContainer(NpcEntity unit) {
        this.unit = unit;
    }

    @Override
    public int getContainerSize() {
        return UnitGear.SLOTS.length;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return UnitGear.given(unit, UnitGear.SLOTS[slot]);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return UnitGear.take(unit, UnitGear.SLOTS[slot]);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return UnitGear.take(unit, UnitGear.SLOTS[slot]);
    }

    /** An empty stack means the item has already left the slot (moved elsewhere): restore the unit's own piece. */
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            UnitGear.take(unit, UnitGear.SLOTS[slot]);
        } else {
            UnitGear.give(unit, UnitGear.SLOTS[slot], stack);
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {}
}
