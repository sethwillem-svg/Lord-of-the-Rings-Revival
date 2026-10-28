package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.RankRequirement;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.Relation;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.Requirement;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.SortGroup;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One row of the achievement table. Yields both halves under the same id ({@code lotrrevival:<tab>/<name>}):
 * the display entry and the backing advancement (one criterion, no display block).
 *
 * @param title English title (our own text)
 * @param description English description (our own text)
 */
public record AchievementRow(
        ResourceLocation id,
        AchievementTab tab,
        Item icon,
        SortGroup sortGroup,
        Criterion<?> criterion,
        List<Requirement> requires,
        Optional<RankRequirement> rank,
        boolean counted,
        String title,
        String description) {

    public static Builder builder(AchievementTab tab, String name) {
        return new Builder(tab, name);
    }

    /** {@code achievement.lotrrevival.<tab>.<name>}; the lang keys append {@code .title} / {@code .description}. */
    public String langBase() {
        return "achievement." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    public String titleKey() {
        return langBase() + ".title";
    }

    public String descriptionKey() {
        return langBase() + ".description";
    }

    public AchievementEntry toEntry() {
        return new AchievementEntry(
                tab,
                new ItemStack(icon),
                Component.translatable(titleKey()),
                Component.translatable(descriptionKey()),
                sortGroup,
                requires,
                rank,
                counted);
    }

    /** The backing advancement: criteria only, never a display block. */
    public Advancement toAdvancement() {
        return Advancement.Builder.advancement().addCriterion("c", criterion).build(id).value();
    }

    public AchievementRow withId(ResourceLocation newId) {
        return new AchievementRow(newId, tab, icon, sortGroup, criterion, requires, rank, counted, title, description);
    }

    public AchievementRow withTab(AchievementTab newTab) {
        return new AchievementRow(id, newTab, icon, sortGroup, criterion, requires, rank, counted, title, description);
    }

    public AchievementRow withRequires(List<Requirement> newRequires) {
        return new AchievementRow(id, tab, icon, sortGroup, criterion, newRequires, rank, counted, title, description);
    }

    public AchievementRow withRank(RankRequirement newRank) {
        return new AchievementRow(
                id, tab, icon, sortGroup, criterion, List.of(), Optional.of(newRank), counted, title, description);
    }

    /** Fluent builder: {@code (tab, name, icon, sort_group, criterion, requires? | rank?, counted?)} plus text. */
    public static final class Builder {
        private final AchievementTab tab;
        private final String name;
        private Item icon;
        private SortGroup sortGroup = SortGroup.OTHER;
        private Criterion<?> criterion;
        private List<Requirement> requires = List.of();
        private Optional<RankRequirement> rank = Optional.empty();
        private boolean counted = true;
        private String title;
        private String description;

        private Builder(AchievementTab tab, String name) {
            this.tab = tab;
            this.name = name;
        }

        public Builder icon(Item icon) {
            this.icon = icon;
            return this;
        }

        public Builder sortGroup(SortGroup sortGroup) {
            this.sortGroup = sortGroup;
            return this;
        }

        public Builder criterion(Criterion<?> criterion) {
            this.criterion = criterion;
            return this;
        }

        /** Adds one requirement; any one of {@code factions} satisfies it. */
        @SafeVarargs
        public final Builder requires(Relation relation, ResourceKey<Faction>... factions) {
            List<Requirement> list = new ArrayList<>(requires);
            list.add(new Requirement(relation, List.of(factions)));
            this.requires = List.copyOf(list);
            return this;
        }

        public Builder rank(ResourceKey<Faction> faction, String rankId) {
            this.rank = Optional.of(new RankRequirement(faction, rankId));
            return this;
        }

        public Builder counted(boolean counted) {
            this.counted = counted;
            return this;
        }

        public Builder text(String title, String description) {
            this.title = title;
            this.description = description;
            return this;
        }

        public AchievementRow build() {
            if (icon == null || criterion == null || title == null || description == null) {
                throw new IllegalStateException(
                        "Achievement row " + tab.id() + "/" + name + " needs icon, criterion and text");
            }
            if (!requires.isEmpty() && rank.isPresent()) {
                throw new IllegalStateException(
                        "Achievement row " + tab.id() + "/" + name + ": requires and rank are mutually exclusive");
            }
            return new AchievementRow(
                    LOTRRevival.id(tab.id() + "/" + name),
                    tab,
                    icon,
                    sortGroup,
                    criterion,
                    requires,
                    rank,
                    counted,
                    title,
                    description);
        }
    }
}
