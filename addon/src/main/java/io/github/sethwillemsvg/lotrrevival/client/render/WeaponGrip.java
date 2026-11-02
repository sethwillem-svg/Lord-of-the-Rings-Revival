package io.github.sethwillemsvg.lotrrevival.client.render;

import io.github.sethwillemsvg.lotrrevival.combat.WeaponClass;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;

/**
 * How a held item sits in the hand, the same as tools/anim-viewer/bake_vanilla.js (WEAPON_LENGTH, weaponGrip, ITEM_HOLD,
 * itemHold): every weapon of a class is scaled to its length along its long axis with the grip in the fist (spears gripped a
 * third of the way up and carried leaning forward), and the brace stances re-seat the spear along the forearm and turn the
 * shield's face forward. Everything works from the item's own shape, so it fits every Middle-earth model. Points are arm
 * space in px (the arm's part space, y down); matrices map arm space to arm space.
 */
public final class WeaponGrip {
    private WeaponGrip() {}

    private static final Map<String, Float> LENGTH = Map.of("dagger", 0.6f, "sword", 1.0f, "longsword", 1.4f, "axe", 1.1f,
            "spear", 2.5f, "bow", 1.1f, "longbow", 1.6f, "crossbow", 0.9f);
    private static final float SPEAR_GRIP = 0.35f;
    private static final Vector3f SPEAR_DIR = new Vector3f(0, -1.2f, -1).normalize();
    private static final Vector3f UP_FORWARD = new Vector3f(0, -1, -1);

    /** The weapon class of a held item (combat.WeaponClass). */
    @Nullable
    public static String classOf(ItemStack stack) {
        return WeaponClass.of(stack);
    }

    private static Vector3f fist(boolean left) {
        return new Vector3f(left ? 1 : -1, 10, -2.5f);   // the front of the fist: grips sit there, not inside the arm
    }

    /** Scale to the class length with the grip in the fist; null for items without a length (shields, banners, others). */
    @Nullable
    static Matrix4f grip(@Nullable String cls, boolean left, float[] pts) {
        Float len = cls == null ? null : LENGTH.get(cls);
        if (len == null || pts.length < 9) return null;
        Shape sh = shape(pts);
        Vector3f a = new Vector3f(sh.axes[0]);
        if (a.dot(UP_FORWARD) < 0) a.negate();   // tip end: held items point forward and up out of the fist
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        Vector3f p = new Vector3f();
        for (int i = 0; i < pts.length; i += 3) {
            float t = p.set(pts[i], pts[i + 1], pts[i + 2]).sub(sh.c).dot(a);
            lo = Math.min(lo, t);
            hi = Math.max(hi, t);
        }
        if (hi - lo < 1e-3f) return null;
        Vector3f f = fist(left);
        float tg = cls.equals("spear") ? lo + SPEAR_GRIP * (hi - lo) : Math.max(lo, Math.min(hi, new Vector3f(f).sub(sh.c).dot(a)));
        Vector3f grip = new Vector3f(a).mul(tg).add(sh.c);
        float s = len * 16f / (hi - lo);
        Matrix3f r = cls.equals("spear") ? between(a, SPEAR_DIR) : new Matrix3f();
        return place(r.scale(s), grip, f);
    }

    /** A brace stance's re-seating of a held item: the spear along the forearm through the fist, the shield face forward. */
    @Nullable
    static Matrix4f hold(@Nullable String slot, boolean left, float[] pts) {
        if (slot == null || pts.length < 9) return null;
        boolean spear = slot.equals("spear_brace") && !left, shield = slot.equals("shield_brace") && left;
        if (!spear && !shield) return null;
        Shape sh = shape(pts);
        if (spear) {
            Vector3f a = new Vector3f(sh.axes[0]), at = new Vector3f(-4, 10, 0);   // along the outside of the forearm
            Vector3f pivot = new Vector3f(a).mul(new Vector3f(at).sub(sh.c).dot(a)).add(sh.c);   // the shaft's point nearest the fist
            if (a.dot(UP_FORWARD) < 0) a.negate();
            return place(between(a, new Vector3f(0, 1, 0)), pivot, at);
        }
        Vector3f n = new Vector3f(sh.axes[2]), at = new Vector3f(1, 7.5f, 3.5f);
        if (n.dot(new Vector3f(sh.c).sub(1, sh.c.y, 0)) < 0) n.negate();   // the outer face: away from the arm
        return place(between(n, new Vector3f(0, 0, 1)), sh.c, at);
    }

