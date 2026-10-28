package io.github.sethwillemsvg.lotrrevival.client.progression.menu;

import io.github.sethwillemsvg.lotrrevival.client.progression.ClientAchievements;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEligibility;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * What the Achievements page shows: eligible entries per tab in page order, plus the header counts. Plain data and
 * pure functions over the synced registry and the client mirrors, so it can be tested without a screen.
 */
public final class AchievementPageModel {
    /** One visible entry. */
    public record Row(ResourceLocation id, AchievementEntry entry, boolean earned) {}

    private static final Comparator<Row> PAGE_ORDER = Comparator.<Row>comparingInt(
                    row -> row.entry().sortGroup().ordinal())
            .thenComparing(row -> row.entry().sortGroup() == AchievementEntry.SortGroup.SPECIAL
                    ? row.id().toString()
                    : row.entry().title().getString().toLowerCase(java.util.Locale.ROOT))
            .thenComparing(row -> row.id().toString());

    private final Map<AchievementTab, List<Row>> tabs = new EnumMap<>(AchievementTab.class);
    private final int earnedCounted;
    private final int totalCounted;

    private AchievementPageModel(Map<AchievementTab, List<Row>> rows, int earnedCounted, int totalCounted) {
        for (AchievementTab tab : AchievementTab.values()) {
            tabs.put(tab, List.copyOf(rows.getOrDefault(tab, List.of())));
        }
        this.earnedCounted = earnedCounted;
        this.totalCounted = totalCounted;
    }

    /** Builds the model for the local player from the registry and the client earned set. */
    public static AchievementPageModel build(Player player) {
        Registry<AchievementEntry> registry = player.level()
                .registryAccess()
                .registry(AchievementEntry.REGISTRY)
                .orElse(null);
        Map<AchievementTab, List<Row>> rows = new EnumMap<>(AchievementTab.class);
        int earnedCounted = 0;
        int totalCounted = 0;
        if (registry != null) {
            Set<ResourceLocation> earned = ClientAchievements.earned();
            for (ResourceLocation id : registry.keySet()) {
                AchievementEntry entry = registry.get(id);
                if (entry == null || !AchievementEligibility.isEligible(player, entry)) {
                    continue;
                }
                boolean isEarned = earned.contains(id);
                rows.computeIfAbsent(entry.tab(), tab -> new ArrayList<>()).add(new Row(id, entry, isEarned));
                if (entry.counted()) {
                    totalCounted++;
                    if (isEarned) {
                        earnedCounted++;
                    }
                }
            }
            rows.values().forEach(AchievementPageModel::sort);
        }
        return new AchievementPageModel(rows, earnedCounted, totalCounted);
    }

    /** Orders rows for display: {@code special} by id, then {@code biome} and {@code other} by title. */
    public static void sort(List<Row> rows) {
        rows.sort(PAGE_ORDER);
    }

    public List<Row> rows(AchievementTab tab) {
        return tabs.get(tab);
    }

    /** Earned, eligible and counted entries over all eligible counted entries (header of the whole page). */
    public int earnedTotal() {
        return earnedCounted;
    }

    public int total() {
        return totalCounted;
    }

    public int earnedIn(AchievementTab tab) {
        return (int) tabs.get(tab).stream().filter(Row::earned).count();
    }

    public int totalIn(AchievementTab tab) {
        return tabs.get(tab).size();
    }

    /** Cheap change detector for live refreshes. */
    public boolean sameAs(AchievementPageModel other) {
        if (earnedCounted != other.earnedCounted || totalCounted != other.totalCounted) {
            return false;
        }
        for (AchievementTab tab : AchievementTab.values()) {
            List<Row> a = tabs.get(tab);
            List<Row> b = other.tabs.get(tab);
            if (a.size() != b.size()) {
                return false;
            }
            for (int n = 0; n < a.size(); n++) {
                if (!a.get(n).id().equals(b.get(n).id()) || a.get(n).earned() != b.get(n).earned()) {
                    return false;
                }
            }
        }
        return true;
    }
}
