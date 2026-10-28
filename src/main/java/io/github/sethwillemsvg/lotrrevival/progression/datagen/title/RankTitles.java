package io.github.sethwillemsvg.lotrrevival.progression.datagen.title;

import io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement.AchievementFactions;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rank titles: one per listed faction x rank of its ladder. Each entry is {@code "text"} or
 * {@code "text|feminine text"}, in ladder order (r1 first). Factions are keyed by their path (as in
 * {@link AchievementFactions}); the array length must equal the ladder size there, which is checked when the
 * rows are built.
 */
final class RankTitles {
    private RankTitles() {}

    private static final Map<String, String[]> LADDERS = new LinkedHashMap<>();

    private static void ladder(String faction, String... titles) {
        LADDERS.put(faction, titles);
    }

    static {
        ladder("hobbit", "Passing Stranger", "Welcome Guest", "Hearth-friend", "Honoured Neighbour",
                "Friend of the Shire", "Smial-elder", "Master of the Hill|Mistress of the Hill");
        ladder("bree", "Roadside Visitor", "Inn Guest", "Regular at the Pony", "Trusted Trader", "Friend of Bree-land",
                "Councillor of Bree", "Warden of the Hedge");
        ladder("dunedain_north", "Unknown Traveller", "Tolerated Wayfarer", "Friend of the Rangers",
                "Scout of the North", "Ranger of the Wild", "Shield-brother of the North|Shield-sister of the North",
                "Keeper of Arnor's Flame");
        ladder("blue_mountains", "Surface Stranger", "Guest of the Halls", "Pickaxe Apprentice", "Delver of Nogrod",
                "Smith of Belegost", "Beard of Renown", "Lord of the Deep Forge|Lady of the Deep Forge",
                "Thane of Ered Luin");
        ladder("high_elf", "Mortal Visitor", "Guest of Mithlond", "Friend of the Havens", "Wanderer of Lindon",
                "Lore-friend of Imladris", "Elf-friend", "Herald of Gil-galad", "High Warden of Lindon");
        ladder("angmar", "Frozen Scavenger", "Camp Follower", "Carn Dum Recruit", "Frost-reaver", "Snow-wolf Rider",
                "Captain of the Cold Host", "Hand of the Witch-king");
        ladder("dunland", "Lowland Stranger", "Hill Visitor", "Kinsman by Oath", "Clan Spear", "Wolf-pelt Warrior",
                "Voice of the Chieftain", "Chieftain of the Hills|Chieftainess of the Hills");
        ladder("gundabad", "Tunnel Rat", "Torch-bearer", "Whip-fodder", "Cave Skirmisher", "Axe of Gundabad",
                "Warband Boss", "Warlord of Gundabad");
        ladder("wood_elf", "Lost Trespasser", "Tolerated Guest", "Friend of the Greenwood", "Archer of the Wood",
                "Warden of the Halls", "Captain of the Glade", "Counsellor of Thranduil");
        ladder("dol_guldur", "Web-snared Fool", "Lurking Spy", "Shadow's Scout", "Spider-tamer", "Sworn of the Hill",
                "Captain of the Dark Hold", "Hand of the Necromancer");
        ladder("dale", "Foreign Visitor", "Market Guest", "Friend of Dale", "Guardsman of Dale", "Bowman of the Lake",
                "Captain of the Bells", "Lord of the Bells|Lady of the Bells");
        ladder("durins_folk", "Outlander", "Guest at the Gate", "Mattock Hand", "Stonecutter of Erebor",
                "Forge-sworn", "Axe of Durin", "Hall-marshal of Erebor", "Heir of the Longbeards");
        ladder("dorwinion", "River Stranger", "Cellar Guest", "Grape-picker", "Cask Hand", "Vintner's Friend",
                "Vineyard Warden", "Master of the Press", "Lord of the Vintage|Lady of the Vintage");
        ladder("lothlorien", "Unwelcome Wanderer", "Blindfolded Guest", "Listener at the Gate",
                "Friend of the Galadhrim", "Watcher of the Mallorn", "Marchwarden of the Wood",
                "Keeper of the Golden Wood", "Guardian of Caras Galadhon");
        ladder("rohan", "Strange Wanderer", "Guest of the Mark", "Stable-friend",
                "Spearman of the Mark|Spear-maiden of the Mark", "Rider of the Mark", "Marshal of the Riddermark",
                "Thane of Edoras");
        ladder("isengard", "Orthanc Trespasser", "Wood-hauler", "Forge Hand", "Warg-handler", "White-hand Soldier",
                "Uruk-hai Captain", "Pit-master of Isengard", "Voice of Saruman");
        ladder("fangorn", "Leaf-trampler", "Quiet Walker", "Friend of Fangorn", "Ent-friend", "Treebeard's Companion");
        ladder("gondor", "Foreign Visitor", "Gate Guest", "Citizen of Gondor", "Soldier of the Citadel",
                "Guard of the Tower", "Captain of Gondor", "Steward's Counsellor",
                "Lord of the White City|Lady of the White City");
        ladder("mordor", "Ash-crawler", "Cinder Scavenger", "Gate Sentry", "Black Legion Recruit",
                "Iron Crown Soldier", "Captain of Gorgoroth", "Fist of the Eye", "Commander of the Black Land");
        ladder("easterling", "Border Stranger", "Tent Guest", "Rhun Raider", "Chariot Spearman",
                "Banner-bearer of Rhun", "Warband Captain", "Khan's Right Hand", "Warlord of the East");
        ladder("near_harad", "Desert Stranger", "Caravan Guest", "Oasis Friend", "Spearman of the Sands",
                "Scimitar of the South", "Captain of Umbar", "Chieftain of Harad|Chieftainess of Harad",
                "Prince of the Southlands|Princess of the Southlands");
        ladder("far_harad", "Unknown Voyager", "Fire-circle Guest", "Hunter of the Plains", "Beast-tamer",
                "Mumak-hand", "Elder of the Tribes", "Chief of the Far South");
        ladder("harad_jungle", "Jungle Trespasser", "Vine-walker", "Tribe Guest", "Blowpipe Hunter", "Drum-bearer",
                "Shaman's Chosen", "Chief of the Green Heat");
        ladder("half_troll", "Cave Scrap", "Boulder Lugger", "Hulk of the Clan", "Bone-crusher", "Tusk-lord",
                "Brute Champion", "Warchief of the Hulks");
    }

    static void addRows(List<TitleRow> rows) {
        for (AchievementFactions.Info info : AchievementFactions.ALL) {
            String[] titles = LADDERS.get(info.path());
            if (titles == null || titles.length != info.ranks()) {
                throw new IllegalStateException("Rank titles for " + info.path() + ": expected " + info.ranks()
                        + " entries, found " + (titles == null ? 0 : titles.length));
            }
            for (int n = 1; n <= titles.length; n++) {
                String[] parts = titles[n - 1].split("\\|", 2);
                rows.add(new TitleRow(
                        "rank/" + info.path() + "_r" + n,
                        parts[0],
                        parts.length > 1 ? parts[1] : null,
                        new TitleSource.AlignmentRank(info.key(), "r" + n),
                        false));
            }
        }
    }
}
