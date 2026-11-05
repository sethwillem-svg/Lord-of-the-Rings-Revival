package io.github.sethwillemsvg.lotrrevival.client.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.github.sethwillemsvg.lotrrevival.combat.WeaponClass;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How a held item sits in the hand. Minecraft's held-item layer draws it in the arm (Middle-earth's models put the handle
 * near the fist); then the drawn weapon is scaled to its weapon class's length about its grip, the grip is moved onto the
 * fist, and the class's extras turn and move it. Classes and extras are in assets/lotr_revival/weapon_grips.json
 * ({"<class>": {"length": blocks, "rotate": [x, y, z] degrees, "offset": [x, y, z] px}}), the same file the viewer uses
 * (tools/anim-viewer/gecko/held.js); "length" is the weapon as drawn, pommel to tip. Points and matrices are in the arm's
 * part space, px (y down); the clip's own nudge comes from the weapon bones (client.anim.NpcPose.weapon).
 *
 * <p>The drawn weapon: Middle-earth's weapons are flat generated item models, two faces over the whole texture square
 * (front and back) plus thin edge quads, one texel deep, along the outline of the opaque pixels. Measuring every quad
 * measures the texture square, and weapons fill 55-100% of their pictures. So faces over a quarter of the largest face
 * and over about 4 texels² (4 px²: a texel is about 1 px once held) are dropped; if that leaves nothing (a real 3D model),
 * every face counts. Its length is the farthest pair of the kept points; its grip is the kept point nearest the fist.
 *
 * <p>Shields have no length: they are strapped to the forearm instead (strap), measured from their own faces the same way,
 * so every Middle-earth shield, block-element model or builtin entity model, sits face out from the back of the forearm.
 *
 * <p>Bows and longbows are held across the forearm instead (bow): measured from their own faces (limbs, grip, which side
 * the string is on), their display transform ignored, upright when the arm is held out toward the target, the grip on the
 * hand and the string toward the archer, at the class length tip to tip.
 */
public final class WeaponGrip {
    private WeaponGrip() {}

    private static final Logger LOG = LogUtils.getLogger();

    private record Grip(float length, Vector3f rotate, Vector3f offset) {}

    private static Map<String, Grip> grips;

    /** The weapon class of a held item (combat.WeaponClass). */
    @Nullable
    public static String classOf(ItemStack stack) {
        return WeaponClass.of(stack);
    }

    /** A face over this share of the largest face's area, and over FACE_MIN (px²), is a generated model's big face. */
    private static final float FACE_SHARE = 0.25f, FACE_MIN = 4f;

    /** The front of the fist in the arm's part space (px): the weapon bones' pivot, where the grip is seated. */
    public static Vector3f fist(boolean left) {
        return new Vector3f(left ? 1 : -1, 10, -2);
    }

    /** A class's length (blocks, pommel to tip as drawn), 0 without one. */
    public static float length(@Nullable String cls) {
        Grip g = cls == null ? null : grips().get(cls);
        return g == null ? 0 : g.length();
    }

    /**
     * Where a held item goes: its drawn weapon scaled to the class length about its grip, the grip moved onto the fist, then
     * the class's turn and offset; a shield strapped to the forearm (strap); a bow held across it (bow). quads: the item's faces as Minecraft draws it
     * in the arm (px), x, y, z per vertex. Null for other classes without a length, and for items with nothing drawn.
     */
    @Nullable
    public static Matrix4f place(@Nullable String cls, boolean left, List<float[]> quads) {
        if ("shield".equals(cls)) return strap(left, quads);
        if (isBow(cls)) return bow(cls, left, quads);
        Grip g = cls == null ? null : grips().get(cls);
        if (g == null) return null;
        List<Vector3f> drawn = drawn(quads);
        if (drawn.isEmpty()) return null;
        float length = diameter(drawn);
        if (length < 1e-3f) return null;
        Vector3f f = fist(left), grip = drawn.get(0);
        for (Vector3f p : drawn) if (p.distanceSquared(f) < grip.distanceSquared(f)) grip = p;
        float s = g.length() * 16f / length;
        float sign = left ? -1 : 1;   // the left hand mirrors the class's turn and sideways offset
        return new Matrix4f().translate(f).translate(sign * g.offset().x, g.offset().y, g.offset().z)
                .rotateZYX((float) Math.toRadians(sign * g.rotate().z), (float) Math.toRadians(sign * g.rotate().y), (float) Math.toRadians(g.rotate().x))
                .scale(s).translate(-grip.x, -grip.y, -grip.z);
    }

