package io.github.sethwillemsvg.lotrrevival.npc.datagen;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.item.NpcItems;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeechLang;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** NPC lang entries, called from core's {@code ModLangProvider} (only one en_us provider can exist). */
public final class NpcLang {
    private NpcLang() {}

    public static void addTo(LanguageProvider lang) {
        lang.addEntityType(NpcEntityTypes.HOBBIT, "Hobbit");
        lang.addItem(NpcItems.HOBBIT_SPAWN_EGG, "Hobbit Spawn Egg");
        // Shown instead of a generated name when a race has no name bank.
        lang.add("npc.lotrrevival.race.hobbit", "Hobbit");
        lang.add("npc.lotrrevival.race.man", "Man");
        // Name followed by role title.
        lang.add("npc.lotrrevival.titled_name", "%1$s, %2$s");
        NpcSpeechLang.addTo(lang);
        // The villager role has no title.
        lang.add("npc.lotrrevival.role.farmer", "Farmer");
        lang.add("npc.lotrrevival.role.innkeeper", "Innkeeper");
        lang.add("npc.lotrrevival.role.traveller", "Traveller");
    }
}
