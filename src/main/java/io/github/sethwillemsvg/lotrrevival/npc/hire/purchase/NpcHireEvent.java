package io.github.sethwillemsvg.lotrrevival.npc.hire.purchase;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Posted on {@code NeoForge.EVENT_BUS} after a unit was hired: payment taken, unit spawned and marked as hired. Not
 * cancellable.
 */
public class NpcHireEvent extends Event {
    private final ServerPlayer player;
    private final NpcEntity trader;
    private final NpcEntity unit;
    private final String entryId;
    private final int cost;

    public NpcHireEvent(ServerPlayer player, NpcEntity trader, NpcEntity unit, String entryId, int cost) {
        this.player = player;
        this.trader = trader;
        this.unit = unit;
        this.entryId = entryId;
        this.cost = cost;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public NpcEntity getTrader() {
        return trader;
    }

    /** The freshly hired unit. */
    public NpcEntity getUnit() {
        return unit;
    }

    /** Id of the unit list entry that was hired. */
    public String getEntryId() {
        return entryId;
    }

    /** Coins paid. */
    public int getCost() {
        return cost;
    }
}
