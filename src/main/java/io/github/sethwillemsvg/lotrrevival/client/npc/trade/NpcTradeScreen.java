package io.github.sethwillemsvg.lotrrevival.client.npc.trade;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.coin.Coins;
import io.github.sethwillemsvg.lotrrevival.npc.hire.purchase.HirePayloads;
import io.github.sethwillemsvg.lotrrevival.npc.hire.purchase.HirePurchase;
import io.github.sethwillemsvg.lotrrevival.npc.hire.purchase.UnitRowView;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradeMenu;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradeMenu.Tab;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradeMenus;
import io.github.sethwillemsvg.lotrrevival.npc.trade.OfferView;
import io.github.sethwillemsvg.lotrrevival.npc.trade.TradePayloads;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The trade screen: tab buttons on top; Buy lists the offers as clickable cells, Sell has the sell slots, a value
 * preview and a Sell button, Exchange has one slot with Break and Combine, Units is a placeholder. Drawn with plain
 * fills so it needs no texture.
 */
public class NpcTradeScreen extends AbstractContainerScreen<NpcTradeMenu> {
    private static final int CELL_W = 83;
    private static final int CELL_H = 18;
    private static final int CELL_X = 6;
    private static final int CELL_Y = 32;

    private static final int PANEL = 0xFF2B2520;
    private static final int BORDER = 0xFF8C7A5B;
    private static final int SLOT_BG = 0xFF1A1612;
    private static final int SLOT_EDGE = 0xFF5A4E3C;
    private static final int CELL_NORMAL = 0xFF4A4034;
    private static final int CELL_HOVER = 0xFF63553F;
    private static final int CELL_OFF = 0xFF2F2A25;
    private static final int TEXT = 0xFFE8DCC0;
    private static final int TEXT_OFF = 0xFF857B6A;
    private static final int PRICE = 0xFFD8D8E0;

    private final Map<Tab, Button> tabButtons = new EnumMap<>(Tab.class);
    private Button sellButton;
    private Button breakButton;
    private Button combineButton;
    private EditBox squadronBox;
    private String squadronText = "";
    private int unitScroll;

    public NpcTradeScreen(NpcTradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 132;
    }

    @Override
    protected void init() {
        super.init();
        tabButtons.clear();
        int x = leftPos + 8;
        for (Tab tab : Tab.values()) {
            if (!menu.hasTab(tab)) {
                continue;
            }
            Component label = Component.translatable("npc.lotrrevival.trade.tab." + tab.name().toLowerCase());
            int width = font.width(label) + 10;
            Button button = addRenderableWidget(Button.builder(label, b -> selectTab(tab))
                    .bounds(x, topPos + 14, width, 14)
                    .build());
            tabButtons.put(tab, button);
            x += width + 2;
        }
        sellButton = addRenderableWidget(Button.builder(
                        Component.translatable("npc.lotrrevival.trade.sell.button"),
                        b -> PacketDistributor.sendToServer(new TradePayloads.Sell(menu.containerId)))
                .bounds(leftPos + 108, topPos + 106, 60, 16)
                .build());
        breakButton = addRenderableWidget(Button.builder(
                        Component.translatable("npc.lotrrevival.trade.exchange.break"),
                        b -> PacketDistributor.sendToServer(new TradePayloads.CoinExchange(menu.containerId, false)))
                .bounds(leftPos + 10, topPos + 61, 56, 18)
                .build());
        combineButton = addRenderableWidget(Button.builder(
                        Component.translatable("npc.lotrrevival.trade.exchange.combine"),
                        b -> PacketDistributor.sendToServer(new TradePayloads.CoinExchange(menu.containerId, true)))
                .bounds(leftPos + 110, topPos + 61, 56, 18)
                .build());
        squadronBox = new EditBox(
                font,
                leftPos + 8,
                topPos + 114,
                160,
                14,
                Component.translatable("npc.lotrrevival.trade.units.squadron"));
        squadronBox.setMaxLength(HirePurchase.SQUADRON_FIELD_MAX);
        squadronBox.setHint(Component.translatable("npc.lotrrevival.trade.units.squadron_hint"));
        squadronBox.setValue(squadronText);
        squadronBox.setResponder(text -> squadronText = text);
        addRenderableWidget(squadronBox);
    }

