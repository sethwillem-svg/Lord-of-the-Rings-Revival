package io.github.sethwillemsvg.lotrrevival.client.progression.fellowship;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Clears the {@link ClientFellowships} mirror when the client leaves a world. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
public final class ClientFellowshipEvents {
    private ClientFellowshipEvents() {}

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFellowships.clear();
    }
}
