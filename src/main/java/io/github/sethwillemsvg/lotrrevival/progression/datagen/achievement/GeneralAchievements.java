package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.SortGroup;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.DistinctBiomesTrigger;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.ProgressionTriggers;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.advancements.critereon.ChangeDimensionTrigger;
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

        explorer(out, 10, Items.LEATHER_BOOTS, "Wayfarer", "Set foot in 10 different biomes.");
        explorer(out, 20, Items.CHAINMAIL_BOOTS, "Far Wanderer", "Set foot in 20 different biomes.");
        explorer(out, 30, Items.IRON_BOOTS, "Long Road Walker", "Set foot in 30 different biomes.");
        explorer(out, 40, Items.GOLDEN_BOOTS, "Lands-Lore Keeper", "Set foot in 40 different biomes.");
        explorer(out, 50, Items.DIAMOND_BOOTS, "Master of the Wide World", "Set foot in 50 different biomes.");
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
