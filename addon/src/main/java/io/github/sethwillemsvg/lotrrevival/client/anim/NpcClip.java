package io.github.sethwillemsvg.lotrrevival.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One baked clip for the six vanilla biped parts (written by tools/anim-viewer/bake_vanilla.js).
 * Positions are vanilla model space (px, y down); rotations are quaternions for the part's rotationZYX.
 */
public final class NpcClip {
    public static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    /** Flexible joint angles (radians), bent in game by client.render.NpcJoints. */
    public static final String[] JOINTS = {"right_elbow", "left_elbow", "right_knee", "left_knee", "waist_yaw", "waist_pitch", "waist_roll"};

    public final float dt;
    public final float length;
    public final boolean loop;
    public final float hit;
    private final int frames;
    private final float[][] rot = new float[PARTS.length][];
    private final float[][] pos = new float[PARTS.length][];
    private final float[][] joints = new float[JOINTS.length][];   // null entries: the clip has no such joint (older bakes)

    private NpcClip(JsonObject json) {
        dt = json.get("dt").getAsFloat();
        length = json.get("length").getAsFloat();
        loop = json.get("loop").getAsBoolean();
        hit = json.has("hit") ? json.get("hit").getAsFloat() : 0f;
        JsonObject parts = json.getAsJsonObject("parts");
        for (int i = 0; i < PARTS.length; i++) {
            JsonObject p = parts.getAsJsonObject(PARTS[i]);
            rot[i] = floats(p.getAsJsonArray("q"));
            pos[i] = floats(p.getAsJsonArray("p"));
        }
        frames = rot[0].length / 4;
        JsonObject j = json.has("joints") ? json.getAsJsonObject("joints") : null;
        for (int i = 0; i < JOINTS.length; i++)
            if (j != null && j.has(JOINTS[i])) joints[i] = floats(j.getAsJsonArray(JOINTS[i]));
    }

    /** Frame position at time t: loops wrap, one-shots hold their last frame. */
    private float frameAt(float t) {
        if (loop && length > 0f) return ((t % length) + length) % length / dt;
        return Math.max(0f, Math.min(t, length)) / dt;
    }

    /** Joint angles at time t into out (JOINTS order; 0 for joints the clip lacks). */
    public void sampleJoints(float t, float[] out) {
        float f = frameAt(t);
        int f0 = Math.min((int) f, frames - 1), f1 = Math.min(f0 + 1, frames - 1);
        float a = f - f0;
        for (int i = 0; i < JOINTS.length; i++) {
            float[] v = joints[i];
            out[i] = v == null ? 0f : v[f0] + (v[f1] - v[f0]) * a;
        }
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
        return out;
    }

    /** Sample part i at time t (seconds) into the pose arrays. Loops wrap; one-shots hold their last frame. */
    public void sample(int part, float t, Quaternionf q, Vector3f p) {
        float f = frameAt(t);
        int f0 = Math.min((int) f, frames - 1), f1 = Math.min(f0 + 1, frames - 1);
        float a = f - f0;
        float[] r = rot[part], v = pos[part];
        Quaternionf q0 = new Quaternionf(r[f0 * 4], r[f0 * 4 + 1], r[f0 * 4 + 2], r[f0 * 4 + 3]);
        Quaternionf q1 = new Quaternionf(r[f1 * 4], r[f1 * 4 + 1], r[f1 * 4 + 2], r[f1 * 4 + 3]);
        q0.slerp(q1, Math.max(0f, Math.min(a, 1f)), q);
        p.set(v[f0 * 3] + (v[f1 * 3] - v[f0 * 3]) * a, v[f0 * 3 + 1] + (v[f1 * 3 + 1] - v[f0 * 3 + 1]) * a,
              v[f0 * 3 + 2] + (v[f1 * 3 + 2] - v[f0 * 3 + 2]) * a);
    }

    // ---- loading: assets/lotr_revival/clips/<race>/<slot>.json, read from the mod jar once ----
    private static final Map<String, Optional<NpcClip>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Integer> ATTACK_COUNTS = new HashMap<>();

    public static NpcClip get(String race, String slot) {
        return CACHE.computeIfAbsent(race + "/" + slot, NpcClip::load).orElse(null);
    }

    /** How many attack_<kind>_N clips exist for the race (1..N). */
    public static synchronized int attackCount(String race, String kind) {
        return ATTACK_COUNTS.computeIfAbsent(race + "/" + kind, k -> {
            int n = 0;
            while (get(race, "attack_" + kind + "_" + (n + 1)) != null) n++;
            return n;
        });
    }

    private static Optional<NpcClip> load(String key) {
        String file = "/assets/lotr_revival/clips/" + key + ".json";
        try (InputStream in = NpcClip.class.getResourceAsStream(file)) {
            if (in == null) return Optional.empty();
            return Optional.of(new NpcClip(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()));
        } catch (Exception e) {
            NpcAnimator.LOG.warn("could not read clip {}", file, e);
            return Optional.empty();
        }
    }
}
