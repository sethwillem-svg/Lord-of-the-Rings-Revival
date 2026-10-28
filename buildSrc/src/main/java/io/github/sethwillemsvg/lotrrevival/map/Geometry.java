package io.github.sethwillemsvg.lotrrevival.map;

import java.util.List;

/** Small 2D geometry helpers in heightmap pixel coordinates. */
final class Geometry {
    private Geometry() {}

    /** A single closed ring; the closing edge back to the first point is implicit. */
    static final class Polygon {
        final double[] us;
        final double[] vs;
        final double minU;
        final double maxU;
        final double minV;
        final double maxV;

        Polygon(List<double[]> points) {
            us = new double[points.size()];
            vs = new double[points.size()];
            double loU = Double.MAX_VALUE;
            double hiU = -Double.MAX_VALUE;
            double loV = Double.MAX_VALUE;
            double hiV = -Double.MAX_VALUE;
            for (int i = 0; i < us.length; i++) {
                us[i] = points.get(i)[0];
                vs[i] = points.get(i)[1];
                loU = Math.min(loU, us[i]);
                hiU = Math.max(hiU, us[i]);
                loV = Math.min(loV, vs[i]);
                hiV = Math.max(hiV, vs[i]);
            }
            minU = loU;
            maxU = hiU;
            minV = loV;
            maxV = hiV;
        }

        /** Even-odd point-in-polygon test. */
        boolean contains(double u, double v) {
            if (u < minU || u > maxU || v < minV || v > maxV) {
                return false;
            }
            boolean inside = false;
            for (int i = 0, j = us.length - 1; i < us.length; j = i++) {
                if ((vs[i] > v) != (vs[j] > v)
                        && u < (us[j] - us[i]) * (v - vs[i]) / (vs[j] - vs[i]) + us[i]) {
                    inside = !inside;
                }
            }
            return inside;
        }
    }

    /** An open polyline. */
    static final class Polyline {
        final double[] us;
        final double[] vs;

        Polyline(List<double[]> points) {
            us = new double[points.size()];
            vs = new double[points.size()];
            for (int i = 0; i < us.length; i++) {
                us[i] = points.get(i)[0];
                vs[i] = points.get(i)[1];
            }
        }

        /**
         * Distance from (u, v) to the polyline. {@code outParam[0]} receives the position along the line as
         * {@code segmentIndex + fraction}, so per-vertex values can be interpolated.
         */
        double distance(double u, double v, double[] outParam) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i + 1 < us.length; i++) {
                double dx = us[i + 1] - us[i];
                double dy = vs[i + 1] - vs[i];
                double len2 = dx * dx + dy * dy;
                double t = len2 == 0 ? 0 : ((u - us[i]) * dx + (v - vs[i]) * dy) / len2;
                t = Math.max(0, Math.min(1, t));
                double px = us[i] + t * dx - u;
                double py = vs[i] + t * dy - v;
                double d2 = px * px + py * py;
                if (d2 < best) {
                    best = d2;
                    if (outParam != null) {
                        outParam[0] = i + t;
                    }
                }
            }
            return Math.sqrt(best);
        }
    }

    static double smoothstep(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }
}
