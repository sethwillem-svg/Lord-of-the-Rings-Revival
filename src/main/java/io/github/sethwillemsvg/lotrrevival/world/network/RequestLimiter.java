package io.github.sethwillemsvg.lotrrevival.world.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Core's c2s rate limit for the world payloads: 20 per second per player per payload type, excess dropped. */
public final class RequestLimiter {
    public static final int MAX_PER_SECOND = 20;
    private static final int WINDOW_TICKS = 20;

    private record Key(UUID player, ResourceLocation payload) {}

    private static final Map<Key, long[]> WINDOWS = new HashMap<>();

    static {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                WINDOWS.keySet().removeIf(key -> key.player().equals(event.getEntity().getUUID())));
    }

    private RequestLimiter() {}

    /** Whether this payload may be handled now; counts it. */
    public static boolean allow(ServerPlayer player, ResourceLocation payload) {
        long now = player.level().getGameTime();
        long[] window = WINDOWS.computeIfAbsent(new Key(player.getUUID(), payload), k -> new long[] {now, 0});
        if (now - window[0] >= WINDOW_TICKS || now < window[0]) {
            window[0] = now;
            window[1] = 0;
        }
        return ++window[1] <= MAX_PER_SECOND;
    }
}
