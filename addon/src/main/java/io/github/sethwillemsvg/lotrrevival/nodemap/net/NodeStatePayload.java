package io.github.sethwillemsvg.lotrrevival.nodemap.net;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: one player's node map state. Barred nodes are worked out on the server, so the client needs no
 * reputation data. Also carries the cost numbers in use so the panel can show the same cooldown and hunger the server
 * will apply, and the coin price per 1,000 blocks (0 = off).
 */
public record NodeStatePayload(List<ResourceLocation> known, List<ResourceLocation> rumoured, List<Barred> barred, List<CampInfo> camps,
                               int cooldown, String lastNode, int campLimit, int departRadius, int cooldownBase, double cooldownPerBlock,
                               int cooldownMax, double exhaustionPerBlock, double exhaustionCap, double wildFactor, int coinPer1000)
        implements CustomPacketPayload {
    public static final Type<NodeStatePayload> TYPE = new Type<>(LotrRevival.id("node_state"));

    public record Barred(ResourceLocation node, ResourceLocation faction) {}

    /** A camp with its current links (other end refs). */
    public record CampInfo(int id, String name, BlockPos pos, List<String> links) {}

    public static final StreamCodec<FriendlyByteBuf, NodeStatePayload> STREAM_CODEC = StreamCodec.of(NodeStatePayload::write, NodeStatePayload::read);

    private static void write(FriendlyByteBuf b, NodeStatePayload p) {
        b.writeVarInt(p.known.size());
        p.known.forEach(b::writeResourceLocation);
        b.writeVarInt(p.rumoured.size());
        p.rumoured.forEach(b::writeResourceLocation);
        b.writeVarInt(p.barred.size());
        for (Barred x : p.barred) {
            b.writeResourceLocation(x.node);
            b.writeResourceLocation(x.faction);
        }
        b.writeVarInt(p.camps.size());
        for (CampInfo c : p.camps) {
            b.writeVarInt(c.id);
            b.writeUtf(c.name, 64);
            b.writeBlockPos(c.pos);
            b.writeVarInt(c.links.size());
            c.links.forEach(l -> b.writeUtf(l, 256));
        }
        b.writeVarInt(p.cooldown);
        b.writeUtf(p.lastNode, 256);
        b.writeVarInt(p.campLimit);
        b.writeVarInt(p.departRadius);
        b.writeVarInt(p.cooldownBase);
        b.writeDouble(p.cooldownPerBlock);
        b.writeVarInt(p.cooldownMax);
        b.writeDouble(p.exhaustionPerBlock);
        b.writeDouble(p.exhaustionCap);
        b.writeDouble(p.wildFactor);
        b.writeVarInt(p.coinPer1000);
    }

    private static NodeStatePayload read(FriendlyByteBuf b) {
        List<ResourceLocation> known = new ArrayList<>();
        for (int i = b.readVarInt(); i > 0; i--) known.add(b.readResourceLocation());
        List<ResourceLocation> rumoured = new ArrayList<>();
        for (int i = b.readVarInt(); i > 0; i--) rumoured.add(b.readResourceLocation());
        List<Barred> barred = new ArrayList<>();
        for (int i = b.readVarInt(); i > 0; i--) barred.add(new Barred(b.readResourceLocation(), b.readResourceLocation()));
        List<CampInfo> camps = new ArrayList<>();
        for (int i = b.readVarInt(); i > 0; i--) {
            int id = b.readVarInt();
            String name = b.readUtf(64);
            BlockPos pos = b.readBlockPos();
            List<String> links = new ArrayList<>();
            for (int j = b.readVarInt(); j > 0; j--) links.add(b.readUtf(256));
            camps.add(new CampInfo(id, name, pos, links));
        }
        return new NodeStatePayload(known, rumoured, barred, camps, b.readVarInt(), b.readUtf(256), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readDouble(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
