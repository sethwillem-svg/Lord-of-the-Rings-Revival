package io.github.sethwillemsvg.lotrrevival.client.npc.unit;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.hire.command.UnitCommandMenu;
import io.github.sethwillemsvg.lotrrevival.npc.hire.command.UnitCommandMenus;
import io.github.sethwillemsvg.lotrrevival.npc.hire.command.UnitCommandPayloads;
import io.github.sethwillemsvg.lotrrevival.npc.hire.command.UnitInfo;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The unit command screen: read-only facts of the unit on top, the five gear slots, the teleport and guard switches,
 * the guard range slider and Dismiss with a confirm click. Drawn with plain fills so it needs no texture. Every
 * button sends a {@link UnitCommandPayloads.Command}; the shown values come from the server's {@link UnitInfo}.
 */
public class UnitCommandScreen extends AbstractContainerScreen<UnitCommandMenu> {
    private static final String KEY = "npc.lotrrevival.unit_command.";
    private static final int PANEL = 0xFF2B2520;
    private static final int BORDER = 0xFF8C7A5B;
    private static final int SLOT_BG = 0xFF1A1612;
    private static final int SLOT_EDGE = 0xFF5A4E3C;
    private static final int TEXT = 0xFFE8DCC0;
    private static final int TEXT_DIM = 0xFF857B6A;
    private static final int BAR_BG = 0xFF1A1612;
    private static final int BAR_FILL = 0xFF7FA650;
    private static final int CONFIRM_TICKS = 60;
    private static final int SLIDER_SEND_DELAY = 5;

    private Button teleportButton;
    private Button guardButton;
    private Button dismissButton;
    private RangeSlider slider;
    private int confirmTicks;

    public UnitCommandScreen(UnitCommandMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 242;
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 150;
    }

