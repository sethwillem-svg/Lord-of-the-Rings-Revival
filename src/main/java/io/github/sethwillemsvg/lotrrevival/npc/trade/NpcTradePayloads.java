package io.github.sethwillemsvg.lotrrevival.npc.trade;

import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Registers the trade screen payloads. Every serverbound handler checks: sender alive, within the rate limit, has this
 * trade menu open (matching container id) and the menu is still valid (12 blocks, trader alive and calm, not hostile).
 * Anything else is dropped.
 */
public final class NpcTradePayloads {
    private static final Logger LOGGER = LogUtils.getLogger();

    private NpcTradePayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = CoreNetwork.registrar(event);
        registrar.playToServer(
                TradePayloads.Buy.TYPE,
                TradePayloads.Buy.STREAM_CODEC,
                (payload, ctx) -> handle(ctx, payload.type(), payload.containerId(), (player, menu) -> menu.buy(player, payload.offer())));
        registrar.playToServer(
                TradePayloads.Sell.TYPE,
                TradePayloads.Sell.STREAM_CODEC,
                (payload, ctx) -> handle(ctx, payload.type(), payload.containerId(), (player, menu) -> menu.sell(player)));
        registrar.playToServer(
                TradePayloads.CoinExchange.TYPE,
                TradePayloads.CoinExchange.STREAM_CODEC,
                (payload, ctx) -> handle(
                        ctx, payload.type(), payload.containerId(), (player, menu) -> menu.exchange(player, payload.combine())));
        io.github.sethwillemsvg.lotrrevival.npc.hire.purchase.HirePayloads.register(registrar);
        registrar.playToClient(TradePayloads.Stock.TYPE, TradePayloads.Stock.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player().containerMenu instanceof NpcTradeMenu menu && menu.containerId == payload.containerId()) {
                menu.applyStock(payload.buys(), payload.sells());
            }
        });
    }

    private static void handle(
            IPayloadContext ctx,
            CustomPacketPayload.Type<?> type,
            int containerId,
            BiConsumer<ServerPlayer, NpcTradeMenu> action) {
        if (!(ctx.player() instanceof ServerPlayer player) || !player.isAlive()) {
            return;
        }
        if (!PayloadLimiter.allow(player, type.id())) {
            LOGGER.debug("Dropped {} from {}: rate limit", type.id(), player.getGameProfile().getName());
            return;
        }
        NpcTradeMenu menu = openMenu(player, containerId);
        if (menu != null) {
            action.accept(player, menu);
        }
    }

    /** The trade menu the player has open with this container id, if it is still valid; else null. */
    @Nullable
    public static NpcTradeMenu openMenu(ServerPlayer player, int containerId) {
        if (player.containerMenu instanceof NpcTradeMenu menu
                && menu.containerId == containerId
                && menu.stillValid(player)) {
            return menu;
        }
        return null;
    }
}
