package io.github.sethwillemsvg.lotrrevival.core.api;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client-side marker hook for the node map (specs/addon/node-map-travel.md, being written). Systems register a source of
 * markers; the node map, when it lands, draws {@link #all()}. Until then nothing reads it. Holds no client-only classes.
 */
public final class MapMarkers {
    /** One marker: x/z centre, radius in blocks, ARGB colour, a title for this viewer, flags. */
    public record Marker(String id, String kind, int x, int z, int radius, int color, Component title, boolean own,
                         boolean faded, long endsAtGameTime) {}

    public interface Source {
        List<Marker> markers();
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    private MapMarkers() {}

    public static void register(Source source) {
        SOURCES.add(source);
    }

    public static List<Marker> all() {
        List<Marker> out = new ArrayList<>();
        for (Source s : SOURCES) out.addAll(s.markers());
        return out;
    }
}
