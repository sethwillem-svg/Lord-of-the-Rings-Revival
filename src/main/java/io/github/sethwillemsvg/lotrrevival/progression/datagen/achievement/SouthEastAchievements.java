package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the south_east tab. Faction rank entries and first-kill entries. */
public final class SouthEastAchievements extends AchievementTabProvider {
    public SouthEastAchievements() {
        super(AchievementTab.SOUTH_EAST);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        AchievementFactions.addRankRows(AchievementTab.SOUTH_EAST, out);
        AchievementFactions.addKillRows(AchievementTab.SOUTH_EAST, out);
    }
}