    // ---- bows ----
    /** Bows and longbows: placed by bow (crossbows are seated as weapons). */
    public static boolean isBow(@Nullable String cls) {
        return "bow".equals(cls) || "longbow".equals(cls);
    }

    /** The grip's place in the bow hand (part space, px): just above the fist's top (z -2.5: the front of the forearm, up
     *  when the arm is held out), half way along the hand (y 8.5), on the forearm's inner side (x +1.25 on the right arm,
     *  whose inner face is at x 1; -1.25 on the left, whose inner face is at -1). */
    public static Vector3f bowGrip(boolean left) {
        return new Vector3f(left ? -1.25f : 1.25f, 8.5f, -2.5f);
    }

    /** The bow's frame in the hand (part space): its limbs along the front of the forearm, out (the arrow's way) down the
     *  arm toward the fist, so the string lies toward the shoulder. */
    public static final Vector3f BOW_UP = new Vector3f(0, 0, -1), BOW_OUT = new Vector3f(0, 1, 0);
    /** The share of the bow's length either side of its middle where the grip is looked for. */
    private static final float BOW_MIDDLE = 0.05f;

    /** A bow measured from its faces (px, the quads' space): limb axis (toward the tip nearer the displayed up, -z), out
     *  (from the string to the wood: the arrow's way), the grip (the wood's middle), brace (the string's middle behind the
     *  grip) and length (tip to tip). */
    public record Bow(Vector3f up, Vector3f out, Vector3f grip, float brace, float length) {}

    /**
     * Measures a bow (the same measure as tools/anim-viewer/gecko/bow.js measure): its drawn points (a generated model's
     * big faces dropped, see the class comment) flattened onto the item's middle plane (the largest face's normal); the
     * tips, the farthest pair; across the limb axis in the plane, the wood's middle stands off the straight string to one
     * side: of the points within BOW_MIDDLE of the middle of the length, the farthest off the tips' line is on the wood,
     * so out points that way (from the string to the wood: the arrow's way); split at the widest gap between their
     * offsets, the far side is the wood (the grip half way through it) and the near side the string (the brace: how far
     * its middle stands behind the grip). Null with nothing drawn.
     */
    @Nullable
    public static Bow measureBow(List<float[]> quads) {
        if (quads.isEmpty()) return null;
        Vector3f bigN = null;
        float bigA = 0;
        for (float[] q : quads) {
            Vector3f n = newell(q);
            float a = n.length() / 2;
            if (a > bigA) { bigA = a; bigN = n.normalize(); }
        }
        if (bigN == null || bigA < 1e-9f) return null;
        List<Vector3f> drawn = drawn(quads);
        if (drawn.size() < 2) return null;
        Vector3f c0 = new Vector3f();
        for (Vector3f p : drawn) c0.add(p);
        c0.div(drawn.size());
        List<Vector3f> pts = new ArrayList<>();
        for (Vector3f p : drawn) pts.add(new Vector3f(p).sub(new Vector3f(bigN).mul(new Vector3f(p).sub(c0).dot(bigN))));
        Vector3f a = pts.get(0), b = pts.get(1);
        float best = -1;
        for (int i = 0; i < pts.size(); i++)
            for (int j = i + 1; j < pts.size(); j++) {
                float d = pts.get(i).distance(pts.get(j));
                if (d > best) { best = d; a = pts.get(i); b = pts.get(j); }
            }
        if (best < 1e-3f) return null;
        Vector3f up = new Vector3f(b).sub(a).normalize();
        if (up.dot(BOW_UP) < 0) up.negate();
        Vector3f normal = new Vector3f(bigN).sub(new Vector3f(up).mul(bigN.dot(up))).normalize();
        Vector3f across = new Vector3f(normal).cross(up).normalize();
        Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
        List<Float> off = new ArrayList<>();
        for (Vector3f p : pts) {
            Vector3f v = new Vector3f(p).sub(mid);
            if (Math.abs(v.dot(up)) <= BOW_MIDDLE * best) off.add(v.dot(across));
        }
        float far = 0;
        for (float s : off) if (Math.abs(s) > Math.abs(far)) far = s;
        float sign = far < 0 ? -1 : 1;
        // the wood and the string at the middle: the offsets either side of the widest gap between them (the string a thin
        // line, the wood a band of its two edges, the gap the brace); a middle with no gap is all wood, its string the tips' line
        float[] s = new float[off.size()];
        for (int i = 0; i < s.length; i++) s[i] = off.get(i) * sign;
        java.util.Arrays.sort(s);
        int cut = 0;
        float gap = 1e-6f;
        for (int i = 1; i < s.length; i++) if (s[i] - s[i - 1] > gap) { gap = s[i] - s[i - 1]; cut = i; }
        float depth = (s[cut] + s[s.length - 1]) / 2, behind = cut > 0 ? (s[0] + s[cut - 1]) / 2 : 0;
        Vector3f out = new Vector3f(across).mul(sign);
        return new Bow(up, out, new Vector3f(mid).add(new Vector3f(out).mul(depth)), depth - behind, best);
    }

