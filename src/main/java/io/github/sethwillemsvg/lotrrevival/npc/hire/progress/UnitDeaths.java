package io.github.sethwillemsvg.lotrrevival.npc.hire.progress;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.friendlyfire.NpcPlayerPrefs;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

/** What happens when a hired unit dies: gear, the owner's message and {@link UnitDeathEvent}. */
public final class UnitDeaths {
    private UnitDeaths() {}

    /**
     * Drops the gear the player gave it, puts the unit's original gear back (so the normal drop chance decides), tells
     * the owner unless they turned it off, and posts {@link UnitDeathEvent}. Runs once per death.
     */
    public static void onDeath(NpcEntity unit, DamageSource source) {
        HiredUnit state = HiredUnits.get(unit);
        if (!state.isHired()) {
            return;
        }
        for (Map.Entry<EquipmentSlot, ItemStack> original : state.originalGear().entrySet()) {
            ItemStack given = unit.getItemBySlot(original.getKey());
            if (!given.isEmpty()) {
                unit.spawnAtLocation(given.copy());
            }
            unit.setItemSlot(original.getKey(), original.getValue().copy());
        }
        UUID ownerId = state.owner().orElseThrow();
        ServerPlayer owner = HiredUnits.onlineOwner(unit);
        boolean notified = false;
        if (owner != null && owner.connection != null && NpcPlayerPrefs.get(owner).unitDeathMessages()) {
            owner.sendSystemMessage(Component.translatable(
                    "npc.lotrrevival.unit.death",
                    unit.speakerName(),
                    state.progress().level(),
                    source.getLocalizedDeathMessage(unit)));
            notified = true;
        }
        NeoForge.EVENT_BUS.post(new UnitDeathEvent(unit, ownerId, source, notified));
    }
}
