package io.github.sethwillemsvg.lotrrevival.client.nemesis;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Client wiring for nemeses and world events: the Nemeses key (N) and the event marker cache. */
public final class NemesisClient {
    public static final KeyMapping NEMESES_KEY = new KeyMapping("key.lotr_revival.nemeses", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N,
            "key.categories.lotr_revival");

    private NemesisClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(NEMESES_KEY));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            while (NEMESES_KEY.consumeClick()) {
                if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen == null) NemesisScreen.open();
            }
        });
        EventMarkerCache.init();
    }
}
