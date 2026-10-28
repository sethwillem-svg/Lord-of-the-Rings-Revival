package io.github.sethwillemsvg.lotrrevival.core.forge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Menu of a forge: four lanes (alloy slot above input slot above output slot), one fuel slot and the player
 * inventory. Burn and progress values travel as container data.
 */
public class ForgeMenu extends AbstractContainerMenu {
    public static final int PLAYER_START = ForgeBlockEntity.SLOTS;
    public static final int PLAYER_END = PLAYER_START + 36;

    // Layout shared with the screen (relative to the top-left of the 176x166 background).
    public static final int LANE_X = 44;
    public static final int LANE_STEP = 22;
    public static final int ALLOY_Y = 16;
    public static final int INPUT_Y = 36;
    public static final int PROGRESS_Y = 55;
    public static final int OUTPUT_Y = 63;
    public static final int FUEL_X = 14;
    public static final int FUEL_Y = 40;
    public static final int FLAME_X = 15;
    public static final int FLAME_Y = 22;

    private static final double REACH_SQR = 8.0 * 8.0;

    @Nullable private final ForgeBlockEntity forge;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Client constructor, called with the extra data written by the server (the forge position). */
    public ForgeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos());
    }

    private ForgeMenu(int containerId, Inventory inventory, net.minecraft.core.BlockPos pos) {
        this(
                containerId,
                inventory,
                new ForgeBlockEntity.ForgeInventory(
                        () -> {},
                        inventory.player::level,
                        () -> inventory.player.level().getBlockState(pos).getBlock()),
                new SimpleContainerData(ForgeBlockEntity.DATA_COUNT),
                ContainerLevelAccess.create(inventory.player.level(), pos),
                null);
    }

    /** Server constructor. */
    public ForgeMenu(int containerId, Inventory inventory, ForgeBlockEntity forge) {
        this(
                containerId,
                inventory,
                forge.inventory(),
                forge.dataAccess(),
                ContainerLevelAccess.create(forge.getLevel(), forge.getBlockPos()),
                forge);
    }

    private ForgeMenu(
            int containerId,
            Inventory inventory,
            IItemHandlerModifiable handler,
            ContainerData data,
            ContainerLevelAccess access,
            @Nullable ForgeBlockEntity forge) {
        super(ForgeBlocks.MENU.get(), containerId);
        this.forge = forge;
        this.access = access;
        this.data = data;

        // Slot order is the handler order: inputs, alloys, outputs, fuel.
        for (int lane = 0; lane < ForgeBlockEntity.LANES; lane++) {
            addSlot(new SlotItemHandler(handler, ForgeBlockEntity.INPUT_START + lane, laneX(lane), INPUT_Y));
        }
        for (int lane = 0; lane < ForgeBlockEntity.LANES; lane++) {
            addSlot(new SlotItemHandler(handler, ForgeBlockEntity.ALLOY_START + lane, laneX(lane), ALLOY_Y));
        }
        for (int lane = 0; lane < ForgeBlockEntity.LANES; lane++) {
            addSlot(new OutputSlot(handler, ForgeBlockEntity.OUTPUT_START + lane, laneX(lane), OUTPUT_Y));
        }
        addSlot(new SlotItemHandler(handler, ForgeBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
    }

    public static int laneX(int lane) {
        return LANE_X + LANE_STEP * lane;
    }

    /** Output slots take nothing in and pay out the stored experience when something is taken. */
    private final class OutputSlot extends SlotItemHandler {
        OutputSlot(IItemHandlerModifiable handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            if (forge != null && player instanceof ServerPlayer serverPlayer) {
                forge.awardExperience(serverPlayer);
            }
            super.onTake(player, stack);
        }
    }

    // ---- synced state (valid on both sides) ----

    public boolean isBurning() {
        return data.get(ForgeBlockEntity.DATA_BURN) > 0;
    }

    /** Remaining burn as a fraction of the current fuel item, 0 to 1. */
    public float burnFraction() {
        int totalBurn = data.get(ForgeBlockEntity.DATA_BURN_TOTAL);
        return totalBurn <= 0 ? 0.0F : Math.min(1.0F, data.get(ForgeBlockEntity.DATA_BURN) / (float) totalBurn);
    }

    /** Progress of one lane as a fraction, 0 to 1. */
    public float progress(int lane) {
        int laneTotal = data.get(ForgeBlockEntity.DATA_TOTAL + lane);
        return laneTotal <= 0 ? 0.0F : Math.min(1.0F, data.get(ForgeBlockEntity.DATA_PROGRESS + lane) / (float) laneTotal);
    }

    // ---- menu behaviour ----

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
            if (index >= ForgeBlockEntity.OUTPUT_START && index < ForgeBlockEntity.FUEL_SLOT) {
                slot.onQuickCraft(stack, original);
            }
        } else if (ForgeRules.isFuel(stack)
                && moveItemStackTo(stack, ForgeBlockEntity.FUEL_SLOT, ForgeBlockEntity.FUEL_SLOT + 1, false)) {
            // moved into the fuel slot
        } else if (moveItemStackTo(stack, ForgeBlockEntity.INPUT_START, ForgeBlockEntity.INPUT_START + ForgeBlockEntity.LANES, false)
                || moveItemStackTo(stack, ForgeBlockEntity.ALLOY_START, ForgeBlockEntity.ALLOY_START + ForgeBlockEntity.LANES, false)) {
            // moved into an input or alloy slot
        } else if (index < PLAYER_START + 27) {
            if (!moveItemStackTo(stack, PLAYER_START + 27, PLAYER_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, PLAYER_START, PLAYER_START + 27, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate(
                (level, pos) -> level.getBlockState(pos).getBlock() instanceof ForgeBlock
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQR,
                true);
    }
}
