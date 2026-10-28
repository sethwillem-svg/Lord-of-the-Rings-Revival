package io.github.sethwillemsvg.lotrrevival.npc.trade;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

/**
 * Posted on {@code NeoForge.EVENT_BUS} once per traded lot, after payment and stock bookkeeping. Not cancellable.
 * {@code stack} is a copy of the lot (what the player received for BUY, what the trader took for SELL).
 */
public class NpcTradeEvent extends Event {
    /** Which way the goods went. */
    public enum Direction {
        BUY,
        SELL
    }

    private final ServerPlayer player;
    private final NpcEntity trader;
    private final Direction direction;
    private final ItemStack stack;
    private final int price;

    public NpcTradeEvent(ServerPlayer player, NpcEntity trader, Direction direction, ItemStack stack, int price) {
        this.player = player;
        this.trader = trader;
        this.direction = direction;
        this.stack = stack;
        this.price = price;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public NpcEntity getTrader() {
        return trader;
    }

    public Direction getDirection() {
        return direction;
    }

    public ItemStack getStack() {
        return stack;
    }

    /** Coin value of the lot. */
    public int getPrice() {
        return price;
    }
}
