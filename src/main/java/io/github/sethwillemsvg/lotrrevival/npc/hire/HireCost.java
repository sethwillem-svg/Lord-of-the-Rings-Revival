package io.github.sethwillemsvg.lotrrevival.npc.hire;

import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionMember;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcStanding;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Hire cost and hire requirements: pure functions plus thin wrappers that read the player and the level. */
public final class HireCost {
    private HireCost() {}

    /** Why a player cannot hire an entry. Declared in the order the checks run. */
    public enum Failure {
        HOSTILE,
        ALIGNMENT,
        PLEDGE
    }

    /**
     * The spec formula: S = max(A - R, 0); f = clamp(S / 1500) if pledged else clamp(S / 2000);
     * cost = max(1, round(C x (pledged ? 1 : 2) x (1 - 0.5 f))), rounding half up.
     *
     * @param baseCost C
     * @param required R
     * @param alignment A, the player's alignment with the unit's faction
     * @param pledged whether the player is pledged to the unit's faction
     */
    public static int cost(int baseCost, float required, float alignment, boolean pledged) {
        double surplus = Math.max((double) alignment - required, 0.0);
        double f = Math.min(Math.max(surplus / (pledged ? 1500.0 : 2000.0), 0.0), 1.0);
        double cost = baseCost * (pledged ? 1.0 : 2.0) * (1.0 - 0.5 * f);
        return Math.max(1, (int) Math.floor(cost + 0.5 + 1.0e-9));
    }

    /**
     * The requirement check (the coin check is separate). Returns the first failing reason: hostile, alignment below
     * R, or an unmet pledge condition.
     */
    public static Optional<Failure> check(boolean hostile, float alignment, float required, boolean pledgeHolds) {
        if (hostile) {
            return Optional.of(Failure.HOSTILE);
        }
        if (alignment < required) {
            return Optional.of(Failure.ALIGNMENT);
        }
        if (!pledgeHolds) {
            return Optional.of(Failure.PLEDGE);
        }
        return Optional.empty();
    }

    /** The faction an entity type belongs to, read from a throwaway instance. Empty for non-faction types. */
    public static Optional<ResourceKey<Faction>> unitFaction(Level level, EntityType<?> type) {
        Entity probe = type.create(level);
        if (probe == null) {
            return Optional.empty();
        }
        try {
            return probe instanceof FactionMember member ? Optional.of(member.faction()) : Optional.empty();
        } finally {
            probe.discard();
        }
    }

    /** Whether the player is pledged to the faction. */
    public static boolean pledgedTo(Player player, ResourceKey<Faction> faction) {
        return Alignment.pledge(player).filter(faction::equals).isPresent();
    }

    /** Cost of an entry for a player. */
    public static int costFor(Player player, UnitList list, UnitEntry entry, ResourceKey<Faction> unitFaction) {
        return cost(
                entry.cost(),
                list.requiredAlignment(entry),
                Alignment.get(player, unitFaction),
                pledgedTo(player, unitFaction));
    }

    /** Requirement check for a player at a trader; the trader's faction decides hostility, the unit's the rest. */
    public static Optional<Failure> checkFor(
            NpcEntity trader, ServerPlayer player, UnitList list, UnitEntry entry, ResourceKey<Faction> unitFaction) {
        return check(
                NpcStanding.isHostile(trader, player),
                Alignment.get(player, unitFaction),
                list.requiredAlignment(entry),
                entry.pledge().holds(player.level(), unitFaction, Alignment.pledge(player)));
    }
}
