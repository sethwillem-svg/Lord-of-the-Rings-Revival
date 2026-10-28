package io.github.sethwillemsvg.lotrrevival.client.core;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Floating "+1.0 Gondor" lines above the hotbar for three seconds after each alignment change. */
public final class AlignmentHud {
    private static final ResourceLocation LAYER = LOTRRevival.id("alignment_changes");

    private AlignmentHud() {}

    @EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(LAYER, (graphics, deltaTracker) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.options.hideGui) {
                    return;
                }
                long now = System.currentTimeMillis();
                List<ClientAlignment.Popup> popups = ClientAlignment.activePopups(now);
                int x = graphics.guiWidth() / 2;
                int y = graphics.guiHeight() - 70;
                for (int n = popups.size() - 1; n >= 0; n--) {
                    ClientAlignment.Popup popup = popups.get(n);
                    float age = (now - popup.createdMillis()) / 3000.0f;
                    int alpha = (int) (255 * Math.min(1.0f, 2.0f * (1.0f - age)));
                    if (alpha < 8) {
                        continue;
                    }
                    Component name = Factions.displayName(ResourceKey.create(
                            ModDataRegistries.FACTION,
                            popup.faction()));
                    String sign = popup.delta() > 0 ? "+" : "-";
                    String number = String.format(Locale.ROOT, "%s%.1f ", sign, Math.abs(popup.delta()));
                    int rgb = popup.delta() > 0 ? 0x55FF55 : 0xFF5555;
                    Component line = Component.literal(number).append(name);
                    int width = mc.font.width(line);
                    graphics.drawString(mc.font, line, x - width / 2, y, (alpha << 24) | rgb);
                    y -= 10;
                }
            });
        }
    }

    @EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientAlignment.clear();
        }
    }
}
