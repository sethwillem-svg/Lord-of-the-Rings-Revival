package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the rohan tab. Faction rank entries and first-kill entries. */
public final class RohanAchievements extends AchievementTabProvider {
    public RohanAchievements() {
        super(AchievementTab.ROHAN);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        AchievementFactions.addRankRows(AchievementTab.ROHAN, out);
        AchievementFactions.addKillRows(AchievementTab.ROHAN, out);
    }
}
