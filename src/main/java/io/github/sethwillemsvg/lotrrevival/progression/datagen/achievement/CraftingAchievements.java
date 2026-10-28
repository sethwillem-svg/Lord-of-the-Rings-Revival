package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the crafting tab. Empty until its content ticket. */
public final class CraftingAchievements extends AchievementTabProvider {
    public CraftingAchievements() {
        super(AchievementTab.CRAFTING);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        // Content tickets add rows here.
    }
}
