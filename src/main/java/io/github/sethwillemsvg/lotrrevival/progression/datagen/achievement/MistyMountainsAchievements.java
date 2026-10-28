package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the misty_mountains tab. Empty until its content ticket. */
public final class MistyMountainsAchievements extends AchievementTabProvider {
    public MistyMountainsAchievements() {
        super(AchievementTab.MISTY_MOUNTAINS);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        // Content tickets add rows here.
    }
}
