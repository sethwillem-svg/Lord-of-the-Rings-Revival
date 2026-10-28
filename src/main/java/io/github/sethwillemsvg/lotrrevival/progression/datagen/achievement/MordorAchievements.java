package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the mordor tab. Faction rank entries and first-kill entries. */
public final class MordorAchievements extends AchievementTabProvider {
    public MordorAchievements() {
        super(AchievementTab.MORDOR);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        AchievementFactions.addRankRows(AchievementTab.MORDOR, out);
        AchievementFactions.addKillRows(AchievementTab.MORDOR, out);
    }
}
