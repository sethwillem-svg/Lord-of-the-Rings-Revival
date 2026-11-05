package io.github.sethwillemsvg.lotrrevival.client.nodemap;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.TravelStatusPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The charge bar above the hotbar while a fast travel charge runs (server sends travel_status every 20 ticks; the bar
 * counts down locally in between). The action bar countdown for the last 5 seconds comes from the server.
 */
public final class TravelHud {
    private static final int W = 120, H = 5;

    private TravelHud() {}

    @EventBusSubscriber(modid = LotrRevival.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent e) {
            e.registerAboveAll(LotrRevival.id("travel_charge"), TravelHud::draw);
        }
    }

    public static void draw(GuiGraphics g, DeltaTracker delta) {
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
        // TODO(spec Packets, travel_cancel): a Cancel button needs mouse input; for now walking away cancels
    }
}
