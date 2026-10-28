package io.github.sethwillemsvg.lotrrevival.core.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * Implemented by entities that come along when a player fast travels, for example hired units set to
 * follow and owned by that player. Owner: Core (interface). Implemented by the NPC pod; called by the
 * World pod's fast travel code when it gathers companions around the departing player.
 */
public interface TravelCompanion {
    /** Whether this entity should travel with {@code player} (it is theirs and currently following). */
    boolean travelsWith(ServerPlayer player);
}