    /** p' = to + m (p - from). */
    private static Matrix4f place(Matrix3f m, Vector3f from, Vector3f to) {
        Vector3f t = new Vector3f(to).sub(m.transform(new Vector3f(from)));
        return new Matrix4f().set(m).setTranslation(t);
    }

    /** Points through a matrix. */
    static float[] transform(Matrix4f m, float[] pts) {
        float[] out = new float[pts.length];
        Vector3f p = new Vector3f();
        for (int i = 0; i < pts.length; i += 3) {
            m.transformPosition(p.set(pts[i], pts[i + 1], pts[i + 2]));
            out[i] = p.x; out[i + 1] = p.y; out[i + 2] = p.z;
        }
        return out;
    }

    /** An arm-space matrix in px as the same move in blocks (the PoseStack's unit). */
    static Matrix4f toBlocks(Matrix4f px) {
        return new Matrix4f().scale(1 / 16f).mul(px).scale(16f);
    }

    /** Smallest rotation taking unit a onto unit b. */
    private static Matrix3f between(Vector3f a, Vector3f b) {
        Vector3f an = new Vector3f(a).normalize(), bn = new Vector3f(b).normalize();
        float c = Math.max(-1f, Math.min(1f, an.dot(bn)));
        Vector3f ax = new Vector3f(an).cross(bn);
        if (ax.length() < 1e-6f) {
            if (c > 0) return new Matrix3f();
            ax = Math.abs(an.x) < 0.9f ? new Vector3f(an).cross(1, 0, 0) : new Vector3f(an).cross(0, 1, 0);
        }
        return new Matrix3f().rotation((float) Math.acos(c), ax.normalize());
    }

    private record Shape(Vector3f c, Vector3f[] axes) {}

    /** Centre and principal axes (longest first): Jacobi on the covariance, as bake_vanilla.js shape(). */
    private static Shape shape(float[] pts) {
        int n = pts.length / 3;
        Vector3f c = new Vector3f();
        for (int i = 0; i < pts.length; i += 3) c.add(pts[i], pts[i + 1], pts[i + 2]);
        c.div(n);
        double[][] A = new double[3][3];
        for (int i = 0; i < pts.length; i += 3) {
            double[] d = {pts[i] - c.x, pts[i + 1] - c.y, pts[i + 2] - c.z};
            for (int r = 0; r < 3; r++) for (int k = 0; k < 3; k++) A[r][k] += d[r] * d[k];
        }
        double[][] V = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        int[][] pairs = {{0, 1}, {0, 2}, {1, 2}};
        for (int sweep = 0; sweep < 30; sweep++) {
            for (int[] pq : pairs) {
                int p = pq[0], q = pq[1];
                if (Math.abs(A[p][q]) < 1e-12) continue;
                double th = 0.5 * Math.atan2(2 * A[p][q], A[q][q] - A[p][p]), cs = Math.cos(th), sn = Math.sin(th);
                for (int k = 0; k < 3; k++) { double a = A[k][p], b = A[k][q]; A[k][p] = cs * a - sn * b; A[k][q] = sn * a + cs * b; }
                for (int k = 0; k < 3; k++) { double a = A[p][k], b = A[q][k]; A[p][k] = cs * a - sn * b; A[q][k] = sn * a + cs * b; }
                for (int k = 0; k < 3; k++) { double a = V[k][p], b = V[k][q]; V[k][p] = cs * a - sn * b; V[k][q] = sn * a + cs * b; }
            }
        }
        Integer[] order = {0, 1, 2};
        java.util.Arrays.sort(order, (x, y) -> Double.compare(A[y][y], A[x][x]));
        Vector3f[] axes = new Vector3f[3];
        for (int i = 0; i < 3; i++) axes[i] = new Vector3f((float) V[0][order[i]], (float) V[1][order[i]], (float) V[2][order[i]]).normalize();
        return new Shape(c, axes);
    }
}