    @Override
    protected void init() {
        super.init();
        teleportButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleTeleport())
                .bounds(leftPos + 8, topPos + 118, 78, 14)
                .build());
        guardButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleGuard())
                .bounds(leftPos + 90, topPos + 118, 78, 14)
                .build());
        slider = addRenderableWidget(new RangeSlider(leftPos + 8, topPos + 134, 160, 14, 8));
        dismissButton = addRenderableWidget(Button.builder(Component.translatable(KEY + "dismiss"), b -> dismiss())
                .bounds(leftPos + 112, topPos + 94, 56, 16)
                .build());
    }

    // ------------------------------------------------------------------ actions

    private void send(UnitCommandMenu.Action action, int value) {
        PacketDistributor.sendToServer(new UnitCommandPayloads.Command(menu.containerId, action, value));
    }

    private void toggleTeleport() {
        UnitInfo info = menu.info();
        if (info != null) {
            send(info.teleport() ? UnitCommandMenu.Action.TELEPORT_OFF : UnitCommandMenu.Action.TELEPORT_ON, 0);
        }
    }

    private void toggleGuard() {
        UnitInfo info = menu.info();
        if (info != null) {
            if (info.guarding()) {
                send(UnitCommandMenu.Action.GUARD_OFF, 0);
            } else {
                send(UnitCommandMenu.Action.GUARD_ON, slider.range());
            }
        }
    }

    private void dismiss() {
        if (confirmTicks > 0) {
            confirmTicks = 0;
            send(UnitCommandMenu.Action.DISMISS, 0);
        } else {
            confirmTicks = CONFIRM_TICKS;
        }
    }

    // ------------------------------------------------------------------ range slider

    /** Slider of the guard range, 1-64. Sends once shortly after the last change, so a drag is not a packet storm. */
    private final class RangeSlider extends AbstractSliderButton {
        private boolean dirty;
        private int sendIn;

        RangeSlider(int x, int y, int width, int height, int range) {
            super(x, y, width, height, Component.empty(), toValue(range));
            updateMessage();
        }

        private static double toValue(int range) {
            return (range - 1) / 63.0;
        }

        int range() {
            return 1 + (int) Math.round(value * 63.0);
        }

        void setRange(int range) {
            this.value = toValue(Math.max(1, Math.min(64, range)));
            updateMessage();
        }

        boolean pending() {
            return dirty;
        }

        void tickSend() {
            if (dirty && --sendIn <= 0) {
                dirty = false;
                send(UnitCommandMenu.Action.SET_RANGE, range());
            }
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(KEY + "range", range()));
        }

        @Override
        protected void applyValue() {
            dirty = true;
            sendIn = SLIDER_SEND_DELAY;
        }
    }

    // ------------------------------------------------------------------ updating

    @Override
    protected void containerTick() {
        super.containerTick();
        if (confirmTicks > 0) {
            confirmTicks--;
        }
        slider.tickSend();
    }

    private void updateWidgets() {
        UnitInfo info = menu.info();
        boolean ready = info != null;
        teleportButton.active = ready;
        guardButton.active = ready;
        dismissButton.active = ready;
        slider.active = ready;
        if (ready) {
            teleportButton.setMessage(Component.translatable(KEY + (info.teleport() ? "teleport.on" : "teleport.off")));
            guardButton.setMessage(Component.translatable(KEY + (info.guarding() ? "guard.on" : "guard.off")));
            if (!slider.pending() && !slider.isFocused() && slider.range() != info.guardRange()) {
                slider.setRange(info.guardRange());
            }
        }
        dismissButton.setMessage(Component.translatable(KEY + (confirmTicks > 0 ? "dismiss_confirm" : "dismiss")));
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
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, SLOT_EDGE);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BG);
        }
        UnitInfo info = menu.info();
        int x = leftPos + 8;
        int y = topPos + 18;
        if (info == null) {
            graphics.drawString(font, Component.translatable(KEY + "loading"), x, y, TEXT_DIM, false);
            return;
        }
        graphics.drawString(font, Component.translatable(KEY + "faction", factionName(info)), x, y, TEXT, false);
        graphics.drawString(
                font,
                Component.translatable(KEY + (info.leveling() ? "level" : "level_max"), info.level()),
                x,
                y + 11,
                TEXT,
                false);
        if (info.leveling()) {
            int barW = 160;
            int span = Math.max(1, info.nextLevelXp() - info.levelStartXp());
            int filled = Math.max(0, Math.min(barW, (int) ((long) (info.xp() - info.levelStartXp()) * barW / span)));
            graphics.fill(x, y + 22, x + barW, y + 28, BAR_BG);
            graphics.fill(x, y + 22, x + filled, y + 28, BAR_FILL);
            graphics.drawString(
                    font,
                    Component.translatable(KEY + "xp", info.xp(), info.nextLevelXp()),
                    x,
                    y + 31,
                    TEXT_DIM,
                    false);
        }
        graphics.drawString(font, Component.translatable(KEY + "kills", info.kills()), x + 90, y + 11, TEXT, false);
        graphics.drawString(
                font,
                Component.translatable(KEY + "status", Component.translatable(KEY + "status." + info.status().name().toLowerCase(Locale.ROOT))),
                x,
                y + 43,
                TEXT,
                false);
        graphics.drawString(
                font, Component.translatable(KEY + "required", formatAlignment(info.requiredAlignment())), x, y + 54, TEXT, false);
        graphics.drawString(
                font,
                Component.translatable(KEY + "pledge", Component.translatable(KEY + "pledge." + info.pledge().getSerializedName())),
                x,
                y + 65,
                TEXT,
                false);
    }

    private static Component factionName(UnitInfo info) {
        return Component.translatable("faction." + info.faction().getNamespace() + "." + info.faction().getPath());
    }

    private static String formatAlignment(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        Slot hovered = hoveredSlot;
        if (hovered != null && !hovered.hasItem() && hovered.index < UnitCommandMenu.GEAR_SLOTS) {
            graphics.renderTooltip(font, gearName(hovered.index), mouseX, mouseY);
        }
    }

    @Nullable
    private static Component gearName(int index) {
        String name = switch (index) {
            case 0 -> "head";
            case 1 -> "chest";
            case 2 -> "legs";
            case 3 -> "feet";
            default -> "mainhand";
        };
        return Component.translatable(KEY + "gear." + name);
    }

    /** Registers the screen on the client mod bus. */
    @EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            event.register(UnitCommandMenus.MENU.get(), UnitCommandScreen::new);
        }
    }
}
