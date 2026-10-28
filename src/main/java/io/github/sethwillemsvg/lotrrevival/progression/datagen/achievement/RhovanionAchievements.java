package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the rhovanion tab. Faction rank entries and first-kill entries. */
public final class RhovanionAchievements extends AchievementTabProvider {
    public RhovanionAchievements() {
        super(AchievementTab.RHOVANION);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        AchievementFactions.addRankRows(AchievementTab.RHOVANION, out);
        AchievementFactions.addKillRows(AchievementTab.RHOVANION, out);
    }
}
