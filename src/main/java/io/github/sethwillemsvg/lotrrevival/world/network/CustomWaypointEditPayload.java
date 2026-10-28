package io.github.sethwillemsvg.lotrrevival.world.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serverbound custom waypoint edit ({@code world/custom_waypoint_edit}): create at the player's position, rename or
 * delete. {@code id} is ignored for create and {@code name} for delete. The server trims the name and refuses anything
 * outside 1 to 32 characters; the wire allows {@link #WIRE_NAME_LIMIT} so padded or slightly long input is refused with
 * a chat message instead of a protocol error.
 */
public record CustomWaypointEditPayload(Op op, int id, String name) implements CustomPacketPayload {
    public static final Type<CustomWaypointEditPayload> TYPE =
            new Type<>(LOTRRevival.id("world/custom_waypoint_edit"));

    /** Longest name string the codec accepts (the service still enforces 32 after trimming). */
    public static final int WIRE_NAME_LIMIT = 64;

    public enum Op {
        CREATE,
        RENAME,
        DELETE
    }

    private static final StreamCodec<ByteBuf, Op> OP_CODEC = ByteBufCodecs.idMapper(
            i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal);

    public static final StreamCodec<ByteBuf, CustomWaypointEditPayload> STREAM_CODEC = StreamCodec.composite(
            OP_CODEC,
            CustomWaypointEditPayload::op,
            ByteBufCodecs.VAR_INT,
            CustomWaypointEditPayload::id,
            ByteBufCodecs.stringUtf8(WIRE_NAME_LIMIT),
            CustomWaypointEditPayload::name,
            CustomWaypointEditPayload::new);

    public static CustomWaypointEditPayload create(String name) {
        return new CustomWaypointEditPayload(Op.CREATE, 0, name);
    }

    public static CustomWaypointEditPayload rename(int id, String name) {
        return new CustomWaypointEditPayload(Op.RENAME, id, name);
    }

    public static CustomWaypointEditPayload delete(int id) {
        return new CustomWaypointEditPayload(Op.DELETE, id, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
