package io.github.sethwillemsvg.lotrrevival.client.progression.menu;

import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** The Achievements page: 14 tabs (stepped with the arrows), an earned / total header and a scrolling list. */
public final class AchievementsScreen extends MenuScreen {
    private static final int ROW_HEIGHT = 20;
    private static AchievementTab lastTab = AchievementTab.GENERAL;

    private AchievementPageModel model;
    private AchievementTab tab = lastTab;
    private int scroll;
    private int listTop;
    private int listBottom;
    private int ticks;

    AchievementsScreen() {
        super(MenuPage.ACHIEVEMENTS, Component.translatable("gui.lotrrevival.menu.achievements"));
    }

    @Override
    protected void buildPage() {
        model = AchievementPageModel.build(Minecraft.getInstance().player);
        int footerY = addFooter(true);
        int tabRowY = top + 20;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> step(-1))
                .bounds(left + 10, tabRowY, 18, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> step(1))
                .bounds(left + panelWidth - 28, tabRowY, 18, 16)
                .build());
        listTop = tabRowY + 20;
        listBottom = footerY - 4;
        clampScroll();
    }

    private void step(int direction) {
        AchievementTab[] tabs = AchievementTab.values();
        tab = tabs[Math.floorMod(tab.ordinal() + direction, tabs.length)];
        lastTab = tab;
        scroll = 0;
    }

    private int maxScroll() {
        return Math.max(0, model.rows(tab).size() * ROW_HEIGHT - (listBottom - listTop));
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    @Override
    public void tick() {
        // Alignment flips and earned deltas arrive while the page is open; refresh a few times a second.
        if (++ticks % 5 == 0 && minecraft != null && minecraft.player != null) {
            AchievementPageModel next = AchievementPageModel.build(minecraft.player);
            if (!next.sameAs(model)) {
                model = next;
                clampScroll();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centre = left + panelWidth / 2;
        graphics.drawCenteredString(
                font,
                Component.translatable("gui.lotrrevival.menu.achievements.header", model.earnedTotal(), model.total()),
                centre,
                top + 7,
                GOLD);
        Component tabLabel = Component.translatable(
                "gui.lotrrevival.menu.achievements.tab",
                Component.translatable(tab.langKey()),
                model.earnedIn(tab),
                model.totalIn(tab));
        graphics.drawCenteredString(font, tabLabel, centre, top + 24, TEXT);

        int x0 = left + 10;
        int x1 = left + panelWidth - 10;
        graphics.fill(x0, listTop, x1, listBottom, INNER_BG);
        graphics.renderOutline(x0, listTop, x1 - x0, listBottom - listTop, PANEL_BORDER);

        List<AchievementPageModel.Row> rows = model.rows(tab);
        if (rows.isEmpty()) {
            graphics.drawCenteredString(
                    font, Component.translatable("gui.lotrrevival.menu.achievements.empty"), centre, listTop + 6, DIM);
            return;
        }
        AchievementPageModel.Row hovered = null;
        graphics.enableScissor(x0 + 1, listTop + 1, x1 - 1, listBottom - 1);
        for (int n = 0; n < rows.size(); n++) {
            int y = listTop + 1 + n * ROW_HEIGHT - scroll;
            if (y + ROW_HEIGHT < listTop || y > listBottom) {
                continue;
            }
            AchievementPageModel.Row row = rows.get(n);
            boolean over = mouseX >= x0 && mouseX < x1 - 4 && mouseY >= Math.max(y, listTop)
                    && mouseY < Math.min(y + ROW_HEIGHT, listBottom);
            if (over) {
                hovered = row;
                graphics.fill(x0 + 1, y, x1 - 1, y + ROW_HEIGHT - 1, 0xFF26211A);
            }
            graphics.renderItem(row.entry().icon(), x0 + 3, y + 1);
            if (!row.earned()) {
                graphics.fill(x0 + 3, y + 1, x0 + 19, y + 17, 0xB00F0D0A);
            }
            String title = font.plainSubstrByWidth(row.entry().title().getString(), x1 - x0 - 34);
            graphics.drawString(font, title, x0 + 24, y + 6, row.earned() ? GOLD : DIM, false);
        }
        graphics.disableScissor();
        drawScrollbar(graphics, x1 - 4, listTop + 1, listBottom - 1, scroll, maxScroll());
        if (hovered != null) {
            graphics.renderTooltip(font, font.split(hovered.entry().description(), 200), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * ROW_HEIGHT, 0, maxScroll());
        return true;
    }
}