    /**
     * Where a bow or longbow goes, whatever the item's own display transform did with it (Middle-earth's turns it for
     * Minecraft's aiming arms): measured from its faces (measureBow), its limbs along the front of the forearm (BOW_UP: up
     * when the arm is held out toward the target), its out down the arm (BOW_OUT, so the string lies toward the archer),
     * the grip on bowGrip, the length the class's (tip to tip). The same placement is tools/anim-viewer/gecko/bow.js place;
     * the bow clips hold the bow arm out with the forearm's front up and draw the string back along it. Null with nothing
     * drawn.
     */
    @Nullable
    public static Matrix4f bow(String cls, boolean left, List<float[]> quads) {
        Bow b = measureBow(quads);
        if (b == null) return null;
        // bows have no "native" scale: the class length, tip to tip (the same factor seatedLength gives such classes)
        float s = length(cls) * 16f / b.length();
        if (s <= 0) return null;
        Matrix3f to = new Matrix3f(BOW_UP, BOW_OUT, new Vector3f(BOW_UP).cross(BOW_OUT));
        Matrix3f from = new Matrix3f(b.up(), b.out(), new Vector3f(b.up()).cross(b.out()));
        Matrix3f r = to.mul(from.transpose()).scale(s);
        Vector3f moved = r.transform(new Vector3f(b.grip()));
        return new Matrix4f().set(r).setTranslation(bowGrip(left).sub(moved));
    }

