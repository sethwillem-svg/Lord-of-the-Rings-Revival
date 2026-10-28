package io.github.sethwillemsvg.lotrrevival.core.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.UUIDUtil;

/** A player's pledged faction, sent to the player and to everyone tracking them (nameplates, fellowships). */
public record PledgeSyncPayload(UUID player, Optional<ResourceLocation> faction) implements CustomPacketPayload {
    public static final Type<PledgeSyncPayload> TYPE = new Type<>(LOTRRevival.id("core/pledge_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PledgeSyncPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            PledgeSyncPayload::player,
            ByteBufCodecs.optional(CoreNetwork.ID_CODEC),
            PledgeSyncPayload::faction,
            PledgeSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