    private void selectTab(Tab tab) {
        if (menu.tab() == tab || minecraft == null || minecraft.gameMode == null) {
            return;
        }
        menu.setTab(tab);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, NpcTradeMenu.BUTTON_TAB_BASE + tab.ordinal());
    }

    private void updateWidgets() {
        tabButtons.forEach((tab, button) -> button.active = menu.tab() != tab);
        sellButton.visible = menu.tab() == Tab.SELL;
        breakButton.visible = menu.tab() == Tab.EXCHANGE;
        combineButton.visible = menu.tab() == Tab.EXCHANGE;
        squadronBox.visible = menu.tab() == Tab.UNITS;
        if (!squadronBox.visible && squadronBox.isFocused()) {
            squadronBox.setFocused(false);
        }
    }

    // ------------------------------------------------------------------ unit rows

    private static final int ROW_W = 160;
    private static final int ROW_H = 22;
    private static final int ROW_X = 8;
    private static final int ROW_Y = 32;
    private static final int ROWS_SHOWN = 3;

    private int rowY(int index) {
        return topPos + ROW_Y + (index - unitScroll) * (ROW_H + 1);
    }

    private int rowAt(double mx, double my) {
        List<UnitRowView> rows = menu.units();
        for (int i = unitScroll; i < rows.size() && i < unitScroll + ROWS_SHOWN; i++) {
            int y = rowY(i);
            if (mx >= leftPos + ROW_X && mx < leftPos + ROW_X + ROW_W && my >= y && my < y + ROW_H) {
                return i;
            }
        }
        return -1;
    }

    private static Component unitName(UnitRowView row) {
        Component name = BuiltInRegistries.ENTITY_TYPE.get(row.entity()).getDescription();
        return row.mount()
                .map(mount -> (Component) Component.translatable(
                        "npc.lotrrevival.trade.units.mounted", name, BuiltInRegistries.ENTITY_TYPE.get(mount).getDescription()))
                .orElse(name);
    }

    private static Component reason(UnitRowView row) {
        return Component.translatable("npc.lotrrevival.trade.units.reason." + row.blocked().name().toLowerCase());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (menu.tab() == Tab.UNITS) {
            int max = Math.max(0, menu.units().size() - ROWS_SHOWN);
            unitScroll = Math.max(0, Math.min(max, unitScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // A focused squadron field keeps every key except Escape (so typing "e" does not close the screen).
        if (squadronBox != null && squadronBox.visible && squadronBox.isFocused() && keyCode != 256) {
            squadronBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ buy cells

    private int cellX(int index) {
        return leftPos + CELL_X + (index % 2) * (CELL_W + 2);
    }

    private int cellY(int index) {
        return topPos + CELL_Y + (index / 2) * (CELL_H + 1);
    }

    private int cellAt(double mx, double my) {
        List<OfferView> buys = menu.buys();
        for (int i = 0; i < buys.size(); i++) {
            if (mx >= cellX(i) && mx < cellX(i) + CELL_W && my >= cellY(i) && my < cellY(i) + CELL_H) {
                return i;
            }
        }
        return -1;
    }

    private boolean affordable(OfferView offer) {
        return minecraft != null && minecraft.player != null && Coins.count(minecraft.player) >= offer.price();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && menu.tab() == Tab.BUY) {
            int cell = cellAt(mx, my);
            if (cell >= 0) {
                OfferView offer = menu.buys().get(cell);
                if (offer.tradable() && affordable(offer)) {
                    PacketDistributor.sendToServer(new TradePayloads.Buy(menu.containerId, cell));
                }
                return true;
            }
        }
        if (button == 0 && menu.tab() == Tab.UNITS && !(squadronBox.isMouseOver(mx, my))) {
            int row = rowAt(mx, my);
            if (row >= 0) {
                UnitRowView view = menu.units().get(row);
                if (view.hirable()) {
                    PacketDistributor.sendToServer(
                            new HirePayloads.HireUnit(menu.containerId, view.id(), squadronText.trim()));
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateWidgets();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, PANEL);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                int x = leftPos + slot.x - 1;
                int y = topPos + slot.y - 1;
                graphics.fill(x, y, x + 18, y + 18, SLOT_EDGE);
                graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BG);
            }
        }
        switch (menu.tab()) {
            case BUY -> drawBuy(graphics, mouseX, mouseY);
            case SELL -> drawSell(graphics);
            case EXCHANGE -> graphics.drawCenteredString(
                    font, Component.translatable("npc.lotrrevival.trade.exchange.hint"), leftPos + imageWidth / 2, topPos + 40, TEXT);
            case UNITS -> drawUnits(graphics, mouseX, mouseY);
        }
    }

    private void drawUnits(GuiGraphics graphics, int mouseX, int mouseY) {
        List<UnitRowView> rows = menu.units();
        if (rows.isEmpty()) {
            graphics.drawCenteredString(
                    font, Component.translatable("npc.lotrrevival.trade.units.placeholder"), leftPos + imageWidth / 2, topPos + 50, TEXT);
        }
        for (int i = unitScroll; i < rows.size() && i < unitScroll + ROWS_SHOWN; i++) {
            UnitRowView row = rows.get(i);
            int x = leftPos + ROW_X;
            int y = rowY(i);
            boolean hover = mouseX >= x && mouseX < x + ROW_W && mouseY >= y && mouseY < y + ROW_H;
            boolean on = row.hirable();
            graphics.fill(x, y, x + ROW_W, y + ROW_H, !on ? CELL_OFF : hover ? CELL_HOVER : CELL_NORMAL);
            graphics.drawString(font, font.substrByWidth(unitName(row), 84).getString(), x + 3, y + 3, on ? TEXT : TEXT_OFF, false);
            Component cost = Component.translatable("npc.lotrrevival.trade.units.cost", row.cost());
            graphics.drawString(font, cost, x + ROW_W - 3 - font.width(cost), y + 3, on ? PRICE : TEXT_OFF, false);
            Component detail = on
                    ? Component.translatable("npc.lotrrevival.trade.units.required", Math.round(row.required()))
                    : reason(row);
            graphics.drawString(font, font.plainSubstrByWidth(detail.getString(), ROW_W - 40), x + 3, y + 12, on ? TEXT_OFF : 0xFFD07060, false);
            if (on) {
                Component hire = Component.translatable("npc.lotrrevival.trade.units.hire");
                graphics.drawString(font, hire, x + ROW_W - 3 - font.width(hire), y + 12, hover ? TEXT : PRICE, false);
            }
        }
        graphics.drawString(font, Component.translatable("npc.lotrrevival.trade.units.squadron"), leftPos + 8, topPos + 104, TEXT, false);
    }

    private void drawBuy(GuiGraphics graphics, int mouseX, int mouseY) {
        List<OfferView> buys = menu.buys();
        for (int i = 0; i < buys.size(); i++) {
            OfferView offer = buys.get(i);
            boolean enabled = offer.tradable() && affordable(offer);
            int x = cellX(i);
            int y = cellY(i);
            boolean hover = mouseX >= x && mouseX < x + CELL_W && mouseY >= y && mouseY < y + CELL_H;
            graphics.fill(x, y, x + CELL_W, y + CELL_H, !enabled ? CELL_OFF : hover ? CELL_HOVER : CELL_NORMAL);
            graphics.renderItem(offer.displayStack(), x + 1, y + 1);
            if (offer.count() > 1) {
                graphics.drawString(font, "x" + offer.count(), x + 19, y + 5, enabled ? TEXT : TEXT_OFF, false);
            }
            Component price = Component.translatable("npc.lotrrevival.trade.price", offer.price());
            graphics.drawString(font, price, x + CELL_W - 3 - font.width(price), y + 5, enabled ? PRICE : TEXT_OFF, false);
            if (!enabled) {
                graphics.fill(x, y, x + CELL_W, y + CELL_H, 0x66000000);
            }
        }
    }

    private void drawSell(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("npc.lotrrevival.trade.sell.header"), leftPos + 8, topPos + 32, TEXT, false);
        List<OfferView> sells = menu.sells();
        if (sells.isEmpty()) {
            graphics.drawString(font, Component.translatable("npc.lotrrevival.trade.sell.none"), leftPos + 8, topPos + 44, TEXT_OFF, false);
        }
        for (int i = 0; i < sells.size() && i < 9; i++) {
            OfferView offer = sells.get(i);
            int color = offer.tradable() ? TEXT : TEXT_OFF;
            int y = topPos + 43 + i * 10;
            String line = offer.count() + "x " + offer.displayStack().getHoverName().getString();
            graphics.drawString(font, font.plainSubstrByWidth(line, 68), leftPos + 8, y, color, false);
            String price = Integer.toString(offer.price());
            graphics.drawString(font, price, leftPos + 98 - font.width(price), y, offer.tradable() ? PRICE : TEXT_OFF, false);
        }
        List<ItemStack> held = new ArrayList<>();
        for (int i = 0; i < NpcTradeMenu.SELL_SLOTS; i++) {
            held.add(menu.getSlot(i).getItem());
        }
        int worth = NpcTradeMenu.previewSell(held, sells);
        graphics.drawString(
                font,
                Component.translatable("npc.lotrrevival.trade.sell.value", worth),
                leftPos + 108,
                topPos + 92,
                worth > 0 ? PRICE : TEXT_OFF,
                false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (menu.tab() == Tab.UNITS && minecraft != null) {
            int row = rowAt(mouseX, mouseY);
            if (row >= 0) {
                UnitRowView view = menu.units().get(row);
                List<Component> lines = new ArrayList<>();
                lines.add(unitName(view));
                lines.add(Component.translatable("npc.lotrrevival.trade.units.required", Math.round(view.required())));
                if (view.pledge() != io.github.sethwillemsvg.lotrrevival.npc.hire.PledgeCondition.NONE) {
                    lines.add(Component.translatable(
                            "npc.lotrrevival.trade.units.pledge." + view.pledge().getSerializedName()));
                }
                if (!view.hirable()) {
                    lines.add(reason(view));
                }
                graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
            }
            return;
        }
        if (menu.tab() != Tab.BUY || minecraft == null) {
            return;
        }
        int cell = cellAt(mouseX, mouseY);
        if (cell < 0) {
            return;
        }
        OfferView offer = menu.buys().get(cell);
        List<Component> lines = new ArrayList<>();
        ItemStack stack = offer.displayStack();
        if (offer.tag().isPresent() && offer.item().isEmpty()) {
            lines.add(Component.translatable("npc.lotrrevival.trade.any", stack.getHoverName()));
        } else {
            lines.addAll(getTooltipFromItem(minecraft, stack));
        }
        lines.add(Component.translatable("npc.lotrrevival.trade.price_tooltip", offer.price()));
        if (!offer.tradable()) {
            lines.add(Component.translatable("npc.lotrrevival.trade.sold_out"));
        } else if (!affordable(offer)) {
            lines.add(Component.translatable("npc.lotrrevival.trade.cannot_afford"));
        }
        graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
    }

    /** Registers the screen on the client mod bus. */
    @EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            event.register(NpcTradeMenus.MENU.get(), NpcTradeScreen::new);
        }
    }
}
