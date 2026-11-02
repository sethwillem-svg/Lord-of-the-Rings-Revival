package io.github.sethwillemsvg.lotrrevival.combat;

import net.minecraft.resources.ResourceLocation;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;

import java.util.Locale;

/** Which race's clips a Middle-earth NPC plays (man, elf, dwarf, hobbit, orc), from its NPC type's race, else its type id. */
public final class NpcRaces {
    private NpcRaces() {}

    public static String of(NpcEntity npc) {
        String key = null;
        try {
            if (npc.getNpcType() != null && npc.getNpcType().getRace() != null) key = npc.getNpcType().getRace().getPath();
        } catch (RuntimeException ignored) {
        }
        if (key == null) {
            ResourceLocation id = npc.getNpcTypeIdentifier();
            if (id != null) key = id.getPath();
        }
        return key == null ? "man" : fromKey(key);
    }

    public static String fromKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        if (k.matches(".*(orc|uruk|goblin|snaga|gundabad|mordor|isengard|moria).*")) return "orc";
        if (k.contains("dwar") || k.contains("erebor") || k.contains("durin")) return "dwarf";
        if (k.contains("hobbit") || k.contains("shire")) return "hobbit";
        if (k.contains("elf") || k.contains("elv") || k.contains("lorien") || k.contains("woodland") || k.contains("rivendell")) return "elf";
        return "man";
    }
}
