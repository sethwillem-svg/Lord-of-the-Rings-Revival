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
import java.util.HashMap;
import java.util.Map;

/**
 * How a held item sits in the hand: where Minecraft's held-item layer seats it (Middle-earth's models put the handle in the
 * fist), scaled about the fist to its weapon class's length, then turned and moved by the class's extras. Classes and
 * extras are in assets/lotr_revival/weapon_grips.json ({"<class>": {"length": blocks, "rotate": [x, y, z] degrees,
 * "offset": [x, y, z] px}}), the same file the viewer uses (tools/anim-viewer/gecko/held.js). Points and matrices are in the
 * arm's part space, px (y down); the clip's own nudge comes from the weapon bones (client.anim.NpcPose.weapon).
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

    /** The front of the fist in the arm's part space (px): the weapon bones' pivot, about which items are scaled. */
    static Vector3f fist(boolean left) {
        return new Vector3f(left ? 1 : -1, 10, -2);
    }

    /** Scale to the class length about the fist, then the class's turn and offset; null for classes without a length. */
    @Nullable
    static Matrix4f place(@Nullable String cls, boolean left, float[] pts) {
        Grip g = cls == null ? null : grips().get(cls);
        if (g == null || pts.length < 3) return null;
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < pts.length; i++) {
            lo[i % 3] = Math.min(lo[i % 3], pts[i]);
            hi[i % 3] = Math.max(hi[i % 3], pts[i]);
        }
        float extent = Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2]));
        if (extent < 1e-3f) return null;
        float s = g.length() * 16f / extent;
        Vector3f f = fist(left);
        float sign = left ? -1 : 1;   // the left hand mirrors the class's turn and sideways offset
        return new Matrix4f().translate(f).translate(sign * g.offset().x, g.offset().y, g.offset().z)
                .rotateZYX((float) Math.toRadians(sign * g.rotate().z), (float) Math.toRadians(sign * g.rotate().y), (float) Math.toRadians(g.rotate().x))
                .scale(s).translate(-f.x, -f.y, -f.z);
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
