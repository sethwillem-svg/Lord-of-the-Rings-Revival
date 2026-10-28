package io.github.sethwillemsvg.lotrrevival.npc.hire.progress;

import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Lang for unit levels and deaths. Speech lines live in {@link UnitSpeechLines}. */
public final class UnitProgressDataGen {
    private UnitProgressDataGen() {}

    /** Called once from {@code NpcDataGen.gather}. */
    public static void gather(GatherDataEvent event) {
        ModLangProvider.contribute(lang -> {
            lang.add("npc.lotrrevival.unit.level_up", "%s has reached level %s.");
            lang.add("npc.lotrrevival.unit.death", "%s, level %s, has fallen. %s");
            lang.add(
                    "commands.lotrrevival.npc.unitdeathmessages.on",
                    "You will be told when one of your units dies.");
            lang.add(
                    "commands.lotrrevival.npc.unitdeathmessages.off",
                    "You will no longer be told when one of your units dies.");
        });
    }
}
