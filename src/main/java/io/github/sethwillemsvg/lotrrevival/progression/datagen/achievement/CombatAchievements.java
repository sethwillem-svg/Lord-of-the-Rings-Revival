package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the combat tab. Empty until its content ticket. */
public final class CombatAchievements extends AchievementTabProvider {
    public CombatAchievements() {
        super(AchievementTab.COMBAT);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        // Content tickets add rows here.
    }
}
