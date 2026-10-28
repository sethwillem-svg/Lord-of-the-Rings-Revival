package io.github.sethwillemsvg.lotrrevival.core.brewing;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Menu of the brewing barrel: 6 ingredient slots, 3 water slots, a display-only result slot and the player
 * inventory. State is synced as four container-data values (state, elapsed, tier, servings). The Brew
 * button is the vanilla menu button with id {@link #BUTTON_BREW}.
 */
public class BrewingBarrelMenu extends AbstractContainerMenu {
    public static final int BUTTON_BREW = 0;
    public static final int SLOT_RESULT = BrewingBarrelBlockEntity.SLOTS;
    public static final int PLAYER_START = SLOT_RESULT + 1;
    public static final int PLAYER_END = PLAYER_START + 36;

    // Layout shared with the screen (relative to the top-left of the 176x166 background).
    public static final int INGREDIENT_X = 26;
    public static final int INGREDIENT_Y = 16;
    public static final int WATER_Y = 52;
    public static final int RESULT_X = 148;
    public static final int RESULT_Y = 32;

    private static final double REACH_SQR = 8.0 * 8.0;

    @Nullable private final BrewingBarrelBlockEntity barrel;
    private final ContainerLevelAccess access;
    private final ContainerData data;
    private final Container result = new SimpleContainer(1);

    /** Client constructor, called with the extra data written by the server (the barrel position). */
    public BrewingBarrelMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId,
                inventory,
                new BrewingBarrelBlockEntity.BarrelInventory(() -> {}, () -> true),
                new SimpleContainerData(4),
                ContainerLevelAccess.create(inventory.player.level(), buf.readBlockPos()),
                null);
    }

    /** Server constructor. */
    public BrewingBarrelMenu(int containerId, Inventory inventory, BrewingBarrelBlockEntity barrel) {
        this(containerId,
                inventory,
                barrel.inventory(),
                barrel.dataAccess(),
                ContainerLevelAccess.create(barrel.getLevel(), barrel.getBlockPos()),
                barrel);
    }

    private BrewingBarrelMenu(
            int containerId,
            Inventory inventory,
            IItemHandlerModifiable handler,
            ContainerData data,
            ContainerLevelAccess access,
            @Nullable BrewingBarrelBlockEntity barrel) {
        super(BrewingBlocks.BARREL_MENU.get(), containerId);
        this.barrel = barrel;
        this.access = access;
        this.data = data;

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new InputSlot(handler, row * 3 + col, INGREDIENT_X + 18 * col, INGREDIENT_Y + 18 * row));
            }
        }
        for (int col = 0; col < 3; col++) {
            addSlot(new InputSlot(handler, BrewingBarrelBlockEntity.INGREDIENT_SLOTS + col, INGREDIENT_X + 18 * col, WATER_Y));
        }
        addSlot(new Slot(result, 0, RESULT_X, RESULT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
        updatePreview();
    }

    /** Ingredient slots (0-5) take anything and water slots (6-8) only water buckets, both only while Empty. */
    private final class InputSlot extends SlotItemHandler {
        private final int handlerIndex;

        InputSlot(IItemHandlerModifiable handler, int index, int x, int y) {
            super(handler, index, x, y);
            this.handlerIndex = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty()
                    && state() == BarrelState.EMPTY
                    && (handlerIndex < BrewingBarrelBlockEntity.INGREDIENT_SLOTS || stack.is(Items.WATER_BUCKET));
        }
    }

    // ---- synced state (valid on both sides) ----

    public BarrelState state() {
        return BarrelState.byOrdinal(data.get(0));
    }

    public int elapsed() {
        return data.get(1);
    }

    public DrinkTier tier() {
        return DrinkTier.byIndex(data.get(2));
    }

    public int servings() {
        return data.get(3);
    }

    /** The drink shown in the result slot (a preview while Empty, the brew otherwise). */
    public ItemStack resultStack() {
        return result.getItem(0);
    }

    /** True if all three water slots hold water buckets (readable on both sides). */
    public boolean hasWater() {
        for (int i = BrewingBarrelBlockEntity.INGREDIENT_SLOTS; i < BrewingBarrelBlockEntity.SLOTS; i++) {
            if (!getSlot(i).getItem().is(Items.WATER_BUCKET)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the Brew button does something right now (the server re-validates on press). */
    public boolean canPressBrew() {
        return switch (state()) {
            case EMPTY -> !resultStack().isEmpty() && hasWater();
            case BREWING -> elapsed() >= BrewingBarrelBlockEntity.TIER_TICKS;
            case FULL -> false;
        };
    }

    /** Recomputes the display-only result slot (server only). */
    public void updatePreview() {
        if (barrel != null) {
            result.setItem(0, barrel.previewResult());
        }
    }

    @Override
    public void broadcastChanges() {
        updatePreview();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_BREW && barrel != null) {
            barrel.pressBrew();
            updatePreview();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index == SLOT_RESULT) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SLOT_RESULT) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (state() != BarrelState.EMPTY) {
                return ItemStack.EMPTY;
            }
            boolean moved = stack.is(Items.WATER_BUCKET)
                    ? moveItemStackTo(stack, BrewingBarrelBlockEntity.INGREDIENT_SLOTS, SLOT_RESULT, false)
                    : moveItemStackTo(stack, 0, BrewingBarrelBlockEntity.INGREDIENT_SLOTS, false);
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate(
                (level, pos) -> level.getBlockState(pos).is(BrewingBlocks.BARREL.get())
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQR,
                true);
    }
}
