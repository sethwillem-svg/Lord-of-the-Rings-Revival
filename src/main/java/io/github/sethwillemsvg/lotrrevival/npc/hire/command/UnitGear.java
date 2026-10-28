package io.github.sethwillemsvg.lotrrevival.npc.hire.command;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;

/**
 * Player-given gear on a hired unit. A slot holds a player-given item exactly when the hire state remembers an
 * original for it (the original may be empty). Giving remembers the unit's own piece once; taking the item back
 * restores it. {@code HiredUnits.release} drops the given pieces and restores the originals the same way.
 */
public final class UnitGear {
    /** The slots of the command screen, in screen order. */
    public static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND
    };

    private UnitGear() {}

    /** Whether the stack may be given for this slot: armor must belong to the slot, any item may go in the main hand. */
    public static boolean accepts(EquipmentSlot slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (slot == EquipmentSlot.MAINHAND) {
            return true;
        }
        EquipmentSlot wanted = stack.getEquipmentSlot();
        if (wanted == null && stack.getItem() instanceof Equipable equipable) {
            wanted = equipable.getEquipmentSlot();
        }
        return wanted == slot;
    }

    /** What the player gave for this slot, else empty (the unit's own piece is not "given"). */
    public static ItemStack given(NpcEntity unit, EquipmentSlot slot) {
        HiredUnit state = HiredUnits.get(unit);
        return state.isHired() && state.originalGear().containsKey(slot) ? unit.getItemBySlot(slot) : ItemStack.EMPTY;
    }

    /**
     * Puts the item on the unit, remembering its own piece if this is the first replacement of the slot.
     *
     * @return false if the unit is not hired or the item does not fit the slot
     */
    public static boolean give(NpcEntity unit, EquipmentSlot slot, ItemStack stack) {
        HiredUnit state = HiredUnits.get(unit);
        if (!state.isHired() || !accepts(slot, stack)) {
            return false;
        }
        HiredUnits.set(unit, state.withOriginalGear(slot, unit.getItemBySlot(slot)));
        unit.setItemSlot(slot, stack.copyWithCount(1));
        return true;
    }

    /**
     * Takes the player-given item off the unit and restores its own piece.
     *
     * @return the given item (empty if nothing was given for the slot)
     */
    public static ItemStack take(NpcEntity unit, EquipmentSlot slot) {
        HiredUnit state = HiredUnits.get(unit);
        if (!state.isHired() || !state.originalGear().containsKey(slot)) {
            return ItemStack.EMPTY;
        }
        ItemStack given = unit.getItemBySlot(slot);
        ItemStack original = state.originalGear().get(slot);
        Map<EquipmentSlot, ItemStack> remaining = new EnumMap<>(EquipmentSlot.class);
        remaining.putAll(state.originalGear());
        remaining.remove(slot);
        HiredUnits.set(
                unit,
                new HiredUnit(
                        state.owner(),
                        state.ownerName(),
                        state.requiredAlignment(),
                        state.pledge(),
                        state.faction(),
                        state.squadron(),
                        state.task(),
                        state.command(),
                        state.progress(),
                        Map.copyOf(remaining)));
        unit.setItemSlot(slot, original.copy());
        return given;
    }
}
