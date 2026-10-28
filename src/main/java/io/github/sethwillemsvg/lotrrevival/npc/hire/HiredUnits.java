package io.github.sethwillemsvg.lotrrevival.npc.hire;

import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

/** Server-side API of hired-unit state: hire, release and the desertion check. No coins, no screens. */
public final class HiredUnits {
    /** Desertion is checked this often (ticks) while the owner is online. */
    public static final int DESERTION_INTERVAL = 20;

    private HiredUnits() {}

    /** The unit's hire state ({@link HiredUnit#NONE} when unhired). Does not attach anything to the entity. */
    public static HiredUnit get(NpcEntity unit) {
        return unit.getExistingData(HireAttachments.HIRED_UNIT).orElse(HiredUnit.NONE);
    }

    public static void set(NpcEntity unit, HiredUnit state) {
        if (state.isHired()) {
            unit.setData(HireAttachments.HIRED_UNIT, state);
        } else {
            unit.removeData(HireAttachments.HIRED_UNIT);
        }
    }

    /**
     * Marks a freshly created unit as hired by the player: owner, name, R, pledge condition, squadron, task, FOLLOW,
     * teleport on, guard range 8. State only: the caller has already taken payment and placed the unit.
     */
    public static HiredUnit hire(NpcEntity unit, ServerPlayer owner, UnitList list, UnitEntry entry, String squadron) {
        HiredUnit state = new HiredUnit(
                Optional.of(owner.getUUID()),
                owner.getGameProfile().getName(),
                list.requiredAlignment(entry),
                entry.pledge(),
                unit.faction().location(),
                HiredUnit.cleanSquadron(squadron),
                entry.task(),
                HiredUnit.Command.DEFAULT,
                HiredUnit.Progress.START,
                Map.of());
        unit.setWild(false);
        unit.setPersistenceRequired();
        set(unit, state);
        return state;
    }

    /** The owner if online (any dimension). */
    @Nullable
    public static ServerPlayer onlineOwner(NpcEntity unit) {
        UUID owner = get(unit).owner().orElse(null);
        if (owner == null || unit.level().getServer() == null) {
            return null;
        }
        return unit.level().getServer().getPlayerList().getPlayer(owner);
    }

    /**
     * Ends the hire: drops gear the player gave (slots with a remembered original), restores the originals, clears
     * the owner and every command, messages the owner if online and posts {@link UnitReleasedEvent}. No-op on an
     * unhired unit.
     */
    public static void release(NpcEntity unit, UnitReleasedEvent.Reason reason) {
        HiredUnit state = get(unit);
        if (!state.isHired()) {
            return;
        }
        UUID ownerId = state.owner().get();
        for (Map.Entry<EquipmentSlot, ItemStack> original : state.originalGear().entrySet()) {
            ItemStack current = unit.getItemBySlot(original.getKey());
            if (!current.isEmpty()) {
                unit.spawnAtLocation(current.copy());
            }
            unit.setItemSlot(original.getKey(), original.getValue().copy());
        }
        set(unit, HiredUnit.NONE);
        io.github.sethwillemsvg.lotrrevival.npc.hire.progress.UnitLevels.removeHealthBonus(unit);
        unit.setTarget(null);
        unit.getNavigation().stop();

        ServerPlayer owner = unit.level().getServer() == null
                ? null
                : unit.level().getServer().getPlayerList().getPlayer(ownerId);
        boolean notified = false;
        if (owner != null && !owner.isFakePlayer()) {
            String key = reason == UnitReleasedEvent.Reason.DESERTED
                    ? "npc.lotrrevival.unit.deserted"
                    : "npc.lotrrevival.unit.dismissed";
            owner.sendSystemMessage(Component.translatable(key, unit.speakerName()));
            notified = true;
        }
        NeoForge.EVENT_BUS.post(new UnitReleasedEvent(unit, ownerId, reason, notified));
    }

    /** Whether the owner still satisfies the unit: alignment at least R and the pledge condition. */
    public static boolean ownerSatisfies(NpcEntity unit, HiredUnit state, ServerPlayer owner) {
        ResourceKey<Faction> faction = ResourceKey.create(ModDataRegistries.FACTION, state.faction());
        if (Alignment.get(owner, faction) < state.requiredAlignment()) {
            return false;
        }
        return state.pledge().holds(unit.level(), faction, Alignment.pledge(owner));
    }

    /**
     * The desertion check, run every {@link #DESERTION_INTERVAL} ticks from the entity tick. Does nothing while the
     * owner is offline.
     */
    public static void checkDesertion(NpcEntity unit) {
        HiredUnit state = get(unit);
        if (!state.isHired()) {
            return;
        }
        ServerPlayer owner = onlineOwner(unit);
        if (owner == null) {
            return;
        }
        if (!ownerSatisfies(unit, state, owner)) {
            release(unit, UnitReleasedEvent.Reason.DESERTED);
        }
    }

    /** Whether this unit is hired by the player. */
    public static boolean isOwnedBy(NpcEntity unit, UUID player) {
        return get(unit).owner().filter(player::equals).isPresent();
    }
}
