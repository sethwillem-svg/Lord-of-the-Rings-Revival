package io.github.sethwillemsvg.lotrrevival.client.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.api.MapMarkers;
import io.github.sethwillemsvg.lotrrevival.events.network.WorldEventsPayload;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Client copy of the world events the server sent (spec E9). Registered as a {@link MapMarkers} source: the node map draws these
 * once node-map-travel.md lands (icon per type, faction colour, own events highlighted, ended events faded).
 */
public final class EventMarkerCache {
    private static volatile List<WorldEventsPayload.Entry> entries = List.of();

    private EventMarkerCache() {}

    public static void init() {
        MapMarkers.register(EventMarkerCache::markers);
    }

    public static void receive(List<WorldEventsPayload.Entry> list) {
        entries = List.copyOf(list);
    }

    public static List<WorldEventsPayload.Entry> entries() {
        return entries;
    }

    private static List<MapMarkers.Marker> markers() {
        List<MapMarkers.Marker> out = new ArrayList<>();
        for (WorldEventsPayload.Entry e : entries) {
            out.add(new MapMarkers.Marker(e.id(), e.type(), e.x(), e.z(), e.radius(), e.color(), Component.translatable(e.titleKey()), e.own(),
                    !e.active(), e.end()));
        }
        return out;
    }
}
