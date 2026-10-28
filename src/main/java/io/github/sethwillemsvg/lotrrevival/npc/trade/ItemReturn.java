package io.github.sethwillemsvg.lotrrevival.npc.trade;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Hands items to a player: into the main inventory where they fit, the rest dropped at the player's feet. Does not use
 * {@link Inventory#add(ItemStack)}, which voids what does not fit for creative players.
 */
public final class ItemReturn {
    private ItemReturn() {}

    /** Gives the stack (consumed) to the player, splitting it across stacks as needed. */
    public static void give(ServerPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < inventory.items.size() && !stack.isEmpty(); i++) {
                ItemStack slot = inventory.items.get(i);
                if (pass == 0) {
                    if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                        int moved = Math.min(slot.getMaxStackSize() - slot.getCount(), stack.getCount());
                        if (moved > 0) {
                            slot.grow(moved);
                            stack.shrink(moved);
                        }
                    }
                } else if (slot.isEmpty()) {
                    inventory.items.set(i, stack.split(Math.min(stack.getMaxStackSize(), stack.getCount())));
                }
            }
        }
        inventory.setChanged();
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(Math.min(stack.getMaxStackSize(), stack.getCount()));
            ItemEntity entity = new ItemEntity(
                    (ServerLevel) player.level(), player.getX(), player.getY(), player.getZ(), part, 0, 0, 0);
            entity.setDefaultPickUpDelay();
            player.level().addFreshEntity(entity);
        }
    }
}
