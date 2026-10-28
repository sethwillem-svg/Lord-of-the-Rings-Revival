package io.github.sethwillemsvg.lotrrevival.world.network;

import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.CustomWaypoints;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelData;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelRules;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of {@code world/travel_sync}: builds the full sync of a player's travel data and sends it. Callers send
 * after every change that is not the plain clock ticking (unlock, clock set, custom edit, arrival) and the event
 * handlers in {@link TravelSyncEvents} send on login, respawn and dimension change.
 */
public final class TravelSync {
    /** Custom limit each player was last sent, so a changed limit (new achievements) triggers a resync. */
    private static final Map<UUID, Integer> SENT_LIMIT = new HashMap<>();

    private TravelSync() {}

    /**
     * Splits the data into the payloads of one full sync, in send order. Never empty. Names longer than the wire limit
     * are cut (stored names are validated to 32 characters, but a hand-edited save could hold more).
     */
    public static List<TravelSyncPayload> parts(TravelData data, int limit) {
        List<TravelData.Custom> customs = new ArrayList<>(data.custom().size());
        for (TravelData.Custom custom : data.custom()) {
            String name = custom.name().length() > CustomWaypoints.MAX_NAME
                    ? custom.name().substring(0, CustomWaypoints.MAX_NAME)
                    : custom.name();
            customs.add(new TravelData.Custom(custom.id(), name, custom.pos(), custom.uses()));
        }
        List<ResourceLocation> regions = data.regions().stream()
                .sorted()
                .limit(CoreNetwork.MAX_COLLECTION)
                .toList();
        Map<ResourceLocation, Integer> uses = new HashMap<>();
        data.uses().entrySet().stream()
                .limit(CoreNetwork.MAX_COLLECTION)
                .forEach(e -> uses.put(e.getKey(), e.getValue()));
        java.util.Optional<String> last = data.last().map(WaypointRef::wire);

        int size = TravelSyncPayload.PART_SIZE;
        int partCount = Math.max(1, (customs.size() + size - 1) / size);
        List<TravelSyncPayload> parts = new ArrayList<>(partCount);
        for (int i = 0; i < partCount; i++) {
            boolean first = i == 0;
            parts.add(new TravelSyncPayload(
                    first,
                    i == partCount - 1,
                    first ? regions : List.of(),
                    first ? uses : Map.of(),
                    customs.subList(i * size, Math.min(customs.size(), (i + 1) * size)),
                    data.clock(),
                    last,
                    limit));
        }
        return parts;
    }

    /** Whether the player is a real connection that has the sync channel (test players are not). */
    public static boolean canSend(ServerPlayer player) {
        return !player.isFakePlayer()
                && player.connection != null
                && player.connection.hasChannel(TravelSyncPayload.TYPE);
    }

    /** Sends the player's full travel sync. Does nothing for test players without a connection. */
    public static void send(ServerPlayer player) {
        if (!canSend(player)) {
            return;
        }
        int limit = TravelRules.customLimit(player);
        SENT_LIMIT.put(player.getUUID(), limit);
        for (TravelSyncPayload part : parts(TravelData.get(player), limit)) {
            PacketDistributor.sendToPlayer(player, part);
        }
    }

    /** Resends when the custom limit changed since the last sync (the player earned or lost achievements). */
    static void resyncIfLimitChanged(ServerPlayer player) {
        Integer sent = SENT_LIMIT.get(player.getUUID());
        if (sent != null && sent != TravelRules.customLimit(player)) {
            send(player);
        }
    }

    static void forget(ServerPlayer player) {
        SENT_LIMIT.remove(player.getUUID());
    }
}
