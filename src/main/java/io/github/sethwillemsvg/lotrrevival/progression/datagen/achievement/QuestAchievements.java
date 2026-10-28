package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.SortGroup;
import io.github.sethwillemsvg.lotrrevival.progression.datagen.quest.QuestOfferContent;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.CompleteMiniQuestTrigger;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.ProgressionTriggers;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.world.item.Items;

/** Achievement rows for quest groups' first completions (one row per group, in the tab of the group's people). */
public final class QuestAchievements {
    private QuestAchievements() {}

    /**
     * The Shire tab's quest rows. The criterion matches the group, so the award also works through the normal
     * trigger flow; the quest system additionally grants it directly when the quest completes.
     */
    public static void addShireRows(Function<String, AchievementRow.Builder> row, Consumer<AchievementRow> out) {
        out.accept(row.apply("first_hobbit_errand")
                .icon(Items.BREAD)
                .sortGroup(SortGroup.OTHER)
                .criterion(ProgressionTriggers.COMPLETE_MINI_QUEST
                        .get()
                        .createCriterion(new CompleteMiniQuestTrigger.TriggerInstance(
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(QuestOfferContent.HOBBITS.location()),
                                Optional.empty(),
                                Optional.empty())))
                .text("A Hobbit's Errand", "Complete an errand for a hobbit of the Shire.")
                .build());
    }
}
