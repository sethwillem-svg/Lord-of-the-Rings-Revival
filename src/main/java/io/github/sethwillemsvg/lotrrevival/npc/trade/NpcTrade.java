package io.github.sethwillemsvg.lotrrevival.npc.trade;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Registration of the trade data listener, the {@code trader_stock} attachment and its events. */
public final class NpcTrade {
    /** {@code lotrrevival:trader_stock}: serialized, not synced. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TraderStock>> TRADER_STOCK =
            ModRegistries.ATTACHMENT_TYPES.register(
                    "trader_stock",
                    () -> AttachmentType.builder(TraderStock::new)
                            .serialize(TraderStock.CODEC)
                            .build());

    private NpcTrade() {}

    /** Called once from {@code NpcMod.init}. */
    public static void init() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new NpcTradeData()));
        NeoForge.EVENT_BUS.addListener(NpcTrade::onJoin);
        NeoForge.EVENT_BUS.addListener(NpcTrade::onTick);
    }

    /** Traders roll their stock when they enter the world after spawning (loaded traders already have it). */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof NpcEntity npc && npc.getRole().isTrader()) {
            TraderStocks.stock(npc);
        }
    }

    private static void onTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof NpcEntity npc && !npc.level().isClientSide && npc.hasData(TRADER_STOCK)) {
            TraderStocks.tick(npc.getData(TRADER_STOCK));
        }
    }
}
