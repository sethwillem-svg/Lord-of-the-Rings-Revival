package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the south_east tab. Empty until its content ticket. */
public final class SouthEastAchievements extends AchievementTabProvider {
    public SouthEastAchievements() {
        super(AchievementTab.SOUTH_EAST);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        // Content tickets add rows here.
    }
}
