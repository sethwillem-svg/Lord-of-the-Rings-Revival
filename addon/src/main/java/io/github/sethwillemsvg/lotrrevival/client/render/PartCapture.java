package io.github.sethwillemsvg.lotrrevival.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * While active, notes which humanoid body part every recorded triangle is drawn by, the way PulseLib / GeckoLib armour binds
 * each bone to a body part: when a HumanoidModel draws, its top-level parts are head, hat, body, right_arm, left_arm,
 * right_leg, left_leg (NpcBipedModel.parts order). Recorded vertices are moved into that part's own space, so the recording
 * can be replayed on any pose by giving each part its posed matrix. Fed by the two capture mixins; costs one boolean check
 * per model and ModelPart draw when inactive. Render thread only.
 */
public final class PartCapture {
    static boolean active;
    /** Body part the current vertices belong to (-1: not inside a humanoid part) and which model draw this is (layer order). */
    static int part = -1, draw = -1;
    static final Matrix4f toLocal = new Matrix4f();
    static final Matrix3f toLocalNormal = new Matrix3f();

    private static final Object NOT_HUMANOID = new Object();
    private static final Deque<Object> models = new ArrayDeque<>();
    private static int depth;

    private PartCapture() {}

    static void begin() {
        active = true;
        part = -1;
        draw = -1;
        depth = 0;
        models.clear();
    }

    static void end() {
        active = false;
        part = -1;
        models.clear();
    }

    /** A model started drawing (AgeableListModel.renderToBuffer HEAD). */
    public static void modelStart(Object model) {
        if (!active) return;
        draw++;
        models.push(model instanceof HumanoidModel<?> ? model : NOT_HUMANOID);
    }

    /** A model finished drawing (AgeableListModel.renderToBuffer RETURN). */
    public static void modelEnd() {
        if (active && !models.isEmpty()) models.pop();
    }

    /** A ModelPart started drawing (ModelPart.render HEAD): top-level parts of a humanoid model set the current body part. */
    public static void partStart(ModelPart p) {
        if (!active) return;
        if (++depth != 1 || !(models.peek() instanceof HumanoidModel<?> m)) return;
        ModelPart[] parts = {m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != p) continue;
            part = i;
            PoseStack ps = new PoseStack();
            p.translateAndRotate(ps);
            toLocal.set(ps.last().pose()).invert();
            toLocalNormal.set(ps.last().normal()).invert();
            return;
        }
    }

    /** A ModelPart finished drawing (ModelPart.render RETURN). */
    public static void partEnd() {
        if (!active) return;
        if (--depth == 0) part = -1;
    }
}
