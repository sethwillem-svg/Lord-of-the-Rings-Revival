package io.github.sethwillemsvg.lotrrevival.core.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.UUIDUtil;

/** Full alignment snapshot for the receiving player. Sent on login, respawn, dimension change and reset. */
public record AlignmentSyncPayload(
        UUID owner,
        Map<ResourceLocation, Float> values,
        Optional<ResourceLocation> pledge,
        int cooldownTicks,
        Optional<ResourceLocation> cooldownFaction)
        implements CustomPacketPayload {
    public static final Type<AlignmentSyncPayload> TYPE =
            new Type<>(LOTRRevival.id("core/alignment_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AlignmentSyncPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC,
            AlignmentSyncPayload::owner,
            ByteBufCodecs.map(HashMap::new, CoreNetwork.ID_CODEC, ByteBufCodecs.FLOAT, CoreNetwork.MAX_COLLECTION),
            AlignmentSyncPayload::values,
            ByteBufCodecs.optional(CoreNetwork.ID_CODEC),
            AlignmentSyncPayload::pledge,
            ByteBufCodecs.VAR_INT,
            AlignmentSyncPayload::cooldownTicks,
            ByteBufCodecs.optional(CoreNetwork.ID_CODEC),
            AlignmentSyncPayload::cooldownFaction,
            AlignmentSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
