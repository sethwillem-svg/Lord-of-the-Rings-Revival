package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * A waypoint region ({@code lotrrevival:waypoint_region} registry): a named group of biomes. Standing in any of its
 * biomes unlocks every waypoint of the region.
 *
 * @param name display name
 * @param biomes biome ids ({@code namespace:path}) or biome tags ({@code #namespace:path})
 */
public record WaypointRegion(Component name, List<String> biomes) {
    public static final Codec<WaypointRegion> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ComponentSerialization.CODEC.fieldOf("name").forGetter(WaypointRegion::name),
                    Codec.STRING.listOf().fieldOf("biomes").forGetter(WaypointRegion::biomes))
            .apply(i, WaypointRegion::new));
}
