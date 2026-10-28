package io.github.sethwillemsvg.lotrrevival.npc.spawn;

/**
 * Which NPCs the vanilla monster despawn rules may remove. The distances and odds themselves are vanilla's (the
 * entity types use the misc category: instant beyond 128 blocks, 1 in 800 per tick after 600 idle ticks with no
 * player within 32); this decides who takes part.
 */
public final class NpcDespawn {
    private NpcDespawn() {}

    /** Only wild NPCs despawn. Home or anchor-bound, name-tagged, hired and player-targeting NPCs never do. */
    public static boolean mayDespawn(
            boolean wild, boolean homeBound, boolean nameTagged, boolean hired, boolean targetingPlayer) {
        return wild && !homeBound && !nameTagged && !hired && !targetingPlayer;
    }
}
