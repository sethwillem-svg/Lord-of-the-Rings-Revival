package io.github.sethwillemsvg.lotrrevival.client.world;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.api.WaypointClient;
import io.github.sethwillemsvg.lotrrevival.world.network.TravelStatusMirror;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client wiring of the travel mirror: registers the {@link WaypointClient} backend, runs the clock, clears on exit. */
public final class ClientTravelEvents {
    private ClientTravelEvents() {}

    @EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> WaypointClient.register(new ClientWaypointBackend()));
        }
    }

    @EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            // The server clock only runs while its world ticks, so a paused single-player game pauses this one too.
            if (mc.level != null && !mc.isPaused()) {
                ClientTravel.tick();
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientTravel.clear();
            TravelStatusMirror.clear();
        }
    }
}
