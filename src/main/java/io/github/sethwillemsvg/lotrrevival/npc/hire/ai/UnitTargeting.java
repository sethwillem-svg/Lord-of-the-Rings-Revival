package io.github.sethwillemsvg.lotrrevival.npc.hire.ai;

import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelation;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * What a hired unit adds to the combat target goal: never its owner, owner's mount or sibling units; the owner's
 * attacker and target; neutral factions the owner is hostile to; nothing while halted; nothing outside the guard area.
 * Retaliation is handled by the target goal before any of this.
 */
public final class UnitTargeting {
    /** Ticks an owner's attacker or target stays interesting to the owner's units. */
    public static final int OWNER_MEMORY_TICKS = 100;

    private UnitTargeting() {}

    /** The verdict on a candidate for a hired unit. */
    public enum Verdict {
        /** Not a target for this unit. */
        REJECT,
        /** The owner hit it or it hit the owner recently. */
        OWNER_TARGET,
        /** A neutral-faction NPC while the owner is hostile to that faction. */
        NEUTRAL_ENEMY,
        /** No special rule: the ordinary priorities decide. */
        NORMAL
    }

    /** Judges a candidate for a unit; unhired NPCs always get {@link Verdict#NORMAL}. */
    public static Verdict judge(NpcEntity npc, LivingEntity candidate) {
        HiredUnit state = HiredUnits.get(npc);
        if (!state.isHired()) {
            return Verdict.NORMAL;
        }
        if (state.command().halted() || isOwnerSide(npc, state, candidate)) {
            return Verdict.REJECT;
        }
        UnitAi.Area area = UnitAi.guardArea(npc);
        if (area != null && !area.contains(candidate)) {
            return Verdict.REJECT;
        }
        ServerPlayer owner = UnitAi.presentOwner(npc);
        if (owner == null) {
            return Verdict.NORMAL;
        }
        if (isOwnerTarget(owner, candidate)) {
            return Verdict.OWNER_TARGET;
        }
        if (neutralWhileOwnerHostile(npc, owner, candidate)) {
            return Verdict.NEUTRAL_ENEMY;
        }
        return Verdict.NORMAL;
    }

    /** The owner, the owner's mount, or another unit of the same owner whose faction is not an enemy of ours. */
    static boolean isOwnerSide(NpcEntity npc, HiredUnit state, LivingEntity candidate) {
        UUID ownerId = state.owner().orElse(null);
        if (ownerId == null) {
            return false;
        }
        if (candidate instanceof Player player && player.getUUID().equals(ownerId)) {
            return true;
        }
        ServerPlayer owner = HiredUnits.onlineOwner(npc);
        if (owner != null && owner.getVehicle() == candidate) {
            return true;
        }
        return candidate instanceof NpcEntity other
                && HiredUnits.isOwnedBy(other, ownerId)
                && !FactionRelations.isHostile(npc, other);
    }

    /** The owner attacked it, or it attacked the owner, within the last {@value #OWNER_MEMORY_TICKS} ticks. */
    static boolean isOwnerTarget(ServerPlayer owner, LivingEntity candidate) {
        if (owner.getLastHurtMob() == candidate
                && owner.tickCount - owner.getLastHurtMobTimestamp() <= OWNER_MEMORY_TICKS) {
            return true;
        }
        return owner.getLastHurtByMob() == candidate
                && owner.tickCount - owner.getLastHurtByMobTimestamp() <= OWNER_MEMORY_TICKS;
    }

    private static boolean neutralWhileOwnerHostile(NpcEntity npc, ServerPlayer owner, LivingEntity candidate) {
        if (candidate instanceof Player) {
            return false;
        }
        Optional<ResourceKey<Faction>> other = FactionRelations.factionOf(candidate);
        if (other.isEmpty()
                || FactionRelations.relation(npc.level().registryAccess(), npc.faction(), other.get())
                        != FactionRelation.NEUTRAL) {
            return false;
        }
        Registry<Faction> registry = npc.level().registryAccess().registryOrThrow(ModDataRegistries.FACTION);
        Faction faction = registry.get(other.get());
        return faction != null && Alignment.get(owner, other.get()) < faction.hostilityThreshold();
    }
}
