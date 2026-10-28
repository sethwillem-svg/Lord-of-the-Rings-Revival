package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.function.Consumer;

/** Rows of the eriador tab. Faction rank entries and first-kill entries. */
public final class EriadorAchievements extends AchievementTabProvider {
    public EriadorAchievements() {
        super(AchievementTab.ERIADOR);
    }

    @Override
    public void addRows(Consumer<AchievementRow> out) {
        AchievementFactions.addRankRows(AchievementTab.ERIADOR, out);
        AchievementFactions.addKillRows(AchievementTab.ERIADOR, out);
    }
}