    /** Newell's normal of a face: its unit normal times twice its area. */
    private static Vector3f newell(float[] q) {
        Vector3f n = new Vector3f();
        int count = q.length / 3;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            float ax = q[i * 3], ay = q[i * 3 + 1], az = q[i * 3 + 2], bx = q[j * 3], by = q[j * 3 + 1], bz = q[j * 3 + 2];
            n.add((ay - by) * (az + bz), (az - bz) * (ax + bx), (ax - bx) * (ay + by));
        }
        return n;
    }

    // ---- shields ----
    /** The forearm in the arm's part space (px): its back (the outside of the arm's box, x 3 on the left arm, -3 on the
     *  right), the plate's middle this far out from it, and the fist's height down the arm. */
    private static final float ARM_BACK = 3f, GAP = 1f, FIST_Y = 10f;
    /** How far from the plate's end a face still counts as the plate (px): the plate's two faces, one px apart. */
    private static final float PLATE_DEPTH = 1.5f;
    /** Extents (px) this close count as the same when picking the plate's axis. */
    private static final float THIN_TIE = 0.25f;

    /**
     * Where a shield goes: strapped to the forearm, whatever the item's own display transform did with it (Middle-earth's
     * shields display edge-on to the front of the arm). Its face looks along the back of the forearm (the outside of the arm,
     * +x on the left arm, -x on the right), its up along the front of the arm (-z: up when the forearm is held across the
     * chest with its back to the front), the plate's middle GAP px out from the forearm's back, level with the fist and
     * centred on the arm. quads: the item's faces as drawn in the arm (px), as for place. Null for an item with nothing
     * drawn. The same strap is in tools/anim-viewer/gecko/shield.js (place), and the clips' shield arm holds the forearm
     * across the chest with its back to the front.
     */
    @Nullable
    public static Matrix4f strap(boolean left, List<float[]> quads) {
        Plate p = plate(quads);
        if (p == null) return null;
        Vector3f out = new Vector3f(left ? 1 : -1, 0, 0), up = new Vector3f(0, 0, -1);
        Matrix3f to = new Matrix3f(out, up, new Vector3f(out).cross(up));
        Matrix3f from = new Matrix3f(p.out, p.up, new Vector3f(p.out).cross(p.up));
        Matrix3f r = to.mul(from.transpose());
        Vector3f at = new Vector3f((left ? 1 : -1) * (ARM_BACK + GAP), FIST_Y, 0);
        Vector3f moved = r.transform(new Vector3f(p.centre));
        return new Matrix4f().set(r).setTranslation(at.sub(moved));
    }

    /** A shield's plate in the arm (px): its face normal (away from the handle), its up, and its middle. */
    record Plate(Vector3f out, Vector3f up, Vector3f centre, float back, float front) {}

    private record Face(float[] q, Vector3f n, float area) {}

    /**
     * The plate of a shield's faces. Its axis: of the directions its faces look along, the one the whole item is thinnest
     * along (the plate and the handle behind it, a few px deep, against 10 px and more across), a tie going to the one more
     * face area looks along. Face area alone cannot tell: shields built of one-px strips (Middle-earth's Goblin Town,
     * Gundabad heavy and Moria shields) have as much side area across the plate as face area out of it. Its outside: the
     * end of that axis where the faces square to it gather most area within PLATE_DEPTH (the plate), away from the handle
     * behind it; a flat item with nothing behind its plate keeps the axis' own sign. Its up: in the plate, the item's up as
     * displayed (arm space -z, or -y when the plate is square to z), unless the plate is a quarter longer the other way,
     * then that long axis (pointing up or forward). Null with nothing drawn.
     */
    @Nullable
    static Plate plate(List<float[]> quads) {
        List<Face> faces = new ArrayList<>();
        for (float[] q : quads) {
            Vector3f n = new Vector3f();
            int count = q.length / 3;
            for (int i = 0; i < count; i++) {   // Newell's method: twice the area along the normal
                int j = (i + 1) % count;
                float ax = q[i * 3], ay = q[i * 3 + 1], az = q[i * 3 + 2], bx = q[j * 3], by = q[j * 3 + 1], bz = q[j * 3 + 2];
                n.add((ay - by) * (az + bz), (az - bz) * (ax + bx), (ax - bx) * (ay + by));
            }
            float len = n.length();
            if (len / 2 > 1e-9f) faces.add(new Face(q, n.div(len), len / 2));
        }
        if (faces.isEmpty()) return null;
        // the axis: of the directions the faces look along, the one the shield is thinnest along (a plate and the handle
        // behind it: a few px, against 10 px and more across); a tie (within THIN_TIE) goes to the one more face area looks along
        List<Vector3f> dirs = new ArrayList<>();
        List<float[]> dirAreaExtent = new ArrayList<>();
        for (Face f : faces) {
            int found = -1;
            for (int i = 0; i < dirs.size() && found < 0; i++) if (Math.abs(dirs.get(i).dot(f.n())) > 0.999f) found = i;
            if (found >= 0) dirAreaExtent.get(found)[0] += f.area();
            else { dirs.add(f.n()); dirAreaExtent.add(new float[] {f.area(), 0}); }
        }
        int best = 0;
        for (int i = 0; i < dirs.size(); i++) {
            Vector3f d = dirs.get(i);
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (Face f : faces) for (int k = 0; k + 2 < f.q().length; k += 3) {
                float s = d.x * f.q()[k] + d.y * f.q()[k + 1] + d.z * f.q()[k + 2];
                lo = Math.min(lo, s);
                hi = Math.max(hi, s);
            }
            dirAreaExtent.get(i)[1] = hi - lo;
            float[] a = dirAreaExtent.get(i), b = dirAreaExtent.get(best);
            if (a[1] < b[1] - THIN_TIE || (Math.abs(a[1] - b[1]) <= THIN_TIE && a[0] > b[0])) best = i;
        }
        Vector3f v = new Vector3f(dirs.get(best));
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (Face f : faces) for (int i = 0; i + 2 < f.q().length; i += 3) {
            float s = v.x * f.q()[i] + v.y * f.q()[i + 1] + v.z * f.q()[i + 2];
            lo = Math.min(lo, s);
            hi = Math.max(hi, s);
        }
        float aLo = 0, aHi = 0;
        for (Face f : faces) {
            if (Math.abs(f.n().dot(v)) <= 0.9f) continue;
            float s = along(f, v);
            if (Math.abs(s - lo) <= PLATE_DEPTH) aLo += f.area();
            if (Math.abs(s - hi) <= PLATE_DEPTH) aHi += f.area();
        }
        boolean atLo = aLo > aHi * 1.001f;
        Vector3f out = atLo ? new Vector3f(v).negate() : new Vector3f(v);
        float end = atLo ? lo : hi;
        List<Vector3f> pts = new ArrayList<>();
        for (Face f : faces) {
            if (Math.abs(f.n().dot(v)) <= 0.9f || Math.abs(along(f, v) - end) > PLATE_DEPTH) continue;
            for (int i = 0; i + 2 < f.q().length; i += 3) pts.add(new Vector3f(f.q()[i], f.q()[i + 1], f.q()[i + 2]));
        }
        Vector3f forward = new Vector3f(0, 0, -1), e1 = inPlane(forward, out);
        if (new Vector3f(forward).sub(new Vector3f(out).mul(forward.dot(out))).length() < 0.5f) e1 = inPlane(new Vector3f(0, -1, 0), out);
        Vector3f e2 = new Vector3f(out).cross(e1);
        float[] r1 = range(pts, e1), r2 = range(pts, e2), ro = range(pts, out);
        Vector3f up = e1;
        if (r2[1] - r2[0] > 1.25f * (r1[1] - r1[0])) up = e2.dot(0, -1, -1) < 0 ? new Vector3f(e2).negate() : e2;
        Vector3f centre = new Vector3f(e1).mul((r1[0] + r1[1]) / 2).add(new Vector3f(e2).mul((r2[0] + r2[1]) / 2)).add(new Vector3f(out).mul((ro[0] + ro[1]) / 2));
        return new Plate(out, new Vector3f(up), centre, ro[0], ro[1]);
    }

    private static float along(Face f, Vector3f v) {
        float s = 0;
        int count = f.q().length / 3;
        for (int i = 0; i < count; i++) s += v.x * f.q()[i * 3] + v.y * f.q()[i * 3 + 1] + v.z * f.q()[i * 3 + 2];
        return s / count;
    }

    private static Vector3f inPlane(Vector3f d, Vector3f normal) {
        return new Vector3f(d).sub(new Vector3f(normal).mul(d.dot(normal))).normalize();
    }

    private static float[] range(List<Vector3f> pts, Vector3f d) {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (Vector3f p : pts) {
            float s = p.dot(d);
            lo = Math.min(lo, s);
            hi = Math.max(hi, s);
        }
        return new float[] {lo, hi};
    }

    /** The drawn weapon's points: every face's but a generated model's big faces (see the class comment). */
    private static List<Vector3f> drawn(List<float[]> quads) {
        float[] areas = new float[quads.size()];
        float max = 0;
        for (int i = 0; i < areas.length; i++) max = Math.max(max, areas[i] = area(quads.get(i)));
        List<Vector3f> kept = new ArrayList<>(), all = new ArrayList<>();
        for (int i = 0; i < areas.length; i++) {
            float[] q = quads.get(i);
            boolean face = areas[i] > FACE_SHARE * max && areas[i] > FACE_MIN;
            for (int k = 0; k + 2 < q.length; k += 3) {
                Vector3f p = new Vector3f(q[k], q[k + 1], q[k + 2]);
                all.add(p);
                if (!face) kept.add(p);
            }
        }
        return kept.isEmpty() ? all : kept;
    }

    /** A face's area (px²), as a fan of triangles from its first vertex. */
    private static float area(float[] q) {
        float a = 0;
        Vector3f o = new Vector3f(q[0], q[1], q[2]), u = new Vector3f(), v = new Vector3f();
        for (int k = 3; k + 5 < q.length; k += 3) {
            u.set(q[k], q[k + 1], q[k + 2]).sub(o);
            v.set(q[k + 3], q[k + 4], q[k + 5]).sub(o);
            a += u.cross(v).length() / 2;
        }
        return a;
    }

    /** The farthest pair's distance, two passes: the point farthest from any point, then the point farthest from that. */
    private static float diameter(List<Vector3f> pts) {
        Vector3f a = farthest(pts, pts.get(0));
        return a.distance(farthest(pts, a));
    }

    private static Vector3f farthest(List<Vector3f> pts, Vector3f from) {
        Vector3f best = from;
        for (Vector3f p : pts) if (p.distanceSquared(from) > best.distanceSquared(from)) best = p;
        return best;
    }

    /** An arm-space matrix in px as the same move in blocks (the PoseStack's unit). */
    static Matrix4f toBlocks(Matrix4f px) {
        return new Matrix4f().scale(1 / 16f).mul(px).scale(16f);
    }

    private static synchronized Map<String, Grip> grips() {
        if (grips != null) return grips;
        grips = new HashMap<>();
        try (InputStream in = WeaponGrip.class.getResourceAsStream("/assets/lotr_revival/weapon_grips.json")) {
            if (in == null) return grips;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                if (!o.has("length")) continue;
                grips.put(e.getKey(), new Grip(o.get("length").getAsFloat(), vec(o.get("rotate")), vec(o.get("offset"))));
            }
        } catch (Exception e) {
            LOG.warn("lotr_revival: could not read weapon_grips.json", e);
        }
        return grips;
    }

    private static Vector3f vec(@Nullable JsonElement e) {
        if (e == null || !e.isJsonArray()) return new Vector3f();
        var a = e.getAsJsonArray();
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }
}
