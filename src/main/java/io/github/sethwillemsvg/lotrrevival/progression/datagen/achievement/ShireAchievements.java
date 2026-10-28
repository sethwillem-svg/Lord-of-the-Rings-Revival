package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the shire tab. Faction rank entries and first-kill entries. */
public final class ShireAchievements extends AchievementTabProvider {
    public ShireAchievements() {
        super(AchievementTab.SHIRE);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        AchievementFactions.addRankRows(AchievementTab.SHIRE, out);
        AchievementFactions.addKillRows(AchievementTab.SHIRE, out);
        QuestAchievements.addShireRows(this::row, out);
    }
}
