package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.world.WorldConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** The per-player travel clock: +1 per online tick, offline credit on login (dedicated servers only). */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class TravelClock {
    private TravelClock() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TravelData data = TravelData.get(player);
            if (data.clock() < TravelData.MAX_CLOCK) {
                TravelData.set(player, data.addClock(1));
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyOfflineCredit(player, player.server.isDedicatedServer(), System.currentTimeMillis());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recordLogout(player, System.currentTimeMillis());
        }
    }

    public static void recordLogout(ServerPlayer player, long nowMillis) {
        TravelData.set(player, TravelData.get(player).withLastLogout(nowMillis));
    }

    /**
     * Adds the offline credit to the clock and clears the stored logout time so a crash cannot credit twice.
     *
     * @return the ticks credited
     */
    public static long applyOfflineCredit(ServerPlayer player, boolean dedicatedServer, long nowMillis) {
        TravelData data = TravelData.get(player);
        long credit = TravelRules.offlineCreditTicks(
                WorldConfig.get(WorldConfig.TRAVEL_OFFLINE_FRACTION), data.lastLogout(), nowMillis, dedicatedServer);
        TravelData.set(player, data.addClock(credit).withLastLogout(0L));
        return credit;
    }
}
