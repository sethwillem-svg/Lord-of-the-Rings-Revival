package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the eriador tab. Empty until its content ticket. */
public final class EriadorAchievements extends AchievementTabProvider {
    public EriadorAchievements() {
        super(AchievementTab.ERIADOR);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        // Content tickets add rows here.
    }
}
