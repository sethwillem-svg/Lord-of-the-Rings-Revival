package io.github.sethwillemsvg.lotrrevival.progression.datagen.title;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.progression.datagen.achievement.AchievementFactions;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;

/**
 * Faction alignment titles. Seven per listed faction, usable at the faction's pledge threshold; then the
 * multi-faction titles (+100 with any one listed faction) and the one any-faction title (+10,000). All have the
 * source type {@code alignment}, so all live in the {@code faction/} folder (multi-faction ids are
 * {@code faction/multi_<name>}, the any-faction id is {@code faction/any_<name>}). Factions are keyed by their
 * path, as in {@link AchievementFactions}.
 */
final class FactionTitles {
    private FactionTitles() {}

    static final int PER_FACTION = 7;

    private static final Map<String, String[]> SINGLE = new LinkedHashMap<>();

    private static void faction(String faction, String... titles) {
        SINGLE.put(faction, titles);
    }

    static {
        faction("hobbit", "Friend of the Hobbits", "Bywater Regular", "Guest of Hobbiton", "Wanderer of the Farthings",
                "Protector of the Shire", "Mathom Collector", "Keeper of the Party Field");
        faction("bree", "Friend of Bree-land", "Prancing Pony Patron", "Wayfarer of Archet", "Forester of Chetwood",
                "Guardian of the Hedge", "Keeper of the Greenway", "Watcher of the Barrow-downs");
        faction("dunedain_north", "Friend of the Rangers", "Stranger-guard of Eriador", "Warden of the Wild",
                "Keeper of Arnor's Memory", "Scout of the Weather Hills", "Bearer of the Northern Star",
                "Heir's Sworn Companion");
        faction("blue_mountains", "Friend of the Blue Mountains", "Delver of Ered Luin", "Smith of Nogrod",
                "Axe of Belegost", "Deep-forge Keeper", "Rune-reader of Thorin's Halls", "Warden of the Western Gate");
        faction("high_elf", "Friend of the High Elves", "Guest of Mithlond", "Envoy of Lindon",
                "Star-gazer of the Havens", "Lorekeeper of Imladris", "Sea-longing Wanderer",
                "Keeper of the Grey Ships");
        faction("gundabad", "Servant of Gundabad", "Mountain Marauder", "Warren Boss", "Pass Ambusher",
                "Bane of Dwarf-roads", "Skull-banner Bearer", "Master of the Deep Warrens");
        faction("angmar", "Servant of Angmar", "Carn Dum Sentinel", "Frost-raider", "Hill-fort Lord",
                "Wraith's Herald", "Witch-king's Fist", "Breaker of Arthedain");
        faction("wood_elf", "Friend of the Wood-elves", "Woodland Archer", "Guest of the Elvenking",
                "Lantern-bearer of the Wood", "Warden of the Old Forest Road", "Spider-slayer of Mirkwood",
                "Keeper of the Feasting Halls");
        faction("dol_guldur", "Servant of Dol Guldur", "Spider-brood Master", "Pit-warden",
                "Whisperer of the Dark Hill", "Blight-bringer", "Hand of the Necromancer",
                "Seeker of the Hidden Fire");
        faction("dale", "Friend of Dale", "Toymaker's Friend", "Bowman of Esgaroth", "Bellringer of Dale",
                "Descendant of Girion", "Warden of the Long Lake", "Dragon-wary Defender");
        faction("durins_folk", "Friend of Durin's Folk", "Guest of Erebor", "Mattock-sworn", "Seeker of Mithril Veins",
                "Keeper of Thror's Memory", "Guardian of the Lonely Mountain", "Shield of the Longbeards");
        faction("lothlorien", "Friend of the Golden Wood", "Guest of the Galadhrim", "Mallorn Climber",
                "Talan Dweller", "Singer of Nimrodel", "Protector of Caras Galadhon", "Light-bearer of the Lady");
        faction("dunland", "Friend of Dunland", "Hill Kinsman", "Wolf-clan Warrior", "Avenger of Old Grudges",
                "Voice of the Hill Clans", "Raider of the Isen Fords", "Lord of the Dun Hills");
        faction("isengard", "Servant of Isengard", "Ring-wall Guard", "Orthanc Sentinel", "Warg-rider of the Gap",
                "Forge-master", "Voice of the White Hand", "White-hand Scout");
        faction("fangorn", "Friend of Fangorn", "Quiet Wood-walker", "Huorn-wary Traveller", "Ent-draught Taster",
                "Keeper of Old Trees", "Shepherd of Saplings", "Guardian of the Entwash");
        faction("rohan", "Friend of Rohan", "Guest of Meduseld", "Horse-friend of the Mark", "Rider of the Eastfold",
                "Defender of the Westfold", "Hornburg Guardian", "Sworn of Edoras");
        faction("gondor", "Friend of Gondor", "Guest of Minas Tirith", "Watcher of the White Tower",
                "Ranger of Ithilien", "Defender of Osgiliath", "Swan Knight of Dol Amroth", "Shield of the Steward");
        faction("mordor", "Servant of Mordor", "Gorgoroth Marcher", "Watcher of the Black Gate", "Smith of Barad-dur",
                "Ash-lands Overseer", "Banner of the Red Eye", "Voice of the Dark Tower");
        faction("dorwinion", "Friend of Dorwinion", "Vineyard Guest", "Taster of the Great Vintage",
                "Keeper of the Wine-cellars", "Barrel-master of Dorwinion", "Courier of the River Running",
                "Guardian of the Grape-road");
        faction("easterling", "Friend of the Easterlings", "Guest of Rhun's Tents", "Wain-driver",
                "Banner-sworn of Rhun", "Rider of the Eastern Plains", "Border Conqueror", "Shield of the Great Khan");
        faction("near_harad", "Friend of Near Harad", "Caravan Escort", "Sand-spear of Harad", "Oasis Guardian",
                "Corsair of Umbar", "Scimitar of the Sun", "Vizier of the Southlands");
        faction("far_harad", "Friend of Far Harad", "Savannah Tracker", "Beast-herd Guide", "Spear of the Tribes",
                "Rider of the Great Beasts", "Drum-keeper of the South", "Elder of the Burning Lands");
        faction("harad_jungle", "Friend of the Jungle Tribes", "Vine-path Walker", "Totem-bearer", "Blowgun Hunter",
                "Mask-dancer", "Keeper of the Green Shrines", "Guardian of the Canopy");
        faction("half_troll", "Friend of the Half-trolls", "Bone-breaker", "Cave Bully", "Boulder-hurler",
                "Warband Brute", "Troll-blood Champion", "Terror of the Hills");
    }

