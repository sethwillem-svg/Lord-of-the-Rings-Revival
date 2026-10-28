package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Serverbound: choose a shield, or none. The server checks the unlock and drops invalid requests. */
public record SelectShieldPayload(Optional<ResourceLocation> shield) implements CustomPacketPayload {
    public static final Type<SelectShieldPayload> TYPE = new Type<>(LOTRRevival.id("progression/select_shield"));

    public static final StreamCodec<ByteBuf, SelectShieldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(CoreNetwork.ID_CODEC), SelectShieldPayload::shield, SelectShieldPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
