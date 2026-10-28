package io.github.sethwillemsvg.lotrrevival.progression.datagen;

import net.neoforged.neoforge.common.data.LanguageProvider;

/** en_us strings of the Middle-earth menu (client screens in {@code client.progression.menu}). */
public final class MenuLang {
    private MenuLang() {}

    public static void add(LanguageProvider lang) {
        lang.add("key.lotrrevival.open_menu", "Open Middle-earth Menu");
        lang.add("gui.lotrrevival.menu.title", "Middle-earth");
        lang.add("gui.lotrrevival.menu.achievements", "Achievements");
        lang.add("gui.lotrrevival.menu.map", "Map");
        lang.add("gui.lotrrevival.menu.factions", "Factions");
        lang.add("gui.lotrrevival.menu.fellowships", "Fellowships");
        lang.add("gui.lotrrevival.menu.titles", "Titles");
        lang.add("gui.lotrrevival.menu.shields", "Shields");
        lang.add("gui.lotrrevival.menu.options", "Options");
        lang.add("gui.lotrrevival.menu.coming_later", "Coming later");
        lang.add("gui.lotrrevival.menu.back", "Back");
        lang.add("gui.lotrrevival.menu.close", "Close");
        lang.add("gui.lotrrevival.menu.quests", "Quests");
        lang.add("gui.lotrrevival.menu.quests.summary", "Active: %1$s   Completed: %2$s");
        lang.add("gui.lotrrevival.menu.quests.tracked", "Tracking: quest from %s");
        lang.add("gui.lotrrevival.menu.achievements.header", "Achievements: %1$s / %2$s");
        lang.add("gui.lotrrevival.menu.achievements.tab", "%1$s: %2$s / %3$s");
        lang.add("gui.lotrrevival.menu.achievements.empty", "Nothing to show here for your allegiances.");
        lang.add("gui.lotrrevival.menu.titles.header", "Titles");
        lang.add("gui.lotrrevival.menu.titles.none", "None");
        lang.add("gui.lotrrevival.menu.titles.colour", "Colour");
        lang.add("gui.lotrrevival.menu.titles.feminine.on", "Feminine ranks: On");
        lang.add("gui.lotrrevival.menu.titles.feminine.off", "Feminine ranks: Off");
        lang.add("gui.lotrrevival.menu.titles.preview", "Preview");
        lang.add("gui.lotrrevival.menu.titles.apply", "Apply");
        lang.add("gui.lotrrevival.menu.titles.current", "Current: %s");
        lang.add("gui.lotrrevival.menu.titles.sent", "Sent...");
        lang.add("gui.lotrrevival.menu.titles.applied", "Applied");
        lang.add("gui.lotrrevival.menu.titles.rejected", "The server did not accept that choice.");
        lang.add("gui.lotrrevival.menu.titles.disabled", "Titles are disabled on this server.");
        FellowshipLang.add(lang);
        FellowshipPageLang.add(lang);
    }
}
