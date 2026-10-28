package io.github.sethwillemsvg.lotrrevival.world.road;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Centripetal Catmull-Rom spline through road control points, sampled every block of arc length, with a smooth
 * sideways jitter (spec: "Roads" rules table). Pure Java, no Minecraft types, deterministic: the jitter is seeded
 * by the road id only, never the world seed, so every world has the same roads.
 */
public final class RoadSpline {
    /** Jitter wavelength along the road, in blocks. */
    public static final double JITTER_WAVELENGTH = 24.0;

    /** Largest sideways jitter, in blocks. */
    public static final double JITTER_AMPLITUDE = 1.0;

    private RoadSpline() {}

    /**
     * Points one block of arc length apart (the last one may be closer).
     *
     * @param x block x of each sample, jitter applied
     * @param z block z of each sample, jitter applied
     * @param arc arc length along the unjittered curve at each sample, in blocks (0 at the start)
     */
    public record Samples(double[] x, double[] z, double[] arc) {
        public int size() {
            return x.length;
        }

        /** Length of the unjittered curve. */
        public double length() {
            return arc.length == 0 ? 0 : arc[arc.length - 1];
        }
    }

    /**
     * Samples the road.
     *
     * @param roadId the road's registry id, the only seed of the jitter
     * @param cx control point block x, at least 2 points
     * @param cz control point block z
     */
    public static Samples sample(String roadId, double[] cx, double[] cz) {
        if (cx.length != cz.length || cx.length < 2) {
            throw new IllegalArgumentException("a road needs at least 2 control points");
        }
        // Drop repeated points: they would give zero-length spline segments.
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < cx.length; i++) {
            if (pts.isEmpty() || pts.get(pts.size() - 1)[0] != cx[i] || pts.get(pts.size() - 1)[1] != cz[i]) {
                pts.add(new double[] {cx[i], cz[i]});
            }
        }
        if (pts.size() < 2) {
            throw new IllegalArgumentException("a road needs at least 2 distinct control points");
        }

        // Dense evaluation of every segment.
        List<double[]> dense = new ArrayList<>();
        dense.add(pts.get(0));
        for (int i = 0; i < pts.size() - 1; i++) {
            // End points are their own outer neighbours.
            double[] p0 = pts.get(Math.max(i - 1, 0));
            double[] p1 = pts.get(i);
            double[] p2 = pts.get(i + 1);
            double[] p3 = pts.get(Math.min(i + 2, pts.size() - 1));
            int steps = Math.max(16, (int) Math.ceil(dist(p1, p2) * 2));
            for (int s = 1; s <= steps; s++) {
                dense.add(centripetal(p0, p1, p2, p3, s / (double) steps));
            }
        }

        // Resample at one block of arc length.
        double total = 0;
        double[] cumulative = new double[dense.size()];
        for (int i = 1; i < dense.size(); i++) {
            total += dist(dense.get(i - 1), dense.get(i));
            cumulative[i] = total;
        }
        int count = (int) Math.floor(total) + 1;
        boolean tail = total - Math.floor(total) > 1e-6;
        int n = count + (tail ? 1 : 0);
        double[] x = new double[n];
        double[] z = new double[n];
        double[] arc = new double[n];
        int seg = 1;
        for (int k = 0; k < n; k++) {
            double target = k < count ? k : total;
            while (seg < dense.size() - 1 && cumulative[seg] < target) {
                seg++;
            }
            double[] a = dense.get(seg - 1);
            double[] b = dense.get(seg);
            double span = cumulative[seg] - cumulative[seg - 1];
            double f = span <= 0 ? 0 : Math.min(1, Math.max(0, (target - cumulative[seg - 1]) / span));
            x[k] = a[0] + (b[0] - a[0]) * f;
            z[k] = a[1] + (b[1] - a[1]) * f;
            arc[k] = target;
        }

        // Jitter sideways along the normal of the unjittered curve.
        long seed = seed(roadId);
        double[] jx = new double[n];
        double[] jz = new double[n];
        for (int k = 0; k < n; k++) {
            int lo = Math.max(k - 1, 0);
            int hi = Math.min(k + 1, n - 1);
            double tx = x[hi] - x[lo];
            double tz = z[hi] - z[lo];
            double len = Math.sqrt(tx * tx + tz * tz);
            if (len == 0) {
                jx[k] = x[k];
                jz[k] = z[k];
                continue;
            }
            double offset = JITTER_AMPLITUDE * valueNoise(seed, arc[k] / JITTER_WAVELENGTH);
            jx[k] = x[k] + (-tz / len) * offset;
            jz[k] = z[k] + (tx / len) * offset;
        }
        return new Samples(jx, jz, arc);
    }

    /** Barry-Goldman evaluation of the centripetal (alpha 0.5) Catmull-Rom segment p1 to p2 at {@code u} in 0..1. */
    static double[] centripetal(double[] p0, double[] p1, double[] p2, double[] p3, double u) {
        double t0 = 0;
        double t1 = t0 + Math.sqrt(dist(p0, p1));
        double t2 = t1 + Math.sqrt(dist(p1, p2));
        double t3 = t2 + Math.sqrt(dist(p2, p3));
        double t = t1 + (t2 - t1) * u;
        double[] a1 = mix(p0, p1, t0, t1, t);
        double[] a2 = mix(p1, p2, t1, t2, t);
        double[] a3 = mix(p2, p3, t2, t3, t);
        double[] b1 = mix(a1, a2, t0, t2, t);
        double[] b2 = mix(a2, a3, t1, t3, t);
        return mix(b1, b2, t1, t2, t);
    }

    /** Linear blend of a (at ta) and b (at tb) evaluated at t; a degenerate interval (repeated end point) gives b. */
    private static double[] mix(double[] a, double[] b, double ta, double tb, double t) {
        if (tb - ta <= 0) {
            return b;
        }
        double f = (t - ta) / (tb - ta);
        return new double[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f};
    }

    private static double dist(double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dz = b[1] - a[1];
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Stable 64-bit seed from the road id (FNV-1a, so it never depends on {@code String.hashCode}). */
    static long seed(String roadId) {
        long h = 0xcbf29ce484222325L;
        for (byte b : roadId.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= 0x100000001b3L;
        }
        return h;
    }

    /** Smooth 1D value noise in -1..1 with lattice spacing 1 in {@code p}. */
    static double valueNoise(long seed, double p) {
        long k = (long) Math.floor(p);
        double f = p - k;
        double t = f * f * (3 - 2 * f);
        double a = lattice(seed, k);
        double b = lattice(seed, k + 1);
        return a + (b - a) * t;
    }

    private static double lattice(long seed, long k) {
        long z = seed + k * 0x9e3779b97f4a7c15L;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        z ^= z >>> 31;
        return ((z >>> 11) / (double) (1L << 53)) * 2.0 - 1.0;
    }
}
