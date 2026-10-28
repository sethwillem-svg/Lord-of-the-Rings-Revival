package io.github.sethwillemsvg.lotrrevival.client.progression.fellowship;

import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipView;
import io.github.sethwillemsvg.lotrrevival.progression.network.FellowshipPositionsPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.FellowshipsDeltaPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.FellowshipsFullPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only client mirror of the viewer's fellowships, pending invites and the co-member positions sent while the map
 * is open. Only the payload handlers write to it. Plain data only, so common code may read it.
 */
public final class ClientFellowships {
    private static final Map<UUID, FellowshipView.Entry> FELLOWSHIPS = new LinkedHashMap<>();
    private static List<FellowshipView.Invite> invites = List.of();
    private static List<FellowshipPositionsPayload.Entry> positions = List.of();

    private ClientFellowships() {}

    public static synchronized void applyFull(FellowshipsFullPayload payload) {
        FELLOWSHIPS.clear();
        payload.fellowships().forEach(entry -> FELLOWSHIPS.put(entry.id(), entry));
        invites = List.copyOf(payload.invites());
    }

    public static synchronized void applyDelta(FellowshipsDeltaPayload payload) {
        payload.removed().forEach(FELLOWSHIPS::remove);
        payload.upserted().forEach(entry -> FELLOWSHIPS.put(entry.id(), entry));
        invites = List.copyOf(payload.invites());
    }

    public static synchronized void applyPositions(FellowshipPositionsPayload payload) {
        positions = List.copyOf(payload.positions());
    }

    /** Forgets everything (on disconnect). */
    public static synchronized void clear() {
        FELLOWSHIPS.clear();
        invites = List.of();
        positions = List.of();
    }

    public static synchronized List<FellowshipView.Entry> fellowships() {
        return new ArrayList<>(FELLOWSHIPS.values());
    }

    public static synchronized Optional<FellowshipView.Entry> find(UUID id) {
        return Optional.ofNullable(FELLOWSHIPS.get(id));
    }

    public static synchronized List<FellowshipView.Invite> invites() {
        return invites;
    }

    /** Positions of co-members the server last sent; empty unless the map has been open recently. */
    public static synchronized List<FellowshipPositionsPayload.Entry> positions() {
        return positions;
    }
}
