package io.github.sethwillemsvg.lotrrevival.core.faction;

import java.util.Optional;
import java.util.UUID;

/** Implemented by hired units and fighting mounts: kills they make are credited to a player (for hire tracking). */
public interface AlignmentCreditor {
    Optional<UUID> creditedPlayer();
}
