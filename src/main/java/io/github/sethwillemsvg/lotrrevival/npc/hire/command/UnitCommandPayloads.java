package io.github.sethwillemsvg.lotrrevival.npc.hire.command;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.github.sethwillemsvg.lotrrevival.npc.trade.PayloadLimiter;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * The payloads of the unit command screen. The serverbound command is dropped unless the sender is alive, within the
 * rate limit, has this menu open (matching container id), the menu is still valid (owner of the unit, unit alive and
 * within 12 blocks) and the action and value are known and in range.
 */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class UnitCommandPayloads {
    private static final Logger LOGGER = LogUtils.getLogger();

    private UnitCommandPayloads() {}

    /** Serverbound: an action (a {@link UnitCommandMenu.Action} id) with a value (the range for range actions). */
    public record Command(int containerId, int action, int value) implements CustomPacketPayload {
        public static final Type<Command> TYPE = new Type<>(LOTRRevival.id("npc/unit_command"));
        public static final StreamCodec<ByteBuf, Command> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                Command::containerId,
                ByteBufCodecs.VAR_INT,
                Command::action,
                ByteBufCodecs.VAR_INT,
                Command::value,
                Command::new);

        public Command(int containerId, UnitCommandMenu.Action action, int value) {
            this(containerId, action.ordinal(), value);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Clientbound: the values the screen shows, sent to the owner when they change. */
    public record Info(int containerId, UnitInfo info) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(LOTRRevival.id("npc/unit_info"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Info::containerId, UnitInfo.STREAM_CODEC, Info::info, Info::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = CoreNetwork.registrar(event);
        registrar.playToServer(Command.TYPE, Command.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                handle(player, payload.containerId(), payload.action(), payload.value());
            }
        });
        registrar.playToClient(Info.TYPE, Info.STREAM_CODEC, (payload, ctx) -> {
            if (ctx.player().containerMenu instanceof UnitCommandMenu menu && menu.containerId == payload.containerId()) {
                menu.applyInfo(payload.info());
            }
        });
    }

    /**
     * The server side of {@link Command}: checks the sender and the menu, then applies the action.
     *
     * @return whether the action was applied
     */
    public static boolean handle(ServerPlayer player, int containerId, int actionId, int value) {
        if (!player.isAlive()) {
            return false;
        }
        if (!PayloadLimiter.allow(player, Command.TYPE.id())) {
            LOGGER.debug("Dropped {} from {}: rate limit", Command.TYPE.id(), player.getGameProfile().getName());
            return false;
        }
        UnitCommandMenu.Action action = UnitCommandMenu.Action.byId(actionId);
        if (action == null
                || !(player.containerMenu instanceof UnitCommandMenu menu)
                || menu.containerId != containerId
                || !menu.stillValid(player)) {
            return false;
        }
        return menu.apply(action, value);
    }
}
