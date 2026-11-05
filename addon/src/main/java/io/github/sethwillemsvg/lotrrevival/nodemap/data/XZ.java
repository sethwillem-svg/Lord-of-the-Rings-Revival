package io.github.sethwillemsvg.lotrrevival.nodemap.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;

/** A horizontal point, written in data as {@code [x, z]} (map pixels or blocks, depending on the field). */
public record XZ(double x, double z) {
    public static final Codec<XZ> CODEC = Codec.DOUBLE.listOf().comapFlatMap(
            l -> l.size() == 2 ? DataResult.success(new XZ(l.get(0), l.get(1))) : DataResult.error(() -> "expected [x, z], got " + l),
            p -> List.of(p.x, p.z));

    public double dist(XZ o) {
        return Math.hypot(x - o.x, z - o.z);
    }

    @Override
    public String toString() {
        return Math.round(x) + "," + Math.round(z);
    }
}
