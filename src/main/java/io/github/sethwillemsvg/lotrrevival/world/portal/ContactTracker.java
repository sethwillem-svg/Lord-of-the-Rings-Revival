package io.github.sethwillemsvg.lotrrevival.world.portal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Counts consecutive ticks of contact per entity; anything that stops touching starts over. */
public final class ContactTracker {
    private final Map<UUID, Integer> ticks = new HashMap<>();

    /**
     * Advances one tick. {@code touching} maps each entity touching this tick to the contact ticks it needs.
     *
     * @return the entities that reached their requirement this tick (their counters are cleared)
     */
    public List<UUID> tick(Map<UUID, Integer> touching) {
        ticks.keySet().retainAll(touching.keySet());
        List<UUID> reached = new ArrayList<>();
        touching.forEach((id, required) -> {
            int count = ticks.merge(id, 1, Integer::sum);
            if (count >= required) {
                reached.add(id);
                ticks.remove(id);
            }
        });
        return reached;
    }

    public int contactOf(UUID id) {
        return ticks.getOrDefault(id, 0);
    }
}
