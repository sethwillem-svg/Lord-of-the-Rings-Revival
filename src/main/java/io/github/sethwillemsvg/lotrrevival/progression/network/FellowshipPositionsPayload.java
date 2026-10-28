package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** {@code lotrrevival:progression/fellowship_positions}, clientbound: co-members' positions while the map is open. */
public record FellowshipPositionsPayload(List<Entry> positions) implements CustomPacketPayload {
    public static final Type<FellowshipPositionsPayload> TYPE =
            new Type<>(LOTRRevival.id("progression/fellowship_positions"));

    public record Entry(UUID player, String name, ResourceLocation dimension, int x, int z) {
        static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC,
                Entry::player,
                ByteBufCodecs.stringUtf8(64),
                Entry::name,
                CoreNetwork.ID_CODEC,
                Entry::dimension,
                ByteBufCodecs.VAR_INT,
                Entry::x,
                ByteBufCodecs.VAR_INT,
                Entry::z,
                Entry::new);
    }

    public static final StreamCodec<ByteBuf, FellowshipPositionsPayload> STREAM_CODEC =
            Entry.CODEC.apply(ByteBufCodecs.list(CoreNetwork.MAX_COLLECTION))
                    .map(FellowshipPositionsPayload::new, FellowshipPositionsPayload::positions);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
