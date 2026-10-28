package io.github.sethwillemsvg.lotrrevival.world.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * One entry of the {@code lotrrevival:road} datapack registry (see {@code specs/world/waypoints-travel.md}).
 *
 * @param name display name
 * @param width half-width in blocks: every column within this distance of the centre line is road
 * @param showOnMap whether the map GUI draws it
 * @param points control points in heightmap pixel coordinates
 */
public record Road(Component name, int width, boolean showOnMap, List<Point> points) {
    public static final int DEFAULT_WIDTH = 4;

    public static final Codec<Road> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ComponentSerialization.CODEC.fieldOf("name").forGetter(Road::name),
                    Codec.intRange(1, 64).optionalFieldOf("width", DEFAULT_WIDTH).forGetter(Road::width),
                    Codec.BOOL.optionalFieldOf("show_on_map", true).forGetter(Road::showOnMap),
                    Point.CODEC.listOf().fieldOf("points").forGetter(Road::points))
            .apply(i, Road::new));

    /** A control point, {@code [u, v]} in heightmap pixels. */
    public record Point(double u, double v) {
        public static final Codec<Point> CODEC = Codec.DOUBLE.listOf().comapFlatMap(
                list -> list.size() == 2
                        ? DataResult.success(new Point(list.get(0), list.get(1)))
                        : DataResult.error(() -> "a road point needs exactly 2 numbers, got " + list.size()),
                point -> List.of(point.u(), point.v()));
    }
}
