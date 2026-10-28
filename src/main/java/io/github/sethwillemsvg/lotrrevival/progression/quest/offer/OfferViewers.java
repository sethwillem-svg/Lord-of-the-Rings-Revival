package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Players who currently have a giver's offer screen open. While a giver has any viewer its offer does not age.
 * Server thread only; entries are removed on close, logout, death, dimension change and distance.
 */
public final class OfferViewers {
    private static final Map<UUID, Set<UUID>> VIEWERS = new HashMap<>();

    private OfferViewers() {}

    public static void add(UUID giver, UUID player) {
        VIEWERS.computeIfAbsent(giver, key -> new HashSet<>()).add(player);
    }

    public static void remove(UUID giver, UUID player) {
        Set<UUID> set = VIEWERS.get(giver);
        if (set != null) {
            set.remove(player);
            if (set.isEmpty()) {
                VIEWERS.remove(giver);
            }
        }
    }

    /** Removes the player from every giver (logout, death, dimension change). */
    public static void removePlayer(UUID player) {
        VIEWERS.values().forEach(set -> set.remove(player));
        VIEWERS.values().removeIf(Set::isEmpty);
    }

    public static boolean has(UUID giver) {
        return VIEWERS.containsKey(giver);
    }

    public static boolean isViewing(UUID giver, UUID player) {
        Set<UUID> set = VIEWERS.get(giver);
        return set != null && set.contains(player);
    }

    /** A copy of the giver's viewers. */
    public static Set<UUID> viewers(UUID giver) {
        Set<UUID> set = VIEWERS.get(giver);
        return set == null ? Set.of() : new HashSet<>(set);
    }

    public static void clear(UUID giver) {
        VIEWERS.remove(giver);
    }
}
