package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.SortGroup;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.DistinctBiomesTrigger;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.ProgressionTriggers;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.ChangeDimensionTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Rows of the general tab: first entry to Middle-earth and the explorer milestones. */
public final class GeneralAchievements extends AchievementTabProvider {
    public GeneralAchievements() {
        super(AchievementTab.GENERAL);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        out.accept(row("enter_middle_earth")
                .icon(Items.COMPASS)
                .sortGroup(SortGroup.SPECIAL)
                .criterion(ChangeDimensionTrigger.TriggerInstance.changedDimensionTo(ModDimensions.MIDDLE_EARTH))
                .text("Over the Threshold", "Step into Middle-earth for the first time.")
                .build());

        // Cross-faction standing: one criterion per faction, any one (or all of a chosen set) completes the entry.
        AchievementRow.Builder firstStanding = row("first_standing")
                .icon(Items.WRITABLE_BOOK)
                .sortGroup(SortGroup.SPECIAL)
                .text("A Name Worth Knowing", "Earn your first rank of standing with any people of Middle-earth.");
        AchievementRow.Builder firstPledge = row("first_pledge_rank")
                .icon(Items.WHITE_BANNER)
                .sortGroup(SortGroup.SPECIAL)
                .text("Ready to Swear", "Reach the standing at which you may pledge yourself to any faction.");
        anyOf(firstStanding, info -> "r1");
        anyOf(firstPledge, AchievementFactions.Info::pledgeRank);
        out.accept(firstStanding.build());
        out.accept(firstPledge.build());

        out.accept(allOf(
                        row("free_peoples_friend")
                                .icon(Items.GOLDEN_APPLE)
                                .sortGroup(SortGroup.SPECIAL)
                                .text(
                                        "Friend of the Free",
                                        "Earn a rank with the Hobbits, High Elves, Durin's Folk, Rohan and Gondor."),
                        Factions.HOBBIT, Factions.HIGH_ELF, Factions.DURINS_FOLK, Factions.ROHAN, Factions.GONDOR)
                .build());
        out.accept(allOf(
                        row("shadow_friend")
                                .icon(Items.WITHER_ROSE)
                                .sortGroup(SortGroup.SPECIAL)
                                .text(
                                        "Many Banners of Shadow",
                                        "Earn a rank with Gundabad, Angmar, Isengard and Mordor."),
                        Factions.GUNDABAD, Factions.ANGMAR, Factions.ISENGARD, Factions.MORDOR)
                .build());

        explorer(out, 10, Items.LEATHER_BOOTS, "Wayfarer", "Set foot in 10 different biomes.");
        explorer(out, 20, Items.CHAINMAIL_BOOTS, "Far Wanderer", "Set foot in 20 different biomes.");
        explorer(out, 30, Items.IRON_BOOTS, "Long Road Walker", "Set foot in 30 different biomes.");
        explorer(out, 40, Items.GOLDEN_BOOTS, "Lands-Lore Keeper", "Set foot in 40 different biomes.");
        explorer(out, 50, Items.DIAMOND_BOOTS, "Master of the Wide World", "Set foot in 50 different biomes.");
    }

    /** Any one listed faction reaching the given rank completes the entry. */
    private static void anyOf(AchievementRow.Builder builder, Function<AchievementFactions.Info, String> rankOf) {
        boolean first = true;
        for (AchievementFactions.Info info : AchievementFactions.ALL) {
            Criterion<?> criterion = AchievementFactions.rankCriterion(info.key(), rankOf.apply(info));
            if (first) {
                builder.criterion(criterion);
                first = false;
            } else {
                builder.alsoCriterion(criterion);
            }
        }
        builder.anyCriterion();
    }

    /** Reaching rank 1 with every given faction completes the entry. */
    @SafeVarargs
    private static AchievementRow.Builder allOf(AchievementRow.Builder builder, ResourceKey<Faction>... factions) {
        for (int n = 0; n < factions.length; n++) {
            Criterion<?> criterion = AchievementFactions.rankCriterion(factions[n], "r1");
            if (n == 0) {
                builder.criterion(criterion);
            } else {
                builder.alsoCriterion(criterion);
            }
        }
        return builder;
    }

    private void explorer(Consumer<AchievementRow> out, int count, Item icon, String title, String description) {
        out.accept(row("explorer_" + count)
                .icon(icon)
                .sortGroup(SortGroup.SPECIAL)
                .criterion(ProgressionTriggers.DISTINCT_BIOMES
                        .get()
                        .createCriterion(new DistinctBiomesTrigger.TriggerInstance(Optional.empty(), count)))
                .text(title, description)
                .build());
    }
}
