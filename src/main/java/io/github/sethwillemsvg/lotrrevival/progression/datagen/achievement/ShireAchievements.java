package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the shire tab. Empty until its content ticket. */
public final class ShireAchievements extends AchievementTabProvider {
    public ShireAchievements() {
        super(AchievementTab.SHIRE);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        // Content tickets add rows here.
    }
}
