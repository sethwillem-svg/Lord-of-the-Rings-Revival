package io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** The shared achievement table: every tab sub-provider feeds it. Content tickets add rows to a tab class. */
public final class AchievementTable {
    private AchievementTable() {}

    /** One sub-provider per tab, in tab order. */
    public static List<AchievementTabProvider> providers() {
        return List.of(
                new GeneralAchievements(),
                new ShireAchievements(),
                new EriadorAchievements(),
                new MistyMountainsAchievements(),
                new RhovanionAchievements(),
                new LothlorienAchievements(),
                new RohanAchievements(),
                new GondorAchievements(),
                new MordorAchievements(),
                new SouthEastAchievements(),
                new CombatAchievements(),
                new CraftingAchievements(),
                new TradeAchievements(),
                new TravelAchievements());
    }

    /** All rows. Built on demand because criteria need the trigger registry. */
    public static List<AchievementRow> rows() {
        List<AchievementRow> rows = new ArrayList<>();
        for (AchievementTabProvider provider : providers()) {
            provider.addRows(rows::add);
        }
        return rows;
    }

    /** Display entries keyed by id (last row wins; duplicates are rejected by the validator). */
    public static Map<ResourceLocation, io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry>
            entries(List<AchievementRow> rows) {
        Map<ResourceLocation, io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry> map =
                new LinkedHashMap<>();
        for (AchievementRow row : rows) {
            map.put(row.id(), row.toEntry());
        }
        return map;
    }
}
