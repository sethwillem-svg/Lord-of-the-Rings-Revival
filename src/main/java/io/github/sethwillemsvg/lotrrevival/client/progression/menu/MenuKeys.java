package io.github.sethwillemsvg.lotrrevival.client.progression.menu;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.CoreKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The menu key (L, rebindable, in Core's key category). Vanilla Advancements also defaults to L; both mappings
 * fire on the same press, so while our press is being handled a vanilla Advancements screen opened by that same
 * press is swapped for the menu. Rebind either key to separate them.
 */
public final class MenuKeys {
    public static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.lotrrevival.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, CoreKeys.CATEGORY);

    private static boolean pressedThisTick;

    private MenuKeys() {}

    @EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_MENU);
        }
    }

    @EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        /** Runs before vanilla handles its keys for the tick, so the press is known when Advancements opens. */
        @SubscribeEvent
        public static void onTickPre(ClientTickEvent.Pre event) {
            Minecraft mc = Minecraft.getInstance();
            pressedThisTick = false;
            while (OPEN_MENU.consumeClick()) {
                if (mc.screen == null && mc.player != null) {
                    pressedThisTick = true;
                }
            }
        }

        @SubscribeEvent
        public static void onScreenOpening(ScreenEvent.Opening event) {
            if (pressedThisTick && event.getNewScreen() instanceof AdvancementsScreen) {
                event.setNewScreen(null);
            }
        }

        @SubscribeEvent
        public static void onTickPost(ClientTickEvent.Post event) {
            if (!pressedThisTick) {
                return;
            }
            pressedThisTick = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null && mc.player != null) {
                MenuPage.openLast();
            }
        }
    }
}
