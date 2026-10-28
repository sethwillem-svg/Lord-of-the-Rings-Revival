package io.github.sethwillemsvg.lotrrevival.client.progression;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Logs how many achievement display entries the synced registry delivered on login. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
public final class AchievementSyncLog {
    private AchievementSyncLog() {}

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        int count = event.getPlayer()
                .level()
                .registryAccess()
                .registry(AchievementEntry.REGISTRY)
                .map(registry -> registry.size())
                .orElse(-1);
        LOTRRevival.LOGGER.info("Achievement registry synced to client: {} entries", count);
    }
}
