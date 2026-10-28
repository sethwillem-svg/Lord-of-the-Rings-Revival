package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the lothlorien tab. Faction rank entries and first-kill entries. */
public final class LothlorienAchievements extends AchievementTabProvider {
    public LothlorienAchievements() {
        super(AchievementTab.LOTHLORIEN);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        AchievementFactions.addRankRows(AchievementTab.LOTHLORIEN, out);
        AchievementFactions.addKillRows(AchievementTab.LOTHLORIEN, out);
    }
}
