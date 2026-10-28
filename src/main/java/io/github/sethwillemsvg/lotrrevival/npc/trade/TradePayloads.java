package io.github.sethwillemsvg.lotrrevival.npc.trade;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The payload records of the trade screen. Ids follow {@code lotrrevival:npc/<name>}. */
public final class TradePayloads {
    private TradePayloads() {}

    /** Serverbound: buy one lot of the offer at this index of the buy list. */
    public record Buy(int containerId, int offer) implements CustomPacketPayload {
        public static final Type<Buy> TYPE = new Type<>(LOTRRevival.id("npc/trade_buy"));
        public static final StreamCodec<ByteBuf, Buy> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Buy::containerId, ByteBufCodecs.VAR_INT, Buy::offer, Buy::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Serverbound: sell every full lot held in the sell slots. */
    public record Sell(int containerId) implements CustomPacketPayload {
        public static final Type<Sell> TYPE = new Type<>(LOTRRevival.id("npc/trade_sell"));
        public static final StreamCodec<ByteBuf, Sell> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Sell::containerId, Sell::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Serverbound: break (combine false) or combine the coins in the exchange slot. */
    public record CoinExchange(int containerId, boolean combine) implements CustomPacketPayload {
        public static final Type<CoinExchange> TYPE = new Type<>(LOTRRevival.id("npc/coin_exchange"));
        public static final StreamCodec<ByteBuf, CoinExchange> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CoinExchange::containerId, ByteBufCodecs.BOOL, CoinExchange::combine, CoinExchange::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Clientbound: both offer lists of the trader, sent to every viewer when they change. */
    public record Stock(int containerId, List<OfferView> buys, List<OfferView> sells) implements CustomPacketPayload {
        public static final Type<Stock> TYPE = new Type<>(LOTRRevival.id("npc/trade_stock"));
        private static final StreamCodec<ByteBuf, List<OfferView>> LIST =
                OfferView.STREAM_CODEC.apply(ByteBufCodecs.list(CoreNetwork.MAX_COLLECTION));
        public static final StreamCodec<ByteBuf, Stock> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Stock::containerId, LIST, Stock::buys, LIST, Stock::sells, Stock::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
