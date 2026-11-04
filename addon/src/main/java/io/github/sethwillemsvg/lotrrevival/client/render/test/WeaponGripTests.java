package io.github.sethwillemsvg.lotrrevival.client.render.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.client.render.WeaponGrip;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * client.render.WeaponGrip on a generated (flat) item model as Minecraft bakes it: two faces over the whole 16 px texture
 * square plus one-texel edge quads along a diagonal blade that fills 60% of it, the pommel 1.5 px behind the fist. The
 * drawn blade, not the square, is scaled to the class length, and its handle lands on the fist (tools/anim-viewer's
 * test/held.test.js checks the viewer's copy of the same measure).
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WeaponGripTests {
    private WeaponGripTests() {}

    private static final float SIDE = 16, WIDTH = 2, BLADE = 0.6f * SIDE * (float) Math.sqrt(2);
    private static final float R = (float) Math.sqrt(0.5);
    /** Up and forward in the arm's part space (y down, -z forward), and across the blade in the item's plane. */
    private static final Vector3f ALONG = new Vector3f(0, -R, -R), ACROSS = new Vector3f(0, -R, R);

    private static Vector3f pommel(boolean left) {
        return WeaponGrip.fist(left).sub(new Vector3f(ALONG).mul(1.5f * (float) Math.sqrt(2)));
    }

    private static Vector3f at(Vector3f p, float along, float across) {
        return new Vector3f(p).add(new Vector3f(ALONG).mul(along)).add(new Vector3f(ACROSS).mul(across));
    }

    private static float[] quad(Vector3f... v) {
        float[] q = new float[v.length * 3];
        for (int i = 0; i < v.length; i++) { q[i * 3] = v[i].x; q[i * 3 + 1] = v[i].y; q[i * 3 + 2] = v[i].z; }
        return q;
    }

    /** The two faces over the texture square first, then the edge quads (one per texel of outline). */
    private static List<float[]> generatedItem(boolean left) {
        Vector3f pm = pommel(left);
        float x0 = pm.x - 0.5f, x1 = pm.x + 0.5f;
        Vector3f c = at(pm, -3, 0), u = new Vector3f(ALONG).mul(SIDE), w = new Vector3f(ACROSS).mul(SIDE);
        Vector3f a = new Vector3f(c).sub(new Vector3f(w).mul(0.5f)), b = new Vector3f(a).add(u), d = new Vector3f(a).add(w), e = new Vector3f(b).add(w);
        List<float[]> quads = new ArrayList<>();
        for (float x : new float[] {x0, x1})
            quads.add(quad(new Vector3f(x, a.y, a.z), new Vector3f(x, b.y, b.z), new Vector3f(x, e.y, e.z), new Vector3f(x, d.y, d.z)));
        Vector3f[] outline = {at(pm, 0, -WIDTH / 2), at(pm, BLADE, -WIDTH / 2), at(pm, BLADE, WIDTH / 2), at(pm, 0, WIDTH / 2)};
        for (int k = 0; k < 4; k++) {
            Vector3f p = outline[k], q = outline[(k + 1) % 4];
            int n = (int) Math.ceil(p.distance(q));
            for (int i = 0; i < n; i++) {
                Vector3f s = new Vector3f(p).lerp(q, (float) i / n), t = new Vector3f(p).lerp(q, (float) (i + 1) / n);
                quads.add(quad(new Vector3f(x0, s.y, s.z), new Vector3f(x0, t.y, t.z), new Vector3f(x1, t.y, t.z), new Vector3f(x1, s.y, s.z)));
            }
        }
        return quads;
    }

    /** The edge quads' points (the drawn weapon) as placed. */
    private static List<Vector3f> placedBlade(List<float[]> quads, Matrix4f m) {
        List<Vector3f> out = new ArrayList<>();
        for (float[] q : quads.subList(2, quads.size()))
            for (int i = 0; i < q.length; i += 3) out.add(m.transformPosition(new Vector3f(q[i], q[i + 1], q[i + 2])));
        return out;
    }

    @GameTest(template = "empty")
    public static void generatedItemIsSizedByItsDrawnWeapon(GameTestHelper h) {
        for (boolean left : new boolean[] {false, true}) {
            for (String cls : new String[] {"dagger", "sword", "longsword", "axe", "spear"}) {
                List<float[]> quads = generatedItem(left);
                Matrix4f m = WeaponGrip.place(cls, left, quads);
                h.assertTrue(m != null, cls + ": no placement");
                List<Vector3f> blade = placedBlade(quads, m);
                float longest = 0, nearest = Float.MAX_VALUE;
                Vector3f fist = WeaponGrip.fist(left);
                for (Vector3f a : blade) {
                    nearest = Math.min(nearest, a.distance(fist));
                    for (Vector3f b : blade) longest = Math.max(longest, a.distance(b));
                }
                float want = WeaponGrip.length(cls) * 16f;
                h.assertTrue(Math.abs(longest - want) < 1e-3f * want, cls + (left ? " (left)" : "") + ": drawn " + longest + " px, class " + want + " px");
                h.assertTrue(nearest < 1e-3f, cls + (left ? " (left)" : "") + ": the handle is " + nearest + " px from the fist");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void classesWithoutLengthKeepTheirSeat(GameTestHelper h) {
        h.assertTrue(WeaponGrip.place(null, false, generatedItem(false)) == null, "an item without a class was placed");
        h.assertTrue(WeaponGrip.place("sword", false, List.of()) == null, "an item without quads was placed");
        h.assertTrue(WeaponGrip.place("shield", false, List.of()) == null, "a shield without quads was placed");
        h.succeed();
    }

    // ---- shields ----
    /** A box's six faces (px), each wound counter-clockwise seen from outside. */
    private static List<float[]> box(Vector3f a, Vector3f b) {
        Vector3f[] c = new Vector3f[8];
        for (int i = 0; i < 8; i++) c[i] = new Vector3f((i & 1) == 0 ? a.x : b.x, (i & 2) == 0 ? a.y : b.y, (i & 4) == 0 ? a.z : b.z);
        return List.of(quad(c[0], c[2], c[3], c[1]), quad(c[4], c[5], c[7], c[6]), quad(c[0], c[4], c[6], c[2]),
                quad(c[1], c[3], c[7], c[5]), quad(c[0], c[1], c[5], c[4]), quad(c[2], c[6], c[7], c[3]));
    }

    /**
     * A shield as Middle-earth's block-element shields are shaped (a 14 x 14 x 1 px plate, its face looking away from a
     * 2 x 6 x 6 px handle behind it), held as their display transform holds it: the face looking out sideways from the arm
     * (-x on the right arm, +x on the left), the plate's middle 7 px up the arm from the fist. faceSign: which way the face
     * looks along z instead (+1 or -1; 0 for the display's own sideways look), to show the strap does not depend on it.
     */
    private static List<float[]> shield(boolean left, int faceSign) {
        // built facing -z (its handle behind it, toward +z), then turned so the face looks where asked
        List<float[]> local = new ArrayList<>(box(new Vector3f(-7, -7, -0.5f), new Vector3f(7, 7, 0.5f)));
        local.addAll(box(new Vector3f(-1, -3, 0.5f), new Vector3f(1, 3, 6.5f)));
        return turned(local, left, faceSign);
    }

    /**
     * A shield built of one-px strips, as Middle-earth's Goblin Town, Gundabad heavy and Moria shields are: 20 upright strips
     * 1 px wide and 1 px deep side by side, each a whole box, with the handle behind them; held as shield(left, 0) holds its
     * plate. Each strip's two sides have the area of its front and back, so by area the faces look across the plate as much
     * as out of it. Its first face is the first strip's front.
     */
    private static List<float[]> stripShield(boolean left) {
        List<float[]> local = new ArrayList<>();
        for (int i = 0; i < 20; i++) local.addAll(box(new Vector3f(-10 + i, -10, -0.5f), new Vector3f(-9 + i, 10, 0.5f)));
        local.addAll(box(new Vector3f(-1, -3, 0.5f), new Vector3f(1, 3, 6.5f)));
        return turned(local, left, 0);
    }

    private static List<float[]> turned(List<float[]> local, boolean left, int faceSign) {
        Matrix4f turn = new Matrix4f().translate(left ? 5 : -5, 3, 1);
        if (faceSign == 0) turn.rotateY((float) Math.toRadians(left ? -90 : 90));   // -z to +x (left) or -x (right)
        else if (faceSign > 0) turn.rotateY((float) Math.PI);
        List<float[]> out = new ArrayList<>();
        for (float[] q : local) {
            float[] r = new float[q.length];
            for (int i = 0; i < q.length; i += 3) {
                Vector3f p = turn.transformPosition(new Vector3f(q[i], q[i + 1], q[i + 2]));
                r[i] = p.x; r[i + 1] = p.y; r[i + 2] = p.z;
            }
            out.add(r);
        }
        return out;
    }

    /** The plate's face (the first face of the plate box, looking -z before the turn) as placed: its normal and middle. */
    private static Vector3f[] placedFace(List<float[]> quads, Matrix4f m) {
        float[] q = quads.get(0);
        Vector3f[] v = new Vector3f[4];
        for (int i = 0; i < 4; i++) v[i] = m.transformPosition(new Vector3f(q[i * 3], q[i * 3 + 1], q[i * 3 + 2]));
        Vector3f n = new Vector3f(v[1]).sub(v[0]).cross(new Vector3f(v[2]).sub(v[0])).normalize();
        Vector3f mid = new Vector3f(v[0]).add(v[1]).add(v[2]).add(v[3]).div(4);
        return new Vector3f[] {n, mid};
    }

    @GameTest(template = "empty")
    public static void shieldIsStrappedToTheForearm(GameTestHelper h) {
        for (boolean left : new boolean[] {false, true}) {
            for (int faceSign : new int[] {0, 1, -1}) {
                List<float[]> quads = shield(left, faceSign);
                Matrix4f m = WeaponGrip.place("shield", left, quads);
                String what = (left ? "left" : "right") + " arm, face " + faceSign;
                h.assertTrue(m != null, what + ": no placement");
                Vector3f[] face = placedFace(quads, m);
                Vector3f back = new Vector3f(left ? 1 : -1, 0, 0);   // the back of the forearm, where the shield's face looks
                h.assertTrue(face[0].dot(back) > 0.999f, what + ": the face looks along " + face[0] + ", not the forearm's back " + back);
                // the plate's face 1.5 px out from the forearm's back (its middle 1 px out), level with the fist, on the arm
                Vector3f want = new Vector3f(left ? 4.5f : -4.5f, 10, 0);
                h.assertTrue(face[1].distance(want) < 1e-3f, what + ": the plate's face is at " + face[1] + ", not " + want);
                // the handle stays behind the plate: every point at or behind its face
                float front = face[1].dot(back);
                for (float[] q : quads)
                    for (int i = 0; i < q.length; i += 3)
                        h.assertTrue(m.transformPosition(new Vector3f(q[i], q[i + 1], q[i + 2])).dot(back) <= front + 1e-3f, what + ": the handle is in front of the plate");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void stripShieldIsStrappedToTheForearm(GameTestHelper h) {
        for (boolean left : new boolean[] {false, true}) {
            List<float[]> quads = stripShield(left);
            Matrix4f m = WeaponGrip.place("shield", left, quads);
            String what = left ? "left arm" : "right arm";
            h.assertTrue(m != null, what + ": no placement");
            Vector3f n = placedFace(quads, m)[0], back = new Vector3f(left ? 1 : -1, 0, 0);
            h.assertTrue(n.dot(back) > 0.999f, what + ": the strip shield's face looks along " + n + ", not the forearm's back " + back);
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;   // the plate's strips: one px through along the face
            for (float[] q : quads.subList(0, 120))
                for (int i = 0; i < q.length; i += 3) {
                    float s = m.transformPosition(new Vector3f(q[i], q[i + 1], q[i + 2])).dot(back);
                    lo = Math.min(lo, s);
                    hi = Math.max(hi, s);
                }
            h.assertTrue(Math.abs(hi - lo - 1) < 1e-3f, what + ": the plate is " + (hi - lo) + " px through");
        }
        h.succeed();
    }
}
