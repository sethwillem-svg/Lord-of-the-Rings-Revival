package io.github.sethwillemsvg.lotrrevival.client.progression.shield;

import io.github.sethwillemsvg.lotrrevival.progression.network.ShieldSyncPayload;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Client mirror of the selected shield per player UUID (the local player included). Only the payload handler
 * writes to it. Plain data only, no client-only classes.
 */
public final class ClientShields {
    private static final Map<UUID, ResourceLocation> SELECTED = new ConcurrentHashMap<>();

    private ClientShields() {}

    public static void apply(ShieldSyncPayload payload) {
        if (payload.shield().isPresent()) {
            SELECTED.put(payload.player(), payload.shield().get());
        } else {
            SELECTED.remove(payload.player());
        }
    }

    public static Optional<ResourceLocation> get(UUID player) {
        return Optional.ofNullable(SELECTED.get(player));
    }

    /** Forgets everything (on disconnect). */
    public static void clear() {
        SELECTED.clear();
    }
}
