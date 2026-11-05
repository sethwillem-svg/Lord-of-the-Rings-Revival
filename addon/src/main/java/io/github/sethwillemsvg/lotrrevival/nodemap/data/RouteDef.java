package io.github.sethwillemsvg.lotrrevival.nodemap.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * A two-way route between two fixed nodes, from {@code data/<ns>/lotr_revival/route/<id>.json}. {@code via} bends are map
 * pixels, {@code via_block} bends are blocks; they shape the line and its length.
 */
public record RouteDef(ResourceLocation from, ResourceLocation to, RouteKind kind, List<XZ> via, List<XZ> viaBlock) {
    public static final Codec<RouteDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("from").forGetter(RouteDef::from),
            ResourceLocation.CODEC.fieldOf("to").forGetter(RouteDef::to),
            RouteKind.CODEC.fieldOf("kind").forGetter(RouteDef::kind),
            XZ.CODEC.listOf().optionalFieldOf("via", List.of()).forGetter(RouteDef::via),
            XZ.CODEC.listOf().optionalFieldOf("via_block", List.of()).forGetter(RouteDef::viaBlock)
    ).apply(i, RouteDef::new));
}
