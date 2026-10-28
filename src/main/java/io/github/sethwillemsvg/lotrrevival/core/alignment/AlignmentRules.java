package io.github.sethwillemsvg.lotrrevival.core.alignment;

import io.github.sethwillemsvg.lotrrevival.core.config.CoreConfig;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

/** Pure alignment arithmetic: ranks, pledge thresholds, penalty scaling, break penalty and cooldown. */
public final class AlignmentRules {
    public static final float MAX_ALIGNMENT = 10_000.0f;
    public static final float MIN_ALIGNMENT = -10_000.0f;

    /** Implicit rank at alignment 0 and above when no listed rank applies. */
    public static final FactionRank NEUTRAL = new FactionRank("neutral", 0.0f);
    /** Implicit rank below 0. */
    public static final FactionRank HOSTILE = new FactionRank("hostile", MIN_ALIGNMENT);

    private AlignmentRules() {}

    /** Rounds to one decimal. */
    public static float round(float value) {
        return Math.round(value * 10.0f) / 10.0f;
    }

    public static float clamp(float value) {
        return Math.max(MIN_ALIGNMENT, Math.min(MAX_ALIGNMENT, value));
    }

    /** Ranks sorted by threshold (the JSON order is not trusted). */
    public static List<FactionRank> sortedRanks(Faction faction) {
        return faction.ranks().stream()
                .sorted(Comparator.comparingDouble(FactionRank::threshold))
                .toList();
    }

    /** Highest listed rank whose threshold is at or below the value; else neutral (>= 0) or hostile. */
    public static FactionRank rank(Faction faction, float value) {
        FactionRank best = null;
        for (FactionRank rank : sortedRanks(faction)) {
            if (rank.threshold() <= value) {
                best = rank;
            }
        }
        if (best != null) {
            return best;
        }
        return value >= 0.0f ? NEUTRAL : HOSTILE;
    }

    /** The alignment needed to pledge: the pledge rank's threshold, else the lowest rank at or above 100. */
    public static Optional<Float> pledgeThreshold(Faction faction) {
        if (faction.fixedAlignment().isPresent() || faction.hidden()) {
            return Optional.empty();
        }
        List<FactionRank> ranks = sortedRanks(faction);
        if (faction.explicitPledgeRank().isPresent()) {
            String id = faction.explicitPledgeRank().get();
            for (FactionRank rank : ranks) {
                if (rank.id().equals(id)) {
                    return Optional.of(rank.threshold());
                }
            }
        }
        for (FactionRank rank : ranks) {
            if (rank.threshold() >= 100.0f) {
                return Optional.of(rank.threshold());
            }
        }
        return Optional.empty();
    }

    /** Penalty multiplier for a kill spread: clamp(alignment / 50, 1, 20) when positive, else 1. */
    public static float penaltyScale(float alignment) {
        if (alignment <= 0.0f) {
            return 1.0f;
        }
        return Math.max(1.0f, Math.min(20.0f, alignment / 50.0f));
    }

    /**
     * Alignment after a pledge break: the threshold of the rank two below the current rank, never below half the
     * pledge threshold; unchanged when already at or below that value.
     */
    public static float breakTarget(Faction faction, float current, float pledgeThreshold) {
        List<FactionRank> ranks = sortedRanks(faction);
        int index = -1;
        for (int n = 0; n < ranks.size(); n++) {
            if (ranks.get(n).threshold() <= current) {
                index = n;
            }
        }
        float floor = pledgeThreshold / 2.0f;
        float target = index - 2 >= 0 ? ranks.get(index - 2).threshold() : floor;
        target = Math.max(target, floor);
        return current <= target ? current : target;
    }

    /** Cooldown ticks after a break: base + extra x clamp((before - threshold) / scale, 0, 1), rounded down. */
    public static int cooldownTicks(float alignmentBefore, float pledgeThreshold) {
        double base = CoreConfig.get(CoreConfig.PLEDGE_COOLDOWN_BASE_TICKS);
        double extra = CoreConfig.get(CoreConfig.PLEDGE_COOLDOWN_EXTRA_TICKS);
        double scale = CoreConfig.get(CoreConfig.PLEDGE_COOLDOWN_SCALE);
        double above = Math.max(0.0, Math.min(scale, alignmentBefore - pledgeThreshold));
        return (int) Math.floor(base + extra * above / scale);
    }

    /** Display name of a rank: the faction's lang key, falling back to the rank id. */
    public static Component rankName(ResourceKey<Faction> faction, FactionRank rank) {
        if (rank == NEUTRAL || rank == HOSTILE) {
            return Component.translatable("alignment.lotrrevival.rank." + rank.id());
        }
        return Component.translatableWithFallback(
                "faction." + faction.location().getNamespace() + "." + faction.location().getPath() + ".rank."
                        + rank.id(),
                rank.id());
    }
}
