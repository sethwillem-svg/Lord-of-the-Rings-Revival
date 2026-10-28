package io.github.sethwillemsvg.lotrrevival.client.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.progression.network.OpenQuestOfferPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestOfferClosedPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestOfferMarkerPayload;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client end of the quest offer payloads. The offer screen and the marker renderer are the next ticket; for now the
 * handlers keep the marker state as plain data and log, so the protocol is exercised end to end. Plain data only,
 * no client-only classes.
 */
public final class ClientQuestOffers {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientQuestOffers.class);

    private static final Map<Integer, QuestOfferMarkerPayload> MARKERS = new HashMap<>();

    private ClientQuestOffers() {}

    /** Marker state per entity id (only entities that currently have one). */
    public static Map<Integer, QuestOfferMarkerPayload> markers() {
        return MARKERS;
    }

    public static void onMarker(QuestOfferMarkerPayload payload) {
        if (payload.group().isPresent()) {
            MARKERS.put(payload.entityId(), payload);
        } else {
            MARKERS.remove(payload.entityId());
        }
        LOGGER.debug("Quest offer marker for entity {}: {}", payload.entityId(), payload.group());
    }

    public static void onOpen(OpenQuestOfferPayload payload) {
        LOGGER.debug(
                "Open quest offer from entity {}: {} x{} ({})",
                payload.entityId(),
                payload.template(),
                payload.count(),
                payload.openingKey());
    }

    public static void onClosed(QuestOfferClosedPayload payload) {
        LOGGER.debug("Quest offer screen of entity {} closed: {}", payload.entityId(), payload.reason());
    }

    public static void reset() {
        MARKERS.clear();
    }
}
