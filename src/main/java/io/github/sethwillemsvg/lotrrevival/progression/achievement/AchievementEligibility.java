package io.github.sethwillemsvg.lotrrevival.progression.achievement;

import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;

/**
 * Whether an achievement entry counts for a player right now. Pure reads of Core's alignment API, so it
 * gives the same answer on the server and on the client (Core's reads use the synced mirror there).
 */
public final class AchievementEligibility {
    private AchievementEligibility() {}

    public static boolean isEligible(Player player, AchievementEntry entry) {
        Registry<Faction> factions = player.level().registryAccess().registryOrThrow(ModDataRegistries.FACTION);
        if (entry.rank().isPresent()) {
            return rankEligible(player, factions, entry.rank().get());
        }
        for (AchievementEntry.Requirement requirement : entry.requires()) {
            if (!requirementHolds(player, factions, requirement)) {
                return false;
            }
        }
        return true;
    }

    /** Ally: alignment >= 0 with at least one listed faction. Enemy: alignment <= 0 with at least one. */
    private static boolean requirementHolds(
            Player player, Registry<Faction> factions, AchievementEntry.Requirement requirement) {
        for (ResourceKey<Faction> faction : requirement.factions()) {
            if (!factions.containsKey(faction)) {
                continue;
            }
            float value = Alignment.get(player, faction);
            boolean holds = requirement.relation() == AchievementEntry.Relation.ALLY ? value >= 0.0f : value <= 0.0f;
            if (holds) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rank entries need alignment >= 0 with the faction and, for a rank above the faction's pledge rank, an
     * active pledge to that faction. Factions without a pledge rank have no pledge gate.
     */
    private static boolean rankEligible(
            Player player, Registry<Faction> factions, AchievementEntry.RankRequirement requirement) {
        Faction faction = factions.get(requirement.faction());
        if (faction == null) {
            return false;
        }
        List<FactionRank> ladder = faction.ranks();
        int rankIndex = indexOf(ladder, requirement.rank());
        if (rankIndex < 0 || Alignment.get(player, requirement.faction()) < 0.0f) {
            return false;
        }
        Optional<String> pledgeRank = faction.pledgeRank();
        if (pledgeRank.isEmpty()) {
            return true;
        }
        int pledgeIndex = indexOf(ladder, pledgeRank.get());
        if (pledgeIndex < 0 || rankIndex <= pledgeIndex) {
            return true;
        }
        return Alignment.pledge(player).filter(requirement.faction()::equals).isPresent();
    }

    private static int indexOf(List<FactionRank> ladder, String rankId) {
        for (int i = 0; i < ladder.size(); i++) {
            if (ladder.get(i).id().equals(rankId)) {
                return i;
            }
        }
        return -1;
    }
}
