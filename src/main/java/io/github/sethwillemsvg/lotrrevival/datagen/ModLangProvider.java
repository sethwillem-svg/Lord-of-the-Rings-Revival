package io.github.sethwillemsvg.lotrrevival.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** English (en_us) lang entries. Replaces the hand-written lang file. */
final class ModLangProvider extends LanguageProvider {
    ModLangProvider(PackOutput output) {
        super(output, LOTRRevival.MOD_ID, "en_us");
    }

    private static final Map<String, String> FACTION_NAMES = new LinkedHashMap<>();

    static {
        FACTION_NAMES.put("hobbit", "Hobbits");
        FACTION_NAMES.put("bree", "Bree-land");
        FACTION_NAMES.put("dunedain_north", "Dunedain of the North");
        FACTION_NAMES.put("blue_mountains", "Blue Mountains");
        FACTION_NAMES.put("high_elf", "High Elves");
        FACTION_NAMES.put("gundabad", "Gundabad");
        FACTION_NAMES.put("angmar", "Angmar");
        FACTION_NAMES.put("wood_elf", "Woodland Realm");
        FACTION_NAMES.put("dol_guldur", "Dol Guldur");
        FACTION_NAMES.put("dale", "Dale");
        FACTION_NAMES.put("durins_folk", "Durin's Folk");
        FACTION_NAMES.put("lothlorien", "Lothlorien");
        FACTION_NAMES.put("dunland", "Dunland");
        FACTION_NAMES.put("isengard", "Isengard");
        FACTION_NAMES.put("fangorn", "Fangorn");
        FACTION_NAMES.put("rohan", "Rohan");
        FACTION_NAMES.put("gondor", "Gondor");
        FACTION_NAMES.put("mordor", "Mordor");
        FACTION_NAMES.put("dorwinion", "Dorwinion");
        FACTION_NAMES.put("easterling", "Easterlings");
        FACTION_NAMES.put("near_harad", "Near Harad");
        FACTION_NAMES.put("far_harad", "Far Harad");
        FACTION_NAMES.put("harad_jungle", "Southern Jungle Tribes");
        FACTION_NAMES.put("half_troll", "Half-trolls");
        FACTION_NAMES.put("utumno", "Utumno");
        FACTION_NAMES.put("hostile", "Hostile");
        FACTION_NAMES.put("unaligned", "Unaligned");
        FACTION_NAMES.put("hostile_trees", "Hostile Trees");
        FACTION_NAMES.put("ruffians", "Ruffians");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.lotrrevival", "The Lord of the Rings: Revival");

        for (var key : Factions.REAL) {
            check(key.location().getPath());
        }
        for (var key : Factions.PSEUDO) {
            check(key.location().getPath());
        }
        FACTION_NAMES.forEach((id, name) -> add("faction.lotrrevival." + id, name));

        add("region.lotrrevival.westlands", "Westlands");
        add("region.lotrrevival.rhun", "Rhun");
        add("region.lotrrevival.harad", "Harad");
        add("region.lotrrevival.deeps", "The Deeps");

        add("relation.lotrrevival.ally", "Ally");
        add("relation.lotrrevival.friend", "Friend");
        add("relation.lotrrevival.neutral", "Neutral");
        add("relation.lotrrevival.enemy", "Enemy");
        add("relation.lotrrevival.mortal_enemy", "Mortal enemy");

        add("commands.lotrrevival.faction.list.header", "Factions:");
        add("commands.lotrrevival.faction.list.region", "%s:");
        add("commands.lotrrevival.faction.relation", "%s and %s: %s");
        add("commands.lotrrevival.faction.unknown", "Unknown faction: %s");
    }

    private static void check(String id) {
        if (!FACTION_NAMES.containsKey(id)) {
            throw new IllegalStateException("Missing faction display name for " + id);
        }
    }
}
