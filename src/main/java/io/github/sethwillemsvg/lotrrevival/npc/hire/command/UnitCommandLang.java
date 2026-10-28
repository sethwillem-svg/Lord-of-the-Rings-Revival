package io.github.sethwillemsvg.lotrrevival.npc.hire.command;

import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Lang entries of the unit command screen. */
public final class UnitCommandLang {
    private UnitCommandLang() {}

    /** Called once from {@code NpcDataGen.gather}. */
    public static void gather(GatherDataEvent event) {
        ModLangProvider.contribute(lang -> {
            String p = "npc.lotrrevival.unit_command.";
            lang.add(p + "faction", "Faction: %s");
            lang.add(p + "level", "Level %s");
            lang.add(p + "level_max", "Level %s (does not level)");
            lang.add(p + "xp", "%s / %s XP");
            lang.add(p + "kills", "Kills: %s");
            lang.add(p + "status", "Status: %s");
            lang.add(p + "required", "Needs alignment: %s");
            lang.add(p + "pledge", "Pledge: %s");
            lang.add(p + "loading", "...");
            lang.add(p + "status.following", "Following");
            lang.add(p + "status.halted", "Halted");
            lang.add(p + "status.guarding", "Guarding");
            lang.add(p + "status.in_combat", "In combat");
            lang.add(p + "status.owner_away", "Owner away");
            lang.add(p + "pledge.none", "None needed");
            lang.add(p + "pledge.faction", "Its own faction");
            lang.add(p + "pledge.any_elf", "An elven faction");
            lang.add(p + "pledge.any_dwarf", "A dwarven faction");
            lang.add(p + "teleport.on", "Teleport: On");
            lang.add(p + "teleport.off", "Teleport: Off");
            lang.add(p + "guard.on", "Guard: On");
            lang.add(p + "guard.off", "Guard: Off");
            lang.add(p + "range", "Guard range: %s");
            lang.add(p + "dismiss", "Dismiss");
            lang.add(p + "dismiss_confirm", "Confirm?");
            lang.add(p + "gear.head", "Helmet");
            lang.add(p + "gear.chest", "Chestplate");
            lang.add(p + "gear.legs", "Leggings");
            lang.add(p + "gear.feet", "Boots");
            lang.add(p + "gear.mainhand", "Main hand");
        });
    }
}
