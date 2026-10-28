package io.github.sethwillemsvg.lotrrevival.client.progression;

import io.github.sethwillemsvg.lotrrevival.progression.network.TitlesDeltaPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.TitlesFullPayload;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;

/**
 * Read-only client mirror of the local player's title state. Only the payload handlers write to it. Holds
 * plain data only (no client-only classes) so common code such as {@code Titles} may read it.
 */
public final class ClientTitles {
    private static boolean enabled = true;
    private static boolean strict;
    private static Optional<ResourceLocation> selected = Optional.empty();
    private static ChatFormatting color = ChatFormatting.WHITE;
    private static boolean feminine;
    private static final Set<ResourceLocation> GRANTED = new HashSet<>();

    private ClientTitles() {}

    public static synchronized void applyFull(TitlesFullPayload payload) {
        enabled = payload.enabled();
        strict = payload.strict();
        selected = payload.selected();
        color = parse(payload.color());
        feminine = payload.feminine();
        GRANTED.clear();
        GRANTED.addAll(payload.granted());
    }

    public static synchronized void applyDelta(TitlesDeltaPayload payload) {
        selected = payload.selected();
        color = parse(payload.color());
        feminine = payload.feminine();
        GRANTED.removeAll(payload.grantedRemoved());
        GRANTED.addAll(payload.grantedAdded());
    }

    private static ChatFormatting parse(String name) {
        ChatFormatting parsed = ChatFormatting.getByName(name);
        return parsed != null && parsed.isColor() ? parsed : ChatFormatting.WHITE;
    }

    /** Forgets everything (on disconnect). */
    public static synchronized void clear() {
        enabled = true;
        strict = false;
        selected = Optional.empty();
        color = ChatFormatting.WHITE;
        feminine = false;
        GRANTED.clear();
    }

    /** The server's {@code titlesEnabled}; the menu hides the Titles page when false. */
    public static synchronized boolean enabled() {
        return enabled;
    }

    /** The server's {@code strictFactionTitles}. */
    public static synchronized boolean strict() {
        return strict;
    }

    public static synchronized Optional<ResourceLocation> selected() {
        return selected;
    }

    public static synchronized ChatFormatting color() {
        return color;
    }

    public static synchronized boolean feminine() {
        return feminine;
    }

    public static synchronized Set<ResourceLocation> granted() {
        return Set.copyOf(GRANTED);
    }
}
