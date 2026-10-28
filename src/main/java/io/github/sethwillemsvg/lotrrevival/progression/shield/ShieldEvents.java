package io.github.sethwillemsvg.lotrrevival.progression.shield;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Game-bus listeners of shields (login, respawn and dimension change are handled by the progression sync). */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class ShieldEvents {
    private ShieldEvents() {}

    /** A player starts tracking another player: tell them the other's shield. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer tracker && event.getTarget() instanceof ServerPlayer target) {
            ShieldService.syncTo(tracker, target);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShieldService.forget(player);
        }
    }
}
