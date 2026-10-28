package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.core.coin.Coins;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Quest coin payout: the fewest coins, dropped 0.5 blocks above the giver's feet, else straight to the player. */
public final class QuestCoinPayout {
    /** The giver must be loaded in the player's level within this many blocks for the coins to drop at it. */
    public static final double DROP_RANGE = 16.0;

    private QuestCoinPayout() {}

    public static void pay(ServerPlayer player, int coins, UUID giver) {
        if (coins <= 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(giver);
        if (entity != null && entity.isAlive() && entity.distanceToSqr(player) <= DROP_RANGE * DROP_RANGE) {
            Coins.drop(level, entity.position().add(0.0, 0.5, 0.0), coins);
        } else {
            Coins.give(player, coins);
        }
    }
}
