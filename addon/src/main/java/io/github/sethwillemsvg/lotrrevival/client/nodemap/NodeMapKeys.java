package io.github.sethwillemsvg.lotrrevival.client.nodemap;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.OpenNodeMapPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Key "Open travel map" (B by default): asks the server, which opens Middle-earth's map screen and sends a fresh node_state. */
public final class NodeMapKeys {
    public static final KeyMapping OPEN = new KeyMapping("key.lotr_revival.open_node_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B,
            "key.categories.lotr_revival");

    private NodeMapKeys() {}

    @EventBusSubscriber(modid = LotrRevival.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(OPEN);
        }
    }

    @EventBusSubscriber(modid = LotrRevival.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            while (OPEN.consumeClick()) {
                if (Minecraft.getInstance().getConnection() == null) continue;
                LotrLog.dbg("nodemap", "client: open travel map key");
                PacketDistributor.sendToServer(OpenNodeMapPayload.INSTANCE);
            }
        }
    }
}
