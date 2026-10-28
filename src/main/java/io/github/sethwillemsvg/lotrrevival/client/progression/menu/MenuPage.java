package io.github.sethwillemsvg.lotrrevival.client.progression.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The pages of the Middle-earth menu. The last page used is remembered for the session. */
public enum MenuPage {
    HUB,
    ACHIEVEMENTS,
    TITLES,
    SHIELDS;

    private static MenuPage last = HUB;

    /** Opens the menu on the page used last (the hub the first time). */
    public static void openLast() {
        open(last);
    }

    public static void open(MenuPage page) {
        last = page;
        Minecraft.getInstance().setScreen(create(page));
    }

    /** Called by the page screens when they are shown, so that the menu reopens there. */
    static void remember(MenuPage page) {
        last = page;
    }

    private static Screen create(MenuPage page) {
        return switch (page) {
            case HUB -> new MenuHubScreen();
            case ACHIEVEMENTS -> new AchievementsScreen();
            case TITLES -> io.github.sethwillemsvg.lotrrevival.client.progression.ClientTitles.enabled()
                    ? new TitlesScreen()
                    : new MenuHubScreen();
            case SHIELDS -> new ShieldsScreen();
        };
    }
}
