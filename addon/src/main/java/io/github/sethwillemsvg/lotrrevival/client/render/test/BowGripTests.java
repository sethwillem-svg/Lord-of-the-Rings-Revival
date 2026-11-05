package io.github.sethwillemsvg.lotrrevival.client.render.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.client.anim.BowDraw;
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
 * Bows in the hand (client.render.WeaponGrip.bow) and the drawn model shown (client.anim.BowDraw), on a generated (flat)
 * bow as Minecraft bakes one: two faces over its 16 px texture square and one-texel edge quads along the wood's two edges
 * and the string, the bow on the texture's diagonal (tips at texels (15.5, 0.5) and (0.5, 15.5), the wood bowed toward
 * texel (c, c)), lying in the arm as a display transform leaves it. tools/anim-viewer's test/bow.test.js checks the
 * viewer's copy of the same placement on the same bow, and the clips that hold it.
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BowGripTests {
    private BowGripTests() {}

    private static float[] bez(float c, float f) {
        float a = (1 - f) * (1 - f), b = 2 * f * (1 - f), d = f * f;
        return new float[] {a * 15.5f + b * c + d * 0.5f, a * 0.5f + b * c + d * 15.5f};
    }

    /** Texel (u, v) at depth y in the arm's part space (the texture square in the plane y = 8, u along -x, v along +z). */
    private static Vector3f toPart(float u, float v, float y) {
        return new Vector3f(8 - u - 1, y, v - 8 - 2);
    }

    private static float[] quad(Vector3f... v) {
        float[] q = new float[v.length * 3];
        for (int i = 0; i < v.length; i++) { q[i * 3] = v[i].x; q[i * 3 + 1] = v[i].y; q[i * 3 + 2] = v[i].z; }
        return q;
    }

    /** A polyline (texels) offset d texels to its left, as edge quads one px deep. */
    private static void edge(List<float[]> out, List<float[]> line, float d) {
        List<float[]> pts = new ArrayList<>();
        for (int i = 0; i < line.size(); i++) {
            float[] a = line.get(Math.max(0, i - 1)), b = line.get(Math.min(line.size() - 1, i + 1));
            float tx = b[0] - a[0], ty = b[1] - a[1], l = (float) Math.hypot(tx, ty);
            pts.add(new float[] {line.get(i)[0] - ty / l * d, line.get(i)[1] + tx / l * d});
        }
        for (int i = 0; i + 1 < pts.size(); i++) {
            float[] p = pts.get(i), q = pts.get(i + 1);
            out.add(quad(toPart(p[0], p[1], 7.5f), toPart(q[0], q[1], 7.5f), toPart(q[0], q[1], 8.5f), toPart(p[0], p[1], 8.5f)));
        }
    }

    /** The generated bow, its wood bowed toward texel (c, c): 2 for the bow, 4 for the flatter longbow. */
    private static List<float[]> generatedBow(float c) {
        List<float[]> quads = new ArrayList<>();
        for (float y : new float[] {7.5f, 8.5f}) quads.add(quad(toPart(0, 0, y), toPart(16, 0, y), toPart(16, 16, y), toPart(0, 16, y)));
        List<float[]> wood = new ArrayList<>(), string = new ArrayList<>();
        for (int i = 0; i <= 24; i++) wood.add(bez(c, i / 24f));
        for (int i = 0; i <= 21; i++) string.add(new float[] {15.5f - 15 * i / 21f, 0.5f + 15 * i / 21f});
        edge(quads, wood, 0.75f);
        edge(quads, wood, -0.75f);
        edge(quads, string, 0.5f);
        edge(quads, string, -0.5f);
        return quads;
    }

    private static List<float[]> moved(List<float[]> quads, Matrix4f m) {
        List<float[]> out = new ArrayList<>();
        for (float[] q : quads) {
            float[] r = new float[q.length];
            for (int i = 0; i < q.length; i += 3) {
                Vector3f p = m.transformPosition(new Vector3f(q[i], q[i + 1], q[i + 2]));
                r[i] = p.x; r[i + 1] = p.y; r[i + 2] = p.z;
            }
            out.add(r);
        }
        return out;
    }

    @GameTest(template = "empty")
    public static void bowIsHeldUprightAcrossTheArmWithItsGripOnTheHand(GameTestHelper h) {
        for (String cls : new String[] {"bow", "longbow"})
            for (boolean left : new boolean[] {false, true}) {
                List<float[]> quads = generatedBow(cls.equals("bow") ? 2 : 4);
                String what = cls + (left ? " (left)" : "");
                // as drawn: the measure finds the wood's middle (texel (c, c) pulled half way to the string's middle at
                // (8, 8): the wood at the middle of the arc) and the string behind it
                WeaponGrip.Bow drawn = WeaponGrip.measureBow(quads);
                h.assertTrue(drawn != null, what + ": not measured");
                float c = cls.equals("bow") ? 2 : 4, woodMid = 0.25f * 15.5f + 0.5f * c + 0.25f * 0.5f;
                h.assertTrue(drawn.grip().distance(toPart(woodMid, woodMid, 8)) < 0.3f, what + ": grip " + drawn.grip());
                h.assertTrue(Math.abs(drawn.brace() - (8 - woodMid) * (float) Math.sqrt(2)) < 0.3f, what + ": brace " + drawn.brace());
                Matrix4f m = WeaponGrip.place(cls, left, quads);
                h.assertTrue(m != null, what + ": no placement");
                WeaponGrip.Bow held = WeaponGrip.measureBow(moved(quads, m));
                h.assertTrue(held.up().dot(WeaponGrip.BOW_UP) > 0.9999f, what + ": limbs along " + held.up() + ", not the front of the forearm");
                h.assertTrue(held.out().dot(WeaponGrip.BOW_OUT) > 0.9999f, what + ": out along " + held.out() + ", not down the arm");
                h.assertTrue(held.grip().distance(WeaponGrip.bowGrip(left)) < 1e-3f, what + ": grip at " + held.grip() + ", not " + WeaponGrip.bowGrip(left));
                float want = WeaponGrip.length(cls) * 16f;
                h.assertTrue(Math.abs(held.length() - want) < 1e-3f * want, what + ": " + held.length() + " px tip to tip, class " + want);
                // the grip on the hand: within the hand (part space x -3..1 right, -1..3 left; y 7..12; z -2.5..2.5) or 2.5 px of it
                Vector3f g = held.grip();
                float lo = left ? -1 : -3, hi = left ? 3 : 1;
                float d = (float) Math.sqrt(sq(Math.max(0, Math.max(lo - g.x, g.x - hi))) + sq(Math.max(0, Math.max(7 - g.y, g.y - 12))) + sq(Math.max(0, Math.max(-2.5f - g.z, g.z - 2.5f))));
                h.assertTrue(d <= 2.5f, what + ": the grip is " + d + " px from the hand");
            }
        h.succeed();
    }

    private static float sq(float x) {
        return x * x;
    }

    @GameTest(template = "empty")
    public static void crossbowsAreSeatedAsWeapons(GameTestHelper h) {
        h.assertTrue(WeaponGrip.isBow("bow") && WeaponGrip.isBow("longbow"), "bows are placed as bows");
        h.assertTrue(!WeaponGrip.isBow("crossbow") && !WeaponGrip.isBow(null), "a crossbow is seated as a weapon");
        h.assertTrue(WeaponGrip.place("bow", false, List.of()) == null, "a bow without quads was placed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void drawnModelFollowsTheClipsDraw(GameTestHelper h) {
        float[] p = {0f, 0.01f, 0.64f, 0.65f, 0.89f, 0.9f, 1f};
        int[] want = {BowDraw.SLACK, BowDraw.PULLING_0, BowDraw.PULLING_0, BowDraw.PULLING_1, BowDraw.PULLING_1, BowDraw.PULLING_2, BowDraw.PULLING_2};
        for (int i = 0; i < p.length; i++) h.assertTrue(BowDraw.stageOf(p[i]) == want[i], "draw " + p[i] + ": stage " + BowDraw.stageOf(p[i]) + ", want " + want[i]);
        BowDraw.Timing d = new BowDraw.Timing(0.2f, 0.6f, 0.05f);
        h.assertTrue(BowDraw.pullAt(d, "bow_aim", 0.1f) == 0f, "the string is drawn before the draw starts");
        h.assertTrue(Math.abs(BowDraw.pullAt(d, "bow_aim", 0.4f) - 0.5f) < 1e-6f, "half way through the draw is not half drawn");
        h.assertTrue(BowDraw.pullAt(d, "bow_aim", 0.7f) == 1f, "the full draw does not hold");
        h.assertTrue(BowDraw.pullAt(d, "bow_release", 0.04f) == 1f && BowDraw.pullAt(d, "bow_release", 0.05f) == 0f, "the release is not at its time");
        h.assertTrue(BowDraw.pullAt(null, "bow_aim", 0.7f) == 0f && BowDraw.pullAt(d, "idle", 0.3f) == 0f, "slack outside the bow clips");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void crossbowShowsItsRealReload(GameTestHelper h) {
        h.assertTrue(BowDraw.crossbowStage(false, false, 0.5f) == BowDraw.SLACK, "a crossbow not being reloaded is drawn");
        h.assertTrue(BowDraw.crossbowStage(true, true, 0.5f) == BowDraw.SLACK, "a loaded crossbow shows a reload stage");
        float[] pull = {0f, 0.57f, 0.58f, 0.99f, 1f, 1.2f};
        int[] want = {BowDraw.PULLING_0, BowDraw.PULLING_0, BowDraw.PULLING_1, BowDraw.PULLING_1, BowDraw.PULLING_2, BowDraw.PULLING_2};
        for (int i = 0; i < pull.length; i++)
            h.assertTrue(BowDraw.crossbowStage(true, false, pull[i]) == want[i], "reload " + pull[i] + ": stage " + BowDraw.crossbowStage(true, false, pull[i]));
        h.succeed();
    }

    /**
     * A stage's predicate values pick that stage's model from a bow's overrides as Minecraft's ItemOverrides does (the last
     * listed override whose every predicate is met): Middle-earth's bows list pulling 1 -> pulling_0, pulling 1 and pull
     * 0.65 -> pulling_1, pulling 1 and pull 0.9 -> pulling_2 (vanilla's bow the same).
     */
    @GameTest(template = "empty")
    public static void stagePredicatesPickTheStagesModel(GameTestHelper h) {
        float[][] overrides = {{1f, Float.NEGATIVE_INFINITY}, {1f, 0.65f}, {1f, 0.9f}};   // { pulling, pull } needed; -inf: not asked
        for (int stage = BowDraw.SLACK; stage <= BowDraw.PULLING_2; stage++) {
            float[] v = BowDraw.predicates(stage);
            int picked = 0;   // 0: the base (slack) model, n: pulling_(n-1)
            for (int i = overrides.length - 1; i >= 0 && picked == 0; i--)
                if (v[0] >= overrides[i][0] && v[1] >= overrides[i][1]) picked = i + 1;
            h.assertTrue(picked == stage, "stage " + stage + " picks model " + picked);
        }
        h.succeed();
    }
}
