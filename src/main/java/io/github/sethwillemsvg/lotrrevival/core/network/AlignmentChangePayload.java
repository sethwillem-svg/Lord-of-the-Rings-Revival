package io.github.sethwillemsvg.lotrrevival.core.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** One applied alignment change for the receiving player: updates the mirror and drives the floating text. */
public record AlignmentChangePayload(
        ResourceLocation faction, float newValue, float delta, ResourceLocation sourceType, Optional<Vec3> pos)
        implements CustomPacketPayload {
    public static final Type<AlignmentChangePayload> TYPE =
            new Type<>(LOTRRevival.id("core/alignment_change"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AlignmentChangePayload> STREAM_CODEC =
            StreamCodec.composite(
                    CoreNetwork.ID_CODEC,
                    AlignmentChangePayload::faction,
                    ByteBufCodecs.FLOAT,
                    AlignmentChangePayload::newValue,
                    ByteBufCodecs.FLOAT,
                    AlignmentChangePayload::delta,
                    CoreNetwork.ID_CODEC,
                    AlignmentChangePayload::sourceType,
                    ByteBufCodecs.optional(CoreNetwork.VEC3_CODEC),
                    AlignmentChangePayload::pos,
                    AlignmentChangePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
