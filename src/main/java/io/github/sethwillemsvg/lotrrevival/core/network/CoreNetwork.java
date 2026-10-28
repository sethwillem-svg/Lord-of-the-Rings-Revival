package io.github.sethwillemsvg.lotrrevival.core.network;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Fixes the network protocol version for every pod's payloads. Bumped by Core on breaking wire changes. */
public final class CoreNetwork {
    public static final String PROTOCOL_VERSION = "1";

    /** Maximum string length on the wire. */
    public static final int MAX_STRING = 256;
    /** Maximum collection size on the wire. */
    public static final int MAX_COLLECTION = 1024;

    /** Resource location with the string length capped. */
    public static final StreamCodec<io.netty.buffer.ByteBuf, ResourceLocation> ID_CODEC =
            ByteBufCodecs.stringUtf8(MAX_STRING).map(ResourceLocation::parse, ResourceLocation::toString);

    public static final StreamCodec<io.netty.buffer.ByteBuf, Vec3> VEC3_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            Vec3::x,
            ByteBufCodecs.DOUBLE,
            Vec3::y,
            ByteBufCodecs.DOUBLE,
            Vec3::z,
            Vec3::new);

    private CoreNetwork() {}

    public static PayloadRegistrar registrar(RegisterPayloadHandlersEvent event) {
        return event.registrar(PROTOCOL_VERSION);
    }
}
