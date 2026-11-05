package io.github.sethwillemsvg.lotrrevival.nodemap.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * One fixed node, from {@code data/<ns>/lotr_revival/node/<id>.json}. Position is {@code map} (Middle-earth map pixels) or
 * {@code block} (block x,z); exactly one. The codec accepts both missing or both present so a bad file never stops the
 * world from loading; NodeGraph skips such a node with a WARN instead.
 */
public record NodeDef(Component name, Component description, NodeKind kind, Optional<XZ> map, Optional<XZ> block,
                      Optional<ResourceLocation> faction, Optional<Integer> discoverRadius, boolean hidden, boolean startKnown) {
    public static final Codec<NodeDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("name").forGetter(NodeDef::name),
            ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(NodeDef::description),
            NodeKind.CODEC.fieldOf("kind").forGetter(NodeDef::kind),
            XZ.CODEC.optionalFieldOf("map").forGetter(NodeDef::map),
            XZ.CODEC.optionalFieldOf("block").forGetter(NodeDef::block),
            ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(NodeDef::faction),
            Codec.INT.optionalFieldOf("discover_radius").forGetter(NodeDef::discoverRadius),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(NodeDef::hidden),
            Codec.BOOL.optionalFieldOf("start_known", false).forGetter(NodeDef::startKnown)
    ).apply(i, NodeDef::new));
}
