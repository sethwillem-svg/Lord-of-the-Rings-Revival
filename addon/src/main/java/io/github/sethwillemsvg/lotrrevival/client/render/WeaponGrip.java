package io.github.sethwillemsvg.lotrrevival.client.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.github.sethwillemsvg.lotrrevival.combat.WeaponClass;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
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
     * the class's turn and offset. quads: the item's faces as Minecraft draws it in the arm (px), x, y, z per vertex. Null
     * for classes without a length, and for items with nothing drawn.
     */
    @Nullable
    public static Matrix4f place(@Nullable String cls, boolean left, List<float[]> quads) {
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
