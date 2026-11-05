package io.github.sethwillemsvg.lotrrevival.nodemap;

import net.sevenstars.middleearth.world.map.MiddleEarthMapUtils;
import org.joml.Vector2d;

/**
 * Middle-earth map pixels to blocks and the border check, over Middle-earth's own map utilities, so nodes written in map
 * pixels follow Middle-earth if it rescales its world. {@link #mapToBlock(double, double, double, double)} is the pure form.
 */
public final class MapCoords {
    private MapCoords() {}

    /** Block x,z of a map pixel u,v (32 blocks per pixel with today's 3000 px map). */
    public static double[] mapToBlock(double u, double v) {
        Vector2d w = MiddleEarthMapUtils.getInstance().getWorldCoordinateFromInitialMap(u, v);
        return new double[] {w.x, w.y};
    }

    public static double[] mapToBlock(double u, double v, double ratioX, double ratioZ) {
        return new double[] {u * ratioX, v * ratioZ};
    }

    public static boolean inBorder(double x, double z) {
        return MiddleEarthMapUtils.getInstance().isWorldCoordinateInBorder((int) Math.floor(x), (int) Math.floor(z));
    }

    public static double blocksPerPixelX() {
        return MiddleEarthMapUtils.getInstance().ratioX;
    }
}
