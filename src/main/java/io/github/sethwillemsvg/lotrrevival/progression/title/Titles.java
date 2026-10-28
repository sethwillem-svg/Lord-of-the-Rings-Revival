package io.github.sethwillemsvg.lotrrevival.progression.title;

import io.github.sethwillemsvg.lotrrevival.client.progression.ClientAchievements;
import io.github.sethwillemsvg.lotrrevival.client.progression.ClientTitles;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEligibility;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntries;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Whether a title is usable by a player right now. Pure reads of Core's alignment API, the achievement state
 * and the player's granted set, so the server and the client (reading its synced mirrors) agree.
 */
public final class Titles {
    private Titles() {}

    /** Server config {@code titlesEnabled}, or the value the server sent when called on the client. */
    public static boolean enabled(Player player) {
        return player.level().isClientSide ? ClientTitles.enabled() : ProgressionConfig.get(ProgressionConfig.TITLES_ENABLED);
    }

    /** Server config {@code strictFactionTitles}, or the value the server sent when called on the client. */
    public static boolean strict(Player player) {
        return player.level().isClientSide
                ? ClientTitles.strict()
                : ProgressionConfig.get(ProgressionConfig.STRICT_FACTION_TITLES);
    }

    /** The granted (special) title ids of the player. */
    public static Set<ResourceLocation> granted(Player player) {
        return player.level().isClientSide
                ? ClientTitles.granted()
                : player.getData(ProgressionAttachments.TITLES).granted();
    }

    public static boolean isUsable(Player player, ResourceLocation id) {
        return TitleDefinitions.find(player.level().registryAccess(), id)
                .map(definition -> isUsable(player, id, definition))
                .orElse(false);
    }

    public static boolean isUsable(Player player, ResourceLocation id, TitleDefinition definition) {
        return switch (definition.source()) {
            case TitleSource.Starter starter -> true;
            case TitleSource.Special special -> granted(player).contains(id);
            case TitleSource.FactionAlignment source -> alignmentUsable(player, source);
            case TitleSource.AchievementSource source -> achievementUsable(player, source);
            case TitleSource.AlignmentRank source -> rankUsable(player, source);
        };
    }

    /** Ids of every usable title, in registry order. */
    public static List<ResourceLocation> usable(Player player) {
        return TitleDefinitions.all(player.level().registryAccess()).entrySet().stream()
                .filter(entry -> isUsable(player, entry.getKey(), entry.getValue()))
                .map(java.util.Map.Entry::getKey)
                .toList();
    }

    // ------------------------------------------------------------------ sources

    /**
     * Alignment >= {@code min} with any one listed faction; {@code pledge} means that faction's pledge-rank
     * threshold. With strict titles the player must also be pledged to that faction.
     */
    private static boolean alignmentUsable(Player player, TitleSource.FactionAlignment source) {
        Registry<Faction> factions = player.level().registryAccess().registryOrThrow(ModDataRegistries.FACTION);
        boolean strict = strict(player);
        for (ResourceKey<Faction> key : source.factions()) {
            Faction faction = factions.get(key);
            if (faction == null) {
                continue;
            }
            Float threshold = source.min().orElseGet(() -> pledgeThreshold(faction));
            if (threshold == null || Alignment.get(player, key) < threshold) {
                continue;
            }
            if (strict && !pledgedTo(player, key)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** The threshold of the faction's pledge rank, or null when it has none. */
    private static Float pledgeThreshold(Faction faction) {
        Optional<String> pledgeRank = faction.pledgeRank();
        if (pledgeRank.isEmpty()) {
            return null;
        }
        for (FactionRank rank : faction.ranks()) {
            if (rank.id().equals(pledgeRank.get())) {
                return rank.threshold();
            }
        }
        return null;
    }

    /** Earned, eligible and counted: the same test the achievement count uses. */
    private static boolean achievementUsable(Player player, TitleSource.AchievementSource source) {
        ResourceLocation id = source.achievement();
        boolean earned = player instanceof ServerPlayer serverPlayer
                ? AchievementService.earned(serverPlayer).contains(id)
                : ClientAchievements.isEarned(id);
        if (!earned) {
            return false;
        }
        AchievementEntry entry = AchievementEntries.find(player.level().registryAccess(), id)
                .orElse(null);
        return entry != null && entry.counted() && AchievementEligibility.isEligible(player, entry);
    }

    /**
     * Rank reached; a rank above the pledge rank also needs an active pledge to the faction, and with strict
     * titles so does the pledge rank itself.
     */
    private static boolean rankUsable(Player player, TitleSource.AlignmentRank source) {
        Registry<Faction> factions = player.level().registryAccess().registryOrThrow(ModDataRegistries.FACTION);
        Faction faction = factions.get(source.faction());
        if (faction == null) {
            return false;
        }
        List<FactionRank> ladder = faction.ranks();
        int rankIndex = indexOf(ladder, source.rank());
        if (rankIndex < 0 || !Alignment.meetsRank(player, source.faction(), source.rank())) {
            return false;
        }
        Optional<String> pledgeRank = faction.pledgeRank();
        if (pledgeRank.isEmpty()) {
            return true;
        }
        int pledgeIndex = indexOf(ladder, pledgeRank.get());
        if (pledgeIndex < 0) {
            return true;
        }
        boolean needsPledge = rankIndex > pledgeIndex || (rankIndex == pledgeIndex && strict(player));
        return !needsPledge || pledgedTo(player, source.faction());
    }

    private static boolean pledgedTo(Player player, ResourceKey<Faction> faction) {
        return Alignment.pledge(player).filter(faction::equals).isPresent();
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
