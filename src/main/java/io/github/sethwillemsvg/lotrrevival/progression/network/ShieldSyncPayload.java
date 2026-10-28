package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Clientbound: the shield a player has selected (to that player and everyone tracking them). */
public record ShieldSyncPayload(UUID player, Optional<ResourceLocation> shield) implements CustomPacketPayload {
    public static final Type<ShieldSyncPayload> TYPE = new Type<>(LOTRRevival.id("progression/shield_sync"));

    public static final StreamCodec<ByteBuf, ShieldSyncPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            ShieldSyncPayload::player,
            ByteBufCodecs.optional(CoreNetwork.ID_CODEC),
            ShieldSyncPayload::shield,
            ShieldSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
