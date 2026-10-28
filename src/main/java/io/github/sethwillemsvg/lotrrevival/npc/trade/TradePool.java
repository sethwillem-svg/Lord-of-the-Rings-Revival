package io.github.sethwillemsvg.lotrrevival.npc.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.List;
import java.util.Map;

/**
 * The pools of one trader role from {@code npc_trades/<type>.json}.
 *
 * @param playerBuys what the trader sells to the player
 * @param playerSells what the trader buys from the player
 */
public record TradePool(List<TradeEntry> playerBuys, List<TradeEntry> playerSells) {
    public static final TradePool EMPTY = new TradePool(List.of(), List.of());

    public static final Codec<TradePool> CODEC = RecordCodecBuilder.create(i -> i.group(
                    TradeEntry.CODEC.listOf().optionalFieldOf("player_buys", List.of()).forGetter(TradePool::playerBuys),
                    TradeEntry.CODEC.listOf().optionalFieldOf("player_sells", List.of()).forGetter(TradePool::playerSells))
            .apply(i, TradePool::new));

    /** One file: role name to pool. */
    public static final Codec<Map<NpcRole, TradePool>> TABLE_CODEC = Codec.unboundedMap(NpcRole.CODEC, CODEC);

    public TradePool {
        playerBuys = List.copyOf(playerBuys);
        playerSells = List.copyOf(playerSells);
    }
}