    private record Multi(String text, List<String> factions) {}

    private static final List<Multi> MULTI = List.of(
            multi("Friend of the Free Peoples", "hobbit", "bree", "dunedain_north", "blue_mountains", "high_elf",
                    "wood_elf", "dale", "durins_folk", "lothlorien", "rohan", "gondor"),
            multi("Servant of the Shadow", "mordor", "angmar", "isengard", "gundabad", "dol_guldur"),
            multi("Friend of the Elven Realms", "high_elf", "wood_elf", "lothlorien"),
            multi("Friend of Dwarf-halls", "blue_mountains", "durins_folk"),
            multi("Horse-wise Wanderer", "rohan", "easterling", "dunland"),
            multi("Eriador Wayfarer", "hobbit", "bree", "dunedain_north", "blue_mountains", "high_elf"),
            multi("Greenwood Wayfarer", "wood_elf", "dale", "durins_folk", "dorwinion"),
            multi("Child of the Rising Sun", "easterling", "dorwinion"),
            multi("Southron Wayfarer", "near_harad", "far_harad", "harad_jungle"),
            multi("Ally of the West", "gondor", "rohan"),
            multi("Shield Against the Dark", "gondor", "dunedain_north", "rohan", "high_elf"),
            multi("Banner of the Northern Hosts", "angmar", "gundabad"),
            multi("Bearer of Hand and Eye", "mordor", "isengard"),
            multi("Friend of the Deep Forests", "fangorn", "wood_elf", "lothlorien"),
            multi("Child of the Mountains", "gundabad", "blue_mountains", "durins_folk"),
            multi("Friend of the Hill Peoples", "dunland", "angmar"),
            multi("Ally of the Dark Lord", "mordor", "easterling", "near_harad", "far_harad", "harad_jungle",
                    "half_troll"),
            multi("Wanderer of Wilderland", "dale", "wood_elf", "durins_folk", "dol_guldur"),
            multi("Warden of the Old Roads", "bree", "dunedain_north", "rohan"),
            multi("Trader of Many Lands", "bree", "dale", "dorwinion", "near_harad", "gondor"),
            multi("Tamer of Great Beasts", "far_harad", "harad_jungle", "half_troll"),
            multi("Walker of Ruined Lands", "dol_guldur", "angmar", "mordor"),
            multi("Keeper of the Old Alliance", "high_elf", "gondor", "dunedain_north"),
            multi("Friend of the Northern Lands", "dale", "durins_folk", "wood_elf", "dunedain_north", "hobbit"),
            multi("Friend of Hearth and Horse", "hobbit", "rohan"));

    private static Multi multi(String text, String... factions) {
        return new Multi(text, List.of(factions));
    }

    static void addRows(List<TitleRow> rows) {
        for (AchievementFactions.Info info : AchievementFactions.ALL) {
            String[] titles = SINGLE.get(info.path());
            if (titles == null || titles.length != PER_FACTION) {
                throw new IllegalStateException("Faction titles for " + info.path() + ": expected " + PER_FACTION
                        + " entries, found " + (titles == null ? 0 : titles.length));
            }
            for (String text : titles) {
                rows.add(new TitleRow(
                        "faction/" + info.path() + "_" + slug(text),
                        text,
                        null,
                        new TitleSource.FactionAlignment(List.of(info.key()), Optional.empty()),
                        false));
            }
        }
        for (Multi multi : MULTI) {
            rows.add(new TitleRow(
                    "faction/multi_" + slug(multi.text()),
                    multi.text(),
                    null,
                    new TitleSource.FactionAlignment(keys(multi.factions()), Optional.of(100f)),
                    false));
        }
        List<ResourceKey<Faction>> all = new ArrayList<>();
        AchievementFactions.ALL.forEach(info -> all.add(info.key()));
        rows.add(new TitleRow(
                "faction/any_honoured_across_middle_earth",
                "Honoured Across Middle-earth",
                null,
                new TitleSource.FactionAlignment(all, Optional.of(10000f)),
                false));
    }

    private static List<ResourceKey<Faction>> keys(List<String> paths) {
        List<ResourceKey<Faction>> keys = new ArrayList<>();
        for (String path : paths) {
            keys.add(AchievementFactions.ALL.stream()
                    .filter(info -> info.path().equals(path))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unknown faction in title table: " + path))
                    .key());
        }
        return keys;
    }

    static String slug(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
