package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the gondor tab. Faction rank entries and first-kill entries. */
public final class GondorAchievements extends AchievementTabProvider {
    public GondorAchievements() {
        super(AchievementTab.GONDOR);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        AchievementFactions.addRankRows(AchievementTab.GONDOR, out);
        AchievementFactions.addKillRows(AchievementTab.GONDOR, out);
    }
}
