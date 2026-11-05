package io.github.sethwillemsvg.lotrrevival.nodemap.net;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the Camps tab. {@code op} is create (name), rename (id, name) or delete (id). */
public record CampEditPayload(String op, int id, String name) implements CustomPacketPayload {
    public static final Type<CampEditPayload> TYPE = new Type<>(LotrRevival.id("camp_edit"));
    public static final StreamCodec<ByteBuf, CampEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), CampEditPayload::op,
            ByteBufCodecs.VAR_INT, CampEditPayload::id,
            ByteBufCodecs.stringUtf8(64), CampEditPayload::name,
            CampEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
