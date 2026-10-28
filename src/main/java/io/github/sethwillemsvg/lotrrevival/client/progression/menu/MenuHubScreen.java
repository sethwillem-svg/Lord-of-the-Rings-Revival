package io.github.sethwillemsvg.lotrrevival.client.progression.menu;

import io.github.sethwillemsvg.lotrrevival.client.progression.ClientQuests;
import io.github.sethwillemsvg.lotrrevival.client.progression.ClientTitles;
import io.github.sethwillemsvg.lotrrevival.progression.quest.PlayerQuests;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** The hub: buttons to the pages, plus a small summary of the player's quests. */
public final class MenuHubScreen extends MenuScreen {
    private int summaryY;
    private boolean showSummary;

    MenuHubScreen() {
        super(MenuPage.HUB, Component.translatable("gui.lotrrevival.menu.title"));
    }

    @Override
    protected void buildPage() {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("achievements", MenuPage.ACHIEVEMENTS));
        entries.add(new Entry("quests", null, io.github.sethwillemsvg.lotrrevival.client.progression.quest.log.QuestLogScreen::openFromMenu));
        entries.add(new Entry("map", null));
        entries.add(new Entry("factions", null));
        entries.add(new Entry("fellowships", null));
        if (ClientTitles.enabled()) {
            entries.add(new Entry("titles", MenuPage.TITLES));
        }
        entries.add(new Entry("shields", MenuPage.SHIELDS));
        entries.add(new Entry("options", null));

        int columns = panelHeight < 150 ? 3 : 2;
        int gap = 4;
        int buttonWidth = (panelWidth - 20 - gap * (columns - 1)) / columns;
        int rows = (entries.size() + columns - 1) / columns;
        int rowHeight = 22;
        int startY = top + 22;
        for (int n = 0; n < entries.size(); n++) {
            Entry entry = entries.get(n);
            int x = left + 10 + (n % columns) * (buttonWidth + gap);
            int y = startY + (n / columns) * rowHeight;
            Button button = Button.builder(Component.translatable("gui.lotrrevival.menu." + entry.key), b -> {
                        if (entry.action != null) {
                            entry.action.run();
                        } else if (entry.page != null) {
                            MenuPage.open(entry.page);
                        }
                    })
                    .bounds(x, y, buttonWidth, 18)
                    .build();
            if (entry.page == null && entry.action == null) {
                button.active = false;
                button.setTooltip(Tooltip.create(Component.translatable("gui.lotrrevival.menu.coming_later")));
            }
            addRenderableWidget(button);
        }
        int footerY = addFooter(false);
        summaryY = startY + rows * rowHeight + 4;
        showSummary = summaryY + 22 <= footerY;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left + panelWidth / 2, top + 8, GOLD);
        if (showSummary) {
            renderSummary(graphics);
        }
    }

    private void renderSummary(GuiGraphics graphics) {
        PlayerQuests quests = ClientQuests.get();
        int x0 = left + 10;
        int x1 = left + panelWidth - 10;
        graphics.fill(x0, summaryY, x1, summaryY + 22, INNER_BG);
        graphics.renderOutline(x0, summaryY, x1 - x0, 22, PANEL_BORDER);
        Component line = Component.translatable(
                "gui.lotrrevival.menu.quests.summary", quests.active().size(), quests.completedTotal());
        graphics.drawString(font, Component.translatable("gui.lotrrevival.menu.quests"), x0 + 4, summaryY + 3, GOLD, false);
        graphics.drawString(font, line, x0 + 4, summaryY + 12, TEXT, false);
        ClientQuests.tracked().ifPresent(tracked -> {
            Component text = Component.translatable("gui.lotrrevival.menu.quests.tracked", tracked.giverName());
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(text.getString(), x1 - x0 - 60),
                    x0 + 56,
                    summaryY + 3,
                    DIM,
                    false);
        });
    }

    private record Entry(String key, MenuPage page, Runnable action) {
        Entry(String key, MenuPage page) {
            this(key, page, null);
        }
    }
}
