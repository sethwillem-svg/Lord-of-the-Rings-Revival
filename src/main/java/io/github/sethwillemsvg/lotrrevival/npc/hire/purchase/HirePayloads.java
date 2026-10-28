package io.github.sethwillemsvg.lotrrevival.npc.hire.purchase;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradeMenu;
import io.github.sethwillemsvg.lotrrevival.npc.trade.NpcTradePayloads;
import io.github.sethwillemsvg.lotrrevival.npc.trade.PayloadLimiter;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payloads of the Units tab: {@code npc/hire_unit} (serverbound) and {@code npc/hire_units} (clientbound rows). */
public final class HirePayloads {
    private HirePayloads() {}

    /** Serverbound: hire the entry with the given id, with the squadron text typed on the screen. */
    public record HireUnit(int containerId, String entryId, String squadron) implements CustomPacketPayload {
        public static final Type<HireUnit> TYPE = new Type<>(LOTRRevival.id("npc/hire_unit"));
        public static final StreamCodec<ByteBuf, HireUnit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                HireUnit::containerId,
                ByteBufCodecs.stringUtf8(64),
                HireUnit::entryId,
                ByteBufCodecs.stringUtf8(1024),
                HireUnit::squadron,
                HireUnit::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Clientbound: the rows of the Units tab for this viewer, re-sent when they change. */
    public record Units(int containerId, List<UnitRowView> rows) implements CustomPacketPayload {
        public static final Type<Units> TYPE = new Type<>(LOTRRevival.id("npc/hire_units"));
        public static final StreamCodec<ByteBuf, Units> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                Units::containerId,
                UnitRowView.STREAM_CODEC.apply(ByteBufCodecs.list(CoreNetwork.MAX_COLLECTION)),
                Units::rows,
                Units::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Called from {@code NpcTradePayloads.register} with its registrar. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(HireUnit.TYPE, HireUnit.STREAM_CODEC, (payload, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer player) || !player.isAlive()) {
                return;
            }
            if (!PayloadLimiter.allow(player, payload.type().id())) {
                return;
            }
            NpcTradeMenu menu = NpcTradePayloads.openMenu(player, payload.containerId());
            if (menu != null && menu.npc() != null && menu.hasTab(NpcTradeMenu.Tab.UNITS)) {
                HirePurchase.hire(player, menu.npc(), payload.entryId(), payload.squadron());
            }
        });
        registrar.playToClient(Units.TYPE, Units.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player().containerMenu instanceof NpcTradeMenu menu && menu.containerId == payload.containerId()) {
                menu.applyUnits(payload.rows());
            }
        });
    }
}
