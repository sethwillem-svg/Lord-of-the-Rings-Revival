package io.github.sethwillemsvg.lotrrevival.npc.interact;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface only: what a hireable NPC exposes. The hiring spec supplies implementations, screens, costs and commands.
 * Hired NPCs never despawn, never target their owner and heal like traders.
 */
public interface Hireable {
    /** What a hired unit has been told to do. */
    enum CommandState {
        NONE,
        FOLLOW,
        GUARD_POS
    }

    /** Minimum alignment with the unit's faction needed to hire it. */
    float hireMin();

    /** Cost in silver coins. */
    int hireCost();

    /** The owning player, empty while unhired. */
    Optional<UUID> owner();

    CommandState commandState();

    /** Whether this NPC currently counts as hired. */
    boolean isHired();
}
