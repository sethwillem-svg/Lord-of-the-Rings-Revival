package io.github.sethwillemsvg.lotrrevival.world.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.CustomWaypoints;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelData;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Clientbound full sync of the player's travel data ({@code world/travel_sync}). One logical sync is one or more parts
 * sent in order: the first part carries the regions and use counters, every part carries a slice of the custom waypoint
 * list (at most {@link #PART_SIZE}, so a part stays well under 32 KiB even with 1,024 long names), and the last part
 * makes the client swap the new data in. The scalar fields are repeated in every part.
 *
 * @param first starts a new sync (the client drops anything half received)
 * @param last ends the sync
 * @param regions unlocked region ids (first part only)
 * @param uses arrivals per fixed waypoint id (first part only)
 * @param custom this part's slice of the custom waypoints
 * @param clock travel clock in ticks at the time of sending
 * @param lastRef wire ref of the last arrival, if any
 * @param limit how many custom waypoints the player may hold
 */
public record TravelSyncPayload(
        boolean first,
        boolean last,
        List<ResourceLocation> regions,
        Map<ResourceLocation, Integer> uses,
        List<TravelData.Custom> custom,
        int clock,
        Optional<String> lastRef,
        int limit)
        implements CustomPacketPayload {
    public static final Type<TravelSyncPayload> TYPE = new Type<>(LOTRRevival.id("world/travel_sync"));

    /** Custom waypoints per part. */
    public static final int PART_SIZE = 128;

    private static final StreamCodec<ByteBuf, TravelData.Custom> CUSTOM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            TravelData.Custom::id,
            ByteBufCodecs.stringUtf8(CustomWaypoints.MAX_NAME),
            TravelData.Custom::name,
            BlockPos.STREAM_CODEC,
            TravelData.Custom::pos,
            ByteBufCodecs.VAR_INT,
            TravelData.Custom::uses,
            TravelData.Custom::new);

    private static final StreamCodec<ByteBuf, List<ResourceLocation>> REGIONS_CODEC =
            CoreNetwork.ID_CODEC.apply(ByteBufCodecs.list(CoreNetwork.MAX_COLLECTION));

    private static final StreamCodec<ByteBuf, Map<ResourceLocation, Integer>> USES_CODEC = ByteBufCodecs.map(
            HashMap::new, CoreNetwork.ID_CODEC, ByteBufCodecs.VAR_INT, CoreNetwork.MAX_COLLECTION);

    private static final StreamCodec<ByteBuf, List<TravelData.Custom>> CUSTOM_LIST_CODEC =
            CUSTOM_CODEC.apply(ByteBufCodecs.list(PART_SIZE));

    private static final StreamCodec<ByteBuf, Optional<String>> LAST_CODEC =
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(CoreNetwork.MAX_STRING));

    public static final StreamCodec<ByteBuf, TravelSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TravelSyncPayload decode(ByteBuf buf) {
            return new TravelSyncPayload(
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    REGIONS_CODEC.decode(buf),
                    USES_CODEC.decode(buf),
                    CUSTOM_LIST_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    LAST_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, TravelSyncPayload payload) {
            ByteBufCodecs.BOOL.encode(buf, payload.first);
            ByteBufCodecs.BOOL.encode(buf, payload.last);
            REGIONS_CODEC.encode(buf, payload.regions);
            USES_CODEC.encode(buf, payload.uses);
            CUSTOM_LIST_CODEC.encode(buf, payload.custom);
            ByteBufCodecs.VAR_INT.encode(buf, payload.clock);
            LAST_CODEC.encode(buf, payload.lastRef);
            ByteBufCodecs.VAR_INT.encode(buf, payload.limit);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
