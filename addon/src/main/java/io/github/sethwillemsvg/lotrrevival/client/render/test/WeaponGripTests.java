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
        h.assertTrue(WeaponGrip.place("shield", false, generatedItem(false)) == null, "a shield was placed");
        h.assertTrue(WeaponGrip.place(null, false, generatedItem(false)) == null, "an item without a class was placed");
        h.assertTrue(WeaponGrip.place("sword", false, List.of()) == null, "an item without quads was placed");
        h.succeed();
    }
}
