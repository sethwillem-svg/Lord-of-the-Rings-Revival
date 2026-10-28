package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * {@code lotrrevival:progression/fellowship_action}, serverbound: the Fellowships page asks for the same actions the
 * commands offer. {@code argument} is a name (fellowship, player) or an option id; {@code value} is the option value.
 */
public record FellowshipActionPayload(Action action, UUID fellowship, String argument, boolean value)
        implements CustomPacketPayload {
    public static final Type<FellowshipActionPayload> TYPE = new Type<>(LOTRRevival.id("progression/fellowship_action"));

    public static final int ARGUMENT_MAX = 64;

    public enum Action {
        CREATE,
        INVITE,
        ACCEPT,
        REJECT,
        LEAVE,
        REMOVE,
        PROMOTE,
        DEMOTE,
        TRANSFER,
        RENAME,
        DISBAND,
        ICON,
        OPTION
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, FellowshipActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
            FellowshipActionPayload::action,
            UUIDUtil.STREAM_CODEC,
            FellowshipActionPayload::fellowship,
            ByteBufCodecs.stringUtf8(ARGUMENT_MAX),
            FellowshipActionPayload::argument,
            ByteBufCodecs.BOOL,
            FellowshipActionPayload::value,
            FellowshipActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
