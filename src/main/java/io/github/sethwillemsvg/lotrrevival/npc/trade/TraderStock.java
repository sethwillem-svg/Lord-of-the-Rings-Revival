package io.github.sethwillemsvg.lotrrevival.npc.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * Value of the {@code lotrrevival:trader_stock} entity attachment: both rolled offer lists and the value traded since
 * the last refresh. Mutable; logic lives in {@link TraderStocks}.
 */
public final class TraderStock {
    public static final Codec<TraderStock> CODEC = RecordCodecBuilder.create(i -> i.group(
                    TradeOffer.CODEC.listOf().optionalFieldOf("player_buys", List.of()).forGetter(s -> s.playerBuys),
                    TradeOffer.CODEC.listOf().optionalFieldOf("player_sells", List.of()).forGetter(s -> s.playerSells),
                    Codec.INT.optionalFieldOf("total_value", 0).forGetter(s -> s.totalValue),
                    Codec.INT.optionalFieldOf("decay_ticks", 0).forGetter(s -> s.decayTicks),
                    Codec.BOOL.optionalFieldOf("rolled", false).forGetter(s -> s.rolled))
            .apply(i, TraderStock::new));

    final List<TradeOffer> playerBuys;
    final List<TradeOffer> playerSells;
    int totalValue;
    int decayTicks;
    boolean rolled;

    public TraderStock() {
        this(List.of(), List.of(), 0, 0, false);
    }

    private TraderStock(List<TradeOffer> buys, List<TradeOffer> sells, int totalValue, int decayTicks, boolean rolled) {
        this.playerBuys = new ArrayList<>(buys);
        this.playerSells = new ArrayList<>(sells);
        this.totalValue = totalValue;
        this.decayTicks = decayTicks;
        this.rolled = rolled;
    }

    /** What the trader sells to the player. */
    public List<TradeOffer> playerBuys() {
        return playerBuys;
    }

    /** What the trader buys from the player. */
    public List<TradeOffer> playerSells() {
        return playerSells;
    }

    /** Value traded since the last refresh. */
    public int totalValue() {
        return totalValue;
    }

    /** Whether the lists have been rolled (an empty rolled list is a real result). */
    public boolean isRolled() {
        return rolled;
    }
}
