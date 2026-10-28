package io.github.sethwillemsvg.lotrrevival.npc.hire.horn;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.trade.PayloadLimiter;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Serverbound payloads of the horn and sword screens. Each checks that the sender is alive, within the rate limit and
 * really holds the right item in the named hand.
 */
public final class HornPayloads {
    private HornPayloads() {}

    /** Picks the mode of the horn in the named hand. */
    public record SetMode(boolean offhand, HornMode mode) implements CustomPacketPayload {
        public static final Type<SetMode> TYPE = new Type<>(LOTRRevival.id("npc/horn_mode"));
        public static final StreamCodec<ByteBuf, SetMode> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SetMode::offhand, HornMode.STREAM_CODEC, SetMode::mode, SetMode::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Renames the squadron of the horn or sword in the named hand. */
    public record Rename(boolean offhand, String squadron) implements CustomPacketPayload {
        public static final Type<Rename> TYPE = new Type<>(LOTRRevival.id("npc/squadron_rename"));
        public static final StreamCodec<ByteBuf, Rename> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL,
                Rename::offhand,
                ByteBufCodecs.stringUtf8(HiredUnit.SQUADRON_MAX),
                Rename::squadron,
                Rename::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = CoreNetwork.registrar(event);
        registrar.playToServer(SetMode.TYPE, SetMode.STREAM_CODEC, (payload, ctx) -> {
            ServerPlayer player = allowed(ctx, SetMode.TYPE.id());
            if (player != null) {
                applyMode(player, payload.offhand(), payload.mode());
            }
        });
        registrar.playToServer(Rename.TYPE, Rename.STREAM_CODEC, (payload, ctx) -> {
            ServerPlayer player = allowed(ctx, Rename.TYPE.id());
            if (player != null) {
                applyRename(player, payload.offhand(), payload.squadron());
            }
        });
    }

    private static ServerPlayer allowed(IPayloadContext ctx, ResourceLocation id) {
        if (ctx.player() instanceof ServerPlayer player && player.isAlive() && PayloadLimiter.allow(player, id)) {
            return player;
        }
        return null;
    }

    /** Sets the mode if the named hand holds a command horn. */
    public static boolean applyMode(ServerPlayer player, boolean offhand, HornMode mode) {
        ItemStack stack = player.getItemInHand(hand(offhand));
        if (!stack.is(HornItems.COMMAND_HORN.get())) {
            return false;
        }
        stack.set(HornComponents.HORN_MODE.get(), mode);
        return true;
    }

    /** Sets the squadron if the named hand holds a command horn or sword. */
    public static boolean applyRename(ServerPlayer player, boolean offhand, String squadron) {
        ItemStack stack = player.getItemInHand(hand(offhand));
        if (!stack.is(HornItems.COMMAND_HORN.get()) && !stack.is(HornItems.COMMAND_SWORD.get())) {
            return false;
        }
        String cleaned = Squadrons.clean(squadron);
        if (cleaned.isEmpty()) {
            stack.remove(HornComponents.SQUADRON.get());
        } else {
            stack.set(HornComponents.SQUADRON.get(), cleaned);
        }
        return true;
    }

    private static InteractionHand hand(boolean offhand) {
        return offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
}
