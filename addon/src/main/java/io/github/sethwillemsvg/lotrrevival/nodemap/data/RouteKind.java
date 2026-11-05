package io.github.sethwillemsvg.lotrrevival.nodemap.data;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Kind of route, with its default length factor (config {@code travel.factor.<kind>}) and its line colour on the map. */
public enum RouteKind implements StringRepresentable {
    ROAD(1.0, 0xFF8B5A2B), RIVER(0.8, 0xFF3A6FD0), PATH(1.25, 0xFF8B5A2B), WILD(1.6, 0xFF8A8A8A);

    public static final Codec<RouteKind> CODEC = StringRepresentable.fromEnum(RouteKind::values);

    public final double defaultFactor;
    public final int color;

    RouteKind(double defaultFactor, int color) {
        this.defaultFactor = defaultFactor;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
