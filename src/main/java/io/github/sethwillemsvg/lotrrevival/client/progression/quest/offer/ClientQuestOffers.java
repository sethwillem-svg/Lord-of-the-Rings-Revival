package io.github.sethwillemsvg.lotrrevival.client.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.progression.network.OpenQuestOfferPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestOfferClosedPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestOfferMarkerPayload;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client end of the quest offer payloads: keeps the marker state read by {@link OfferMarkerRenderer} and hands the
 * open and close payloads to the {@link OfferScreen}.
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

    /** Opens the offer screen. Runs only on a client (the payload handler is a client-side lambda). */
    public static void onOpen(OpenQuestOfferPayload payload) {
        OfferScreen.open(payload);
    }

    public static void onClosed(QuestOfferClosedPayload payload) {
        OfferScreen.closeFor(payload);
    }

    public static void reset() {
        MARKERS.clear();
    }
}
