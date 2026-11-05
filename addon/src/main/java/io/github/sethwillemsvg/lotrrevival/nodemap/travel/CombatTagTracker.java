package io.github.sethwillemsvg.lotrrevival.nodemap.travel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;

/**
 * When each player last hurt, or was hurt by, a living entity (game time). Kept here instead of reading the vanilla
 * "last hurt" timestamps because those count entity ticks, which mock players in game tests never advance.
 */
public final class CombatTagTracker {
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    public void tag(Player player, long gameTime) {
        last.put(player.getUUID(), gameTime);
    }

    /** Ticks since the last combat, or -1 when never. */
    public long since(Player player, long gameTime) {
        Long t = last.get(player.getUUID());
        return t == null ? -1 : gameTime - t;
    }

    public boolean inCombat(Player player, long gameTime, int tagTicks) {
        long s = since(player, gameTime);
        return s >= 0 && s < tagTicks;
    }

    public void forget(Player player) {
        last.remove(player.getUUID());
    }
}
