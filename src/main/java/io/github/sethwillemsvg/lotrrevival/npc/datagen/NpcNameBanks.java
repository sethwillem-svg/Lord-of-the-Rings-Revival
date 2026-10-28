package io.github.sethwillemsvg.lotrrevival.npc.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Source lists for {@code npc_names/}: our own Tolkien-flavoured names. Hobbit names are cosy and rural; the men of
 * Bree get Bree-style surnames (plants, places and trades of a hill town).
 */
final class NpcNameBanks {
    private NpcNameBanks() {}

    /** Bank file name (without .json) to its names. */
    static Map<String, List<String>> all() {
        Map<String, List<String>> banks = new LinkedHashMap<>();
        banks.put("hobbit_male_first", HOBBIT_MALE);
        banks.put("hobbit_female_first", HOBBIT_FEMALE);
        banks.put("hobbit_family", HOBBIT_FAMILY);
        banks.put("man_male_first", MAN_MALE);
        banks.put("man_female_first", MAN_FEMALE);
        banks.put("man_family", MAN_FAMILY);
        return banks;
    }

    private static final List<String> HOBBIT_MALE = List.of(
            "Hamlo", "Dodwin", "Fastred", "Bartolo", "Wilibald", "Tobold", "Rufus", "Ferdi", "Odo", "Marlo",
            "Heribald", "Gorbin", "Olwin", "Brandor", "Haldan", "Garrow", "Wendel", "Alfric", "Mungo", "Posco",
            "Bodo", "Sandro", "Tolman", "Cotto", "Hildegar", "Isembard", "Fortun", "Gerold", "Hugo", "Ivo",
            "Kelmo", "Lobelo", "Madoc", "Orlo", "Pimpo", "Quillo", "Rowan", "Samwin", "Theobald", "Ulfo",
            "Valdo", "Wilcome", "Yarrow", "Zacco", "Bruno", "Cedric");

    private static final List<String> HOBBIT_FEMALE = List.of(
            "Rosamund", "Pimsy", "Marigold", "Lilibet", "Primula", "Camellia", "Elfrida", "Amaranth", "Berylla",
            "Clover", "Dahlia", "Eglantine", "Fenella", "Gilly", "Hazel", "Iris", "Jessamine", "Kitty", "Lavinia",
            "Mirabel", "Nettle", "Opaline", "Poppy", "Quince", "Rosalind", "Saffron", "Tansy", "Ursula", "Verbena",
            "Willow", "Xanthe", "Bryony", "Cowslip", "Dilly", "Elanor", "Fern", "Gertie", "Hollis", "Ivy",
            "Juniper", "Lobelina", "Myrtle", "Petra", "Rue");

    private static final List<String> HOBBIT_FAMILY = List.of(
            "Applewhistle", "Underbough", "Thistlebrook", "Hollowmere", "Longfurrow", "Puddifoot", "Brambleton",
            "Cloverdown", "Dimblebrook", "Eldersby", "Fernwallow", "Goodhollow", "Hayweather", "Mossgrove",
            "Nettlewick", "Oakenfold", "Pennywhistle", "Quickwater", "Reedmarsh", "Sandyfoot", "Tanglewood",
            "Underfield", "Velvetleaf", "Wheatley", "Yarrowby", "Barleycorn", "Burrowes", "Cobblewick", "Dewberry",
            "Fallowfield", "Greenhollow", "Hobbswick", "Littlebarrow", "Merrybank");

    private static final List<String> MAN_MALE = List.of(
            "Alder", "Bertram", "Cuthbert", "Dunstan", "Edric", "Fulk", "Godric", "Hamon", "Ivor", "Jory",
            "Kenric", "Leofric", "Merrick", "Norbert", "Osric", "Perrin", "Quentin", "Ralf", "Stanwin", "Tobias",
            "Uthred", "Vance", "Walter", "Wystan", "Yorick", "Aldous", "Berold", "Cedwin", "Doran", "Elwyn",
            "Farnell", "Gilbert", "Harlan", "Isaak", "Jasper", "Kendrick", "Lambert", "Morwin", "Nicolas", "Oswin",
            "Piers", "Rolf", "Sewell", "Thaddeus", "Vincent", "Wilmot");

    private static final List<String> MAN_FEMALE = List.of(
            "Aldith", "Beatrice", "Clemence", "Dorrit", "Edda", "Freda", "Gwenna", "Hilda", "Isolde", "Joan",
            "Kerra", "Leofwyn", "Maude", "Nessa", "Orla", "Petronel", "Rhosyn", "Sybil", "Thea", "Una",
            "Viola", "Winifred", "Ysmay", "Adela", "Brenna", "Cecily", "Dilys", "Eadgyth", "Fiona", "Gytha",
            "Hester", "Imelda", "Jocelyn", "Katelin", "Lettice", "Mabyn", "Nell", "Olwen", "Philippa", "Rowena",
            "Sunniva", "Tamsin", "Verity", "Wenna");

    private static final List<String> MAN_FAMILY = List.of(
            "Heatherfield", "Pickwell", "Goatwhistle", "Appleford", "Ferndale", "Thistlewood", "Brookfield", "Hartley",
            "Barrowdown", "Mapleby", "Hedgerow", "Nettlebank", "Oldwick", "Pennyworth", "Quarrelby", "Ridgeway",
            "Stonecroft", "Tinkersby", "Underwood", "Wainwright", "Yewdale", "Marshmead", "Lanternby", "Cobbledyke",
            "Dunmere", "Eastgate", "Foxholme", "Greywater", "Hillbrow", "Ironside", "Kettleby", "Lowthorn",
            "Millbrook", "Northcombe");
}
