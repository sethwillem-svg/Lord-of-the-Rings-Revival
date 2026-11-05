package io.github.sethwillemsvg.lotrrevival.nodemap.graph;

import io.github.sethwillemsvg.lotrrevival.nodemap.data.NodeKind;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A node as the graph sees it: block position resolved, faction checked. {@code discoverRadius} below 0 means "use the
 * config default". Camps are GraphNodes too (kind CAMP, ref camp:N), added per query for their owner.
 */
public record GraphNode(NodeRef ref, Component name, Component description, NodeKind kind, double x, double z,
                        @Nullable ResourceLocation faction, int discoverRadius, boolean hidden, boolean startKnown) {
    public double distSq(double px, double pz) {
        double dx = x - px, dz = z - pz;
        return dx * dx + dz * dz;
    }

    public double dist(double px, double pz) {
        return Math.sqrt(distSq(px, pz));
    }

    public static GraphNode simple(ResourceLocation id, double x, double z, @Nullable ResourceLocation faction, boolean hidden) {
        return new GraphNode(NodeRef.fixed(id), Component.literal(id.getPath()), Component.empty(), NodeKind.TOWN, x, z, faction, -1,
                hidden, false);
    }

    public static GraphNode camp(int id, String name, double x, double z) {
        return new GraphNode(NodeRef.camp(id), Component.literal(name), Component.empty(), NodeKind.CAMP, x, z, null, -1, false,
                false);
    }
}
