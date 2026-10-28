package io.github.sethwillemsvg.lotrrevival.npc.datagen;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.item.NpcItems;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** NPC lang entries, called from core's {@code ModLangProvider} (only one en_us provider can exist). */
public final class NpcLang {
    private NpcLang() {}

    public static void addTo(LanguageProvider lang) {
        lang.addEntityType(NpcEntityTypes.HOBBIT, "Hobbit");
        lang.addItem(NpcItems.HOBBIT_SPAWN_EGG, "Hobbit Spawn Egg");
        // The villager role has no title.
        lang.add("npc.lotrrevival.role.farmer", "Farmer");
        lang.add("npc.lotrrevival.role.innkeeper", "Innkeeper");
        lang.add("npc.lotrrevival.role.traveller", "Traveller");
    }
}
