package io.github.sethwillemsvg.lotrrevival.progression.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Posted on the NeoForge game bus (server side) when a player's {@link ProgressionApi#achievementCount} changed.
 * Fired once per change at the end of the tick, after the new value was computed.
 */
public class AchievementCountChangedEvent extends Event {
    private final ServerPlayer player;
    private final int oldCount;
    private final int newCount;

    public AchievementCountChangedEvent(ServerPlayer player, int oldCount, int newCount) {
        this.player = player;
        this.oldCount = oldCount;
        this.newCount = newCount;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public int getOldCount() {
        return oldCount;
    }

    public int getNewCount() {
        return newCount;
    }
}
