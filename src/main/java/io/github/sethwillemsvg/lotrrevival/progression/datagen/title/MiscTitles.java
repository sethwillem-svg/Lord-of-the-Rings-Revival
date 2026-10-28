package io.github.sethwillemsvg.lotrrevival.progression.datagen.title;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Starter, special and achievement titles. */
final class MiscTitles {
    private MiscTitles() {}

    static final int STARTER_COUNT = 31;
    static final int SPECIAL_COUNT = 12;

    /** Starter titles: always usable. */
    private static final String[] STARTER = {
        "Wanderer", "Road-walker", "Hearth-seeker", "Stargazer", "Lantern-bearer", "Footsore Traveller",
        "Map-maker", "Pathfinder", "River-crosser", "Hill-climber", "Forest-dweller", "Moor-walker",
        "Marsh-wader", "Cave-delver", "Stone-gatherer", "Tinkerer", "Fire-tender", "Bread-baker",
        "Pipe-smoker", "Song-singer", "Tale-teller", "Horn-blower", "Bow-bearer", "Shield-bearer",
        "Sword-wielder", "Torch-bearer", "Messenger", "Seeker of Tales", "Bold Beginner", "Keeper of Secrets",
        "Ford-finder",
    };

    /** Special titles: granted by command or code; placeholders for playtesters and contests. */
    private static final String[] SPECIAL = {
        "Playtester", "Early Wanderer", "Bug Hunter", "Contest Champion", "Contest Runner-up", "Contest Finalist",
        "Master Builder", "Banner-weaver", "Storyteller", "Lorekeeper", "Festival Guest", "Council Member",
    };

    /**
     * Achievement titles, keyed by achievement id path ({@code <tab>/<name>}). The title id is
     * {@code achievement/<name>}. Rank achievements get no title here: the rank titles cover them.
     */
    private static final Map<String, String> ACHIEVEMENT = new LinkedHashMap<>();

    private static void achievement(String achievement, String text) {
        ACHIEVEMENT.put(achievement, text);
    }

    static {
        achievement("general/enter_middle_earth", "Threshold-crosser");
        achievement("general/explorer_10", "Road-wise Roamer");
        achievement("general/explorer_20", "Leagues Behind Me");
        achievement("general/explorer_30", "Walker of Many Roads");
        achievement("general/explorer_40", "Atlas-minded");
        achievement("general/explorer_50", "Lord of the Wide Map");
        achievement("general/first_pledge_rank", "Oath-ready");
        achievement("general/first_standing", "Known by Name");
        achievement("general/free_peoples_friend", "Voice for the Free");
        achievement("general/shadow_friend", "Dabbler in Shadows");
        achievement("combat/slay_dwarf", "Delver's Bane");
        achievement("combat/slay_elf", "Starlight's Bane");
        achievement("combat/slay_orc", "Orc-bane");
        achievement("combat/slay_tree", "Lumberjack of Arda");
        achievement("combat/slay_troll", "Troll-toppler");
        achievement("shire/kill_hobbit", "Shire-breaker");
        achievement("eriador/kill_bree", "Breeland Brawler");
        achievement("eriador/kill_dunedain_north", "Ranger-bane");
        achievement("eriador/kill_blue_mountains", "Ered Luin Raider");
        achievement("eriador/kill_high_elf", "Lindon Foe");
        achievement("eriador/kill_angmar", "Frost-breaker");
        achievement("eriador/kill_dunland", "Hillfolk Foe");
        achievement("misty_mountains/kill_gundabad", "Peak Conqueror");
        achievement("rhovanion/kill_wood_elf", "Canopy Hunter");
        achievement("rhovanion/kill_dol_guldur", "Shadow-slipper");
        achievement("rhovanion/kill_dale", "Silencer of Bells");
        achievement("rhovanion/kill_durins_folk", "Longbeard Foe");
        achievement("rhovanion/kill_dorwinion", "Vineyard Raider");
        achievement("lothlorien/kill_lothlorien", "Mallorn-breaker");
        achievement("rohan/kill_rohan", "Horse-lord Foe");
        achievement("rohan/kill_isengard", "Forge-quencher");
        achievement("rohan/kill_fangorn", "Fell-wood Axeman");
        achievement("gondor/kill_gondor", "Citadel Besieger");
        achievement("mordor/kill_mordor", "Ember in the Ash");
        achievement("south_east/kill_easterling", "Banner-ripper");
        achievement("south_east/kill_near_harad", "Spear-breaker");
        achievement("south_east/kill_far_harad", "Burning-sands Survivor");
        achievement("south_east/kill_harad_jungle", "Green-heat Survivor");
        achievement("south_east/kill_half_troll", "Giant-felled");
    }

    /** Achievement id paths that have a title row. */
    static java.util.Set<String> achievementPaths() {
        return ACHIEVEMENT.keySet();
    }

    static void addRows(List<TitleRow> rows) {
        if (STARTER.length != STARTER_COUNT) {
            throw new IllegalStateException("Expected " + STARTER_COUNT + " starter titles, found " + STARTER.length);
        }
        if (SPECIAL.length != SPECIAL_COUNT) {
            throw new IllegalStateException("Expected " + SPECIAL_COUNT + " special titles, found " + SPECIAL.length);
        }
        for (String text : STARTER) {
            rows.add(new TitleRow(
                    "starter/" + FactionTitles.slug(text), text, null, TitleSource.Starter.INSTANCE, false));
        }
        for (String text : SPECIAL) {
            rows.add(new TitleRow(
                    "special/" + FactionTitles.slug(text), text, null, TitleSource.Special.INSTANCE, true));
        }
        ACHIEVEMENT.forEach((achievement, text) -> rows.add(new TitleRow(
                "achievement/" + achievement.substring(achievement.indexOf('/') + 1),
                text,
                null,
                new TitleSource.AchievementSource(LOTRRevival.id(achievement)),
                false)));
    }
}
