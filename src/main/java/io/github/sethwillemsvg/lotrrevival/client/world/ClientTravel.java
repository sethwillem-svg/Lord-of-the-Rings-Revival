package io.github.sethwillemsvg.lotrrevival.client.world;

import io.github.sethwillemsvg.lotrrevival.world.network.TravelSyncPayload;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelData;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Read-only client mirror of the local player's travel data. Only the {@code world/travel_sync} handler writes to it
 * ({@link #apply}) and the client tick advances the clock ({@link #tick}). Holds plain data only (no client-only
 * classes), so tests and common code may use it. The latest {@code world/travel_status} is kept separately in
 * {@link io.github.sethwillemsvg.lotrrevival.world.network.TravelStatusMirror}.
 *
 * <p>A sync can arrive in several parts; the data is swapped in when the last part lands, so readers never see a
 * half-built custom list.
 */
public final class ClientTravel {
    private static TravelData data = TravelData.EMPTY;
    private static int limit;

    // Parts of a sync in progress.
    private static List<ResourceLocation> pendingRegions = new ArrayList<>();
    private static Map<ResourceLocation, Integer> pendingUses = new HashMap<>();
    private static List<TravelData.Custom> pendingCustom = new ArrayList<>();

    private ClientTravel() {}

    /** Takes in one part of a full sync. The first part starts afresh, the last part commits. */
    public static synchronized void apply(TravelSyncPayload payload) {
        if (payload.first()) {
            pendingRegions = new ArrayList<>(payload.regions());
            pendingUses = new HashMap<>(payload.uses());
            pendingCustom = new ArrayList<>();
        }
        pendingCustom.addAll(payload.custom());
        if (!payload.last()) {
            return;
        }
        int nextId = 0;
        for (TravelData.Custom custom : pendingCustom) {
            nextId = Math.max(nextId, custom.id() + 1);
        }
        data = new TravelData(
                new HashSet<>(pendingRegions),
                pendingUses,
                pendingCustom,
                nextId,
                payload.clock(),
                payload.lastRef().flatMap(WaypointRef::tryParse),
                0L);
        limit = Math.max(0, payload.limit());
        pendingRegions = new ArrayList<>();
        pendingUses = new HashMap<>();
        pendingCustom = new ArrayList<>();
    }

    /** One client tick: the clock runs on between syncs (the server counts the same ticks). */
    public static synchronized void tick() {
        if (data.clock() < TravelData.MAX_CLOCK) {
            data = data.addClock(1);
        }
    }

    /** The synced travel data with the locally advanced clock. {@code nextCustomId} is derived, {@code lastLogout} 0. */
    public static synchronized TravelData data() {
        return data;
    }

    /** The travel clock in ticks. */
    public static synchronized int clock() {
        return data.clock();
    }

    /** Maximum number of custom waypoints, as of the last sync. */
    public static synchronized int limit() {
        return limit;
    }

    /** Forgets everything (on disconnect). */
    public static synchronized void clear() {
        data = TravelData.EMPTY;
        limit = 0;
        pendingRegions = new ArrayList<>();
        pendingUses = new HashMap<>();
        pendingCustom = new ArrayList<>();
    }
}
