package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the misty_mountains tab. Faction rank entries and first-kill entries. */
public final class MistyMountainsAchievements extends AchievementTabProvider {
    public MistyMountainsAchievements() {
        super(AchievementTab.MISTY_MOUNTAINS);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        AchievementFactions.addRankRows(AchievementTab.MISTY_MOUNTAINS, out);
        AchievementFactions.addKillRows(AchievementTab.MISTY_MOUNTAINS, out);
    }
}
