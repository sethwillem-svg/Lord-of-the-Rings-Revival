package io.github.sethwillemsvg.lotrrevival.client.progression.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared frame of the menu pages: a plain panel in our own colours that fits any window size. */
abstract class MenuScreen extends Screen {
    static final int PANEL_BG = 0xF0191612;
    static final int PANEL_BORDER = 0xFF8A7440;
    static final int INNER_BG = 0xFF0F0D0A;
    static final int GOLD = 0xFFE8C860;
    static final int TEXT = 0xFFE6E0D0;
    static final int DIM = 0xFF807868;

    protected int left;
    protected int top;
    protected int panelWidth;
    protected int panelHeight;

    private final MenuPage page;

    protected MenuScreen(MenuPage page, Component title) {
        super(title);
        this.page = page;
    }

    @Override
    protected final void init() {
        panelWidth = Math.min(width - 8, 300);
        panelHeight = Math.min(height - 8, 220);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        MenuPage.remember(page);
        buildPage();
    }

    protected abstract void buildPage();

    /** Adds the bottom "Back" (to the hub) or "Close" button and returns its top edge. */
    protected final int addFooter(boolean back) {
        int y = top + panelHeight - 22;
        Component label = Component.translatable(back ? "gui.lotrrevival.menu.back" : "gui.lotrrevival.menu.close");
        addRenderableWidget(Button.builder(label, button -> {
                    if (back) {
                        MenuPage.open(MenuPage.HUB);
                    } else {
                        onClose();
                    }
                })
                .bounds(left + panelWidth / 2 - 40, y, 80, 18)
                .build());
        return y;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, PANEL_BORDER);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL_BG);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Draws a thin scrollbar for a list occupying {@code y0..y1} at the right edge {@code x}. */
    protected static void drawScrollbar(GuiGraphics graphics, int x, int y0, int y1, int scroll, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        int height = y1 - y0;
        int bar = Math.max(8, height * height / (height + maxScroll));
        int barY = y0 + (int) ((long) (height - bar) * scroll / maxScroll);
        graphics.fill(x, y0, x + 3, y1, 0xFF2A2620);
        graphics.fill(x, barY, x + 3, barY + bar, PANEL_BORDER);
    }
}
