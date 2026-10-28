package io.github.sethwillemsvg.lotrrevival.core.table;

import io.github.sethwillemsvg.lotrrevival.core.event.FactionTableCraftEvent;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * 3x3 crafting grid plus result, like the vanilla table, without a recipe book. Matches faction recipes for its own
 * table first, then vanilla crafting recipes. Re-checks the alignment gate every 20 ticks and on every click.
 */
public class FactionTableMenu extends AbstractContainerMenu {
    public static final int RESULT_SLOT = 0;
    private static final int GRID_START = 1;
    private static final int INV_START = 10;
    private static final int INV_END = 46;
    private static final int RECHECK_TICKS = 20;
    private static final double REACH_SQR = 8.0 * 8.0;

    private final ContainerLevelAccess access;
    private final Player player;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final Block table;
    @Nullable private final FactionTableBlock factionTable;
    @Nullable private RecipeHolder<CraftingRecipe> lastRecipe;
    private int ticks;

    /** Client constructor: the server wrote the table position. */
    public FactionTableMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, ContainerLevelAccess.create(inventory.player.level(), buf.readBlockPos()), (Block) null);
    }

    /** Server constructor. */
    public FactionTableMenu(int containerId, Inventory inventory, ContainerLevelAccess access, FactionTableBlock block) {
        this(containerId, inventory, access, (Block) block);
    }

    private FactionTableMenu(int containerId, Inventory inventory, ContainerLevelAccess access, @Nullable Block block) {
        super(FactionTables.MENU.get(), containerId);
        this.access = access;
        this.player = inventory.player;
        Block resolved = block != null ? block : access.evaluate((level, pos) -> level.getBlockState(pos).getBlock(), FactionTables.HOBBIT.block().get());
        this.table = resolved;
        this.factionTable = resolved instanceof FactionTableBlock f ? f : null;

        addSlot(new FactionResultSlot(inventory.player, craftSlots, resultSlots, RESULT_SLOT, 124, 35));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(craftSlots, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    public Block table() {
        return table;
    }

    /** Result slot that announces every take on the game bus. */
    private final class FactionResultSlot extends ResultSlot {
        FactionResultSlot(Player player, CraftingContainer craftSlots, Container container, int slot, int x, int y) {
            super(player, craftSlots, container, slot, x, y);
        }

        @Override
        public void onTake(Player taker, ItemStack stack) {
            ItemStack taken = stack.copy();
            super.onTake(taker, stack);
            if (!taker.level().isClientSide && taker instanceof ServerPlayer serverPlayer && factionTable != null) {
                NeoForge.EVENT_BUS.post(
                        new FactionTableCraftEvent(serverPlayer, table, factionTable.faction(), taken));
            }
        }
    }

    @Override
    public void slotsChanged(Container container) {
        if (container == craftSlots) {
            updateResult();
        }
    }

    private void updateResult() {
        Level level = player.level();
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer) || level.getServer() == null) {
            return;
        }
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> found =
                FactionTableRecipes.find(level.getServer().getRecipeManager(), input, level, table, lastRecipe);
        if (found.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = found.get();
            lastRecipe = holder;
            if (resultSlots.setRecipeUsed(level, serverPlayer, holder)) {
                ItemStack assembled = holder.value().assemble(input, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) {
                    result = assembled;
                }
            }
        }
        resultSlots.setItem(0, result);
    }

    @Override
    public void broadcastChanges() {
        if (factionTable != null
                && player instanceof ServerPlayer
                && ++ticks % RECHECK_TICKS == 0
                && !FactionTableAccess.canUse(player, factionTable.faction())) {
            player.closeContainer();
            return;
        }
        super.broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player clicker) {
        if (!clicker.level().isClientSide
                && factionTable != null
                && !FactionTableAccess.canUse(clicker, factionTable.faction())) {
            clicker.closeContainer();
            return;
        }
        super.clicked(slotId, button, clickType, clicker);
    }

    @Override
    public void removed(Player removedBy) {
        super.removed(removedBy);
        access.execute((level, pos) -> clearContainer(removedBy, craftSlots));
    }

    @Override
    public boolean stillValid(Player stillPlayer) {
        return access.evaluate(
                (level, pos) -> level.getBlockState(pos).is(table)
                        && stillPlayer.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQR,
                true);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public ItemStack quickMoveStack(Player mover, int index) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            original = stack.copy();
            if (index == RESULT_SLOT) {
                access.execute((level, pos) -> stack.getItem().onCraftedBy(stack, level, mover));
                if (!moveItemStackTo(stack, INV_START, INV_END, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stack, original);
            } else if (index >= INV_START && index < INV_END) {
                if (!moveItemStackTo(stack, GRID_START, INV_START, false)) {
                    if (index < 37) {
                        if (!moveItemStackTo(stack, 37, INV_END, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (!moveItemStackTo(stack, INV_START, 37, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (!moveItemStackTo(stack, INV_START, INV_END, false)) {
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
            slot.onTake(mover, stack);
            if (index == RESULT_SLOT) {
                mover.drop(stack, false);
            }
        }
        return original;
    }
}
