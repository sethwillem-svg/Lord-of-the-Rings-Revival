package io.github.sethwillemsvg.lotrrevival.npc.hire.command;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitReleasedEvent;
import io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitCommands;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The unit command menu ({@code lotrrevival:unit_command}): five gear slots backed by the unit's player-given gear and
 * the player inventory. Switches and dismissal arrive as {@link UnitCommandPayloads.Command} and run through
 * {@link #apply}. The server pushes a {@link UnitInfo} to the owner whenever it changes.
 */
public class UnitCommandMenu extends AbstractContainerMenu {
    /** What the screen can ask for. The ids are the wire values; unknown ids are dropped. */
    public enum Action {
        GUARD_ON,
        GUARD_OFF,
        SET_RANGE,
        TELEPORT_ON,
        TELEPORT_OFF,
        DISMISS;

        @Nullable
        public static Action byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : null;
        }
    }

    public static final int GEAR_SLOTS = UnitGear.SLOTS.length;
    public static final int PLAYER_START = GEAR_SLOTS;
    public static final int PLAYER_END = PLAYER_START + 36;

    // Layout shared with the screen (relative to the top-left of the background).
    public static final int GEAR_X = 8;
    public static final int GEAR_Y = 94;
    public static final int GEAR_STEP = 20;
    public static final int INVENTORY_Y = 160;
    public static final int HOTBAR_Y = 218;

    /** Close distance (12 blocks, like the trade screen). */
    public static final double REACH_SQR = 12.0 * 12.0;

    @Nullable private final NpcEntity unit;
    private final Player viewer;
    private final int unitId;
    @Nullable private UnitInfo info;
    @Nullable private UnitInfo lastSent;

    /** Client constructor, called with the extra data written by {@link UnitCommandMenus#open}. */
    public UnitCommandMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, null, buf.readVarInt(), new SimpleContainer(GEAR_SLOTS));
    }

    /** Server constructor. */
    public UnitCommandMenu(int containerId, Inventory inventory, NpcEntity unit) {
        this(containerId, inventory, unit, unit.getId(), new UnitGearContainer(unit));
    }

    private UnitCommandMenu(int containerId, Inventory inventory, @Nullable NpcEntity unit, int unitId, Container gear) {
        super(UnitCommandMenus.MENU.get(), containerId);
        this.unit = unit;
        this.viewer = inventory.player;
        this.unitId = unitId;
        for (int i = 0; i < GEAR_SLOTS; i++) {
            addSlot(new GearSlot(gear, i, GEAR_X + GEAR_STEP * i, GEAR_Y, UnitGear.SLOTS[i]));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    /** A gear slot: holds one item that fits its equipment slot. */
    private static final class GearSlot extends Slot {
        private final EquipmentSlot equipment;

        GearSlot(Container container, int index, int x, int y, EquipmentSlot equipment) {
            super(container, index, x, y);
            this.equipment = equipment;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return UnitGear.accepts(equipment, stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    // ------------------------------------------------------------------ state

    public int unitId() {
        return unitId;
    }

    @Nullable
    public NpcEntity unit() {
        return unit;
    }

    /** Client: the latest synced values, null until the first sync arrives. */
    @Nullable
    public UnitInfo info() {
        return info;
    }

    /** Client: takes the synced values. */
    public void applyInfo(UnitInfo info) {
        this.info = info;
    }

    // ------------------------------------------------------------------ server actions

    /**
     * Runs a screen action on the unit. The caller has checked that the player has this menu open and that it is
     * still valid. Range values outside 1-64 are refused.
     *
     * @return whether anything was applied
     */
    public boolean apply(Action action, int value) {
        if (unit == null || !HiredUnits.get(unit).isHired()) {
            return false;
        }
        boolean rangeOk = value >= UnitCommands.MIN_GUARD_RANGE && value <= UnitCommands.MAX_GUARD_RANGE;
        return switch (action) {
            case GUARD_ON -> rangeOk && UnitCommands.setGuard(unit, true, value);
            case GUARD_OFF -> UnitCommands.setGuard(unit, false, HiredUnits.get(unit).command().guardRange());
            case SET_RANGE -> rangeOk && UnitCommands.setGuardRange(unit, value);
            case TELEPORT_ON -> UnitCommands.setTeleport(unit, true);
            case TELEPORT_OFF -> UnitCommands.setTeleport(unit, false);
            case DISMISS -> {
                HiredUnits.release(unit, UnitReleasedEvent.Reason.DISMISSED);
                yield true;
            }
        };
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (unit != null && viewer instanceof ServerPlayer player) {
            UnitInfo now = UnitInfo.of(unit);
            if (now != null && !now.equals(lastSent)) {
                lastSent = now;
                if (!player.isFakePlayer()) {
                    PacketDistributor.sendToPlayer(player, new UnitCommandPayloads.Info(containerId, now));
                }
            }
        }
    }

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
        } else {
            boolean moved = false;
            for (int i = 0; i < GEAR_SLOTS && !moved; i++) {
                Slot gear = slots.get(i);
                if (!gear.hasItem() && gear.mayPlace(stack)) {
                    gear.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
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

    /**
     * Valid while the unit lives, is hired by this player, is in the player's level within 12 blocks and the player is
     * alive. The client side (no unit) is always valid.
     */
    @Override
    public boolean stillValid(Player player) {
        if (unit == null) {
            return true;
        }
        HiredUnit state = HiredUnits.get(unit);
        return player.isAlive()
                && unit.isAlive()
                && !unit.isRemoved()
                && unit.level() == player.level()
                && player.distanceToSqr(unit) <= REACH_SQR
                && state.owner().filter(owner -> owner.equals(player.getUUID())).isPresent();
    }
}
