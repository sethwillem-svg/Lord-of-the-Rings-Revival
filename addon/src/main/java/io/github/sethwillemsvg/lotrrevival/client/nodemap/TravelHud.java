package io.github.sethwillemsvg.lotrrevival.client.nodemap;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.TravelCancelPayload;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.TravelStatusPayload;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The charge bar above the hotbar while a fast travel charge runs (server sends travel_status every 20 ticks; the bar
 * counts down locally in between), with a Cancel button beside it (spec N10). In play the mouse is captured, so the button
 * is clicked with any screen open that shows the cursor (chat, inventory, the map); the "Cancel travel" key works anywhere.
 * Either sends travel_cancel; the server cancels with reason "player" and answers with a cancelled status.
 */
public final class TravelHud {
    private static final int W = 120, H = 5, BTN_W = 44, BTN_H = 12;
    /** Cancel button rectangle of the last frame drawn (x0, y0, x1, y1), or null when no bar is shown. */
    @Nullable private static int[] cancelBtn;
    /** Game time a cancel was last sent, so a double click sends one packet. */
    private static long sentAt = Long.MIN_VALUE;

    private TravelHud() {}

    @EventBusSubscriber(modid = LotrRevival.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent e) {
            e.registerAboveAll(LotrRevival.id("travel_charge"), TravelHud::draw);
        }
    }

    /** True while the server says a charge is running (and has not gone quiet). */
    public static boolean charging() {
        TravelStatusPayload s = ClientNodeState.status();
        return s != null && s.state().equals("charging");
    }

    public static void draw(GuiGraphics g, DeltaTracker delta) {
        cancelBtn = null;
        Minecraft mc = Minecraft.getInstance();
        TravelStatusPayload s = ClientNodeState.status();
        if (s == null || mc.level == null || mc.options.hideGui) return;
        long age = mc.level.getGameTime() - ClientNodeState.statusAt();
        if (!s.state().equals("charging")) {
            if (age > 60) ClientNodeState.clearStatus();
            return;
        }
        int left = (int) Math.max(0, s.ticksLeft() - age);
        if (left <= 0 && age > 40) {   // no news from the server: drop the bar
            ClientNodeState.clearStatus();
            return;
        }
        float done = s.total() <= 0 ? 1f : 1f - (float) left / s.total();
        int x = (g.guiWidth() - W) / 2, y = g.guiHeight() - 59;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xC0000000);
        g.fill(x, y, x + Math.round(W * done), y + H, 0xFFD8B04A);

        Font font = mc.font;
        int bx = x + W + 6, by = y + H / 2 - BTN_H / 2;
        cancelBtn = new int[] {bx, by, bx + BTN_W, by + BTN_H};
        boolean cursor = mc.screen != null;
        double mx = cursor ? mc.mouseHandler.xpos() * g.guiWidth() / mc.getWindow().getScreenWidth() : -1;
        double my = cursor ? mc.mouseHandler.ypos() * g.guiHeight() / mc.getWindow().getScreenHeight() : -1;
        boolean over = in(cancelBtn, mx, my);
        g.fill(bx, by, bx + BTN_W, by + BTN_H, over ? 0xFF8B3A3A : 0xC05A2828);
        g.renderOutline(bx, by, BTN_W, BTN_H, 0xFF8B6A3A);
        g.drawCenteredString(font, Component.translatable("nodemap.lotr_revival.hud.cancel"), bx + BTN_W / 2, by + 2, 0xFFFFFFFF);
        if (!cursor) {
            Component hint = NodeMapKeys.CANCEL.isUnbound()
                    ? Component.translatable("nodemap.lotr_revival.hud.cancel_hint_chat")
                    : Component.translatable("nodemap.lotr_revival.hud.cancel_hint_key", NodeMapKeys.CANCEL.getTranslatedKeyMessage());
            g.drawCenteredString(font, hint, g.guiWidth() / 2, y - 11, 0xFFC0C0C0);
        }
    }

    private static boolean in(@Nullable int[] r, double x, double y) {
        return r != null && x >= r[0] && x < r[2] && y >= r[1] && y < r[3];
    }

    /** Sends travel_cancel once per click; {@code via} is for the log (hud, key, panel). */
    public static void sendCancel(String via) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.level == null) return;
        long now = mc.level.getGameTime();
        if (now - sentAt < 5) return;
        sentAt = now;
        LotrLog.dbg("travel", "client: Cancel pressed via {} target={}", via, ClientNodeState.status() == null ? "?" : ClientNodeState.status().target());
        PacketDistributor.sendToServer(TravelCancelPayload.INSTANCE);
    }

    @EventBusSubscriber(modid = LotrRevival.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        /** Before the map layer's own click handler, so a click on the button never selects a node or drags the map. */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void click(ScreenEvent.MouseButtonPressed.Pre e) {
            if (e.getButton() != 0 || !charging() || !in(cancelBtn, e.getMouseX(), e.getMouseY())) return;
            sendCancel("hud");
            e.setCanceled(true);
        }
    }
}
