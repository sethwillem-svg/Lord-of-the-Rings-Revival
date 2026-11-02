package io.github.sethwillemsvg.lotrrevival.client.render;

import com.arcanc.pulselib.content.model.deformer.PBendDefinition;
import com.arcanc.pulselib.content.model.deformer.PBendDeformer;
import com.arcanc.pulselib.content.model.deformer.PChannelReference;
import com.arcanc.pulselib.content.model.deformer.PDeformerInstance;
import com.arcanc.pulselib.content.model.deformer.PDeformerStack;
import com.arcanc.pulselib.content.model.deformer.PMeshDeformation;
import com.arcanc.pulselib.content.model.deformer.PTwistDefinition;
import com.arcanc.pulselib.content.model.deformer.PTwistDeformer;
import com.arcanc.pulselib.content.model.deformer.gpu.PGpuDeformerBuffers;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Flexible elbows, knees and waist: PulseLib bend and twist deformers on the biped's meshes, run in PulseLib's vertex shader
 * with the joint angles of the pose (NpcAnimator.joints, NpcClip.JOINTS order). The zones are the ones the baker uses
 * (tools/anim-viewer/bake_vanilla.js JOINTS, in px there): each op works on [origin, origin + extent] along the part's +y; a
 * bend curves that zone and turns everything below it rigidly by the angle, a twist turns the zone progressively.
 */
final class NpcJoints {
    private NpcJoints() {}

    // part indices of NpcBipedModel.parts
    private static final int BODY = 2, RIGHT_ARM = 3, LEFT_ARM = 4, RIGHT_LEG = 5, LEFT_LEG = 6;
    // NpcClip.JOINTS indices
    private static final int R_ELBOW = 0, L_ELBOW = 1, R_KNEE = 2, L_KNEE = 3, YAW = 4, PITCH = 5, ROLL = 6;

    private static final float PX = 1f / 16f;
    private static final float WAIST_AT = 6 * PX, WAIST_LEN = 3 * PX;
    private static final float ELBOW_AT = 3 * PX, ELBOW_LEN = 2 * PX;
    private static final float KNEE_AT = 5 * PX, KNEE_LEN = 2 * PX;
    private static final Vector3f X = new Vector3f(1, 0, 0), Y = new Vector3f(0, 1, 0), Z = new Vector3f(0, 0, 1);

    private static final PChannelReference<Float> WAIST_YAW = new PChannelReference<>("waist_yaw", 0f);
    private static final PChannelReference<Float> WAIST_PITCH = new PChannelReference<>("waist_pitch", 0f);
    private static final PChannelReference<Float> WAIST_ROLL = new PChannelReference<>("waist_roll", 0f);
    private static final PChannelReference<Float> BEND = new PChannelReference<>("bend", 0f);

    private static final PDeformerStack WAIST = PDeformerStack.compile(List.of(
            new PDeformerInstance<>(PTwistDeformer.INSTANCE, new PTwistDefinition(at(WAIST_AT), Y, WAIST_LEN, 0f, WAIST_YAW)),
            new PDeformerInstance<>(PBendDeformer.INSTANCE, new PBendDefinition(at(WAIST_AT), Y, X, WAIST_LEN, 0f, WAIST_PITCH)),
            new PDeformerInstance<>(PBendDeformer.INSTANCE, new PBendDefinition(at(WAIST_AT), Y, Z, WAIST_LEN, 0f, WAIST_ROLL))));
    private static final PDeformerStack ELBOW = bend(ELBOW_AT, ELBOW_LEN);
    private static final PDeformerStack KNEE = bend(KNEE_AT, KNEE_LEN);

    /** One object per independently deformed part, as PulseLib asks (it keys its buffers by it). */
    private static final Object[] KEYS = {new Object(), new Object(), new Object(), new Object(), new Object(), new Object(), new Object()};

    private static Vector3f at(float y) {
        return new Vector3f(0, y, 0);
    }

    private static PDeformerStack bend(float origin, float extent) {
        return PDeformerStack.compile(List.of(
                new PDeformerInstance<>(PBendDeformer.INSTANCE, new PBendDefinition(at(origin), Y, X, extent, 0f, BEND))));
    }

    /** Heights (part space, blocks) where a part's faces are cut so the bend zone has enough vertices to curve: every half
     *  pixel across the zone. Empty for parts that do not bend. */
    static float[] cuts(int part) {
        return switch (part) {
            case BODY -> steps(WAIST_AT, WAIST_LEN);
            case RIGHT_ARM, LEFT_ARM -> steps(ELBOW_AT, ELBOW_LEN);
            case RIGHT_LEG, LEFT_LEG -> steps(KNEE_AT, KNEE_LEN);
            default -> new float[0];
        };
    }

    private static float[] steps(float from, float len) {
        int n = Math.round(len / (PX / 2));
        float[] out = new float[n + 1];
        for (int i = 0; i <= n; i++) out[i] = from + len * i / n;
        return out;
    }

    /** Uploads this part's joint angles for one draw and returns the offsets its instances carry; NONE when it is straight. */
    static PGpuDeformerBuffers.Submission submit(int part, @Nullable float[] joints) {
        if (joints == null) return PGpuDeformerBuffers.NONE;
        switch (part) {
            case BODY -> {
                float yaw = joints[YAW], pitch = joints[PITCH], roll = joints[ROLL];
                if (still(yaw) && still(pitch) && still(roll)) return PGpuDeformerBuffers.NONE;
                return PGpuDeformerBuffers.submit(new PMeshDeformation(WAIST, ref -> switch (ref.name()) {
                    case "waist_yaw" -> yaw;
                    case "waist_pitch" -> pitch;
                    case "waist_roll" -> roll;
                    default -> ref.defaultValue();
                }, KEYS[part], 0));
            }
            case RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG -> {
                float a = joints[angleIndex(part)];
                if (still(a)) return PGpuDeformerBuffers.NONE;
                PDeformerStack stack = part == RIGHT_ARM || part == LEFT_ARM ? ELBOW : KNEE;
                return PGpuDeformerBuffers.submit(new PMeshDeformation(stack, ref -> ref.name().equals("bend") ? a : ref.defaultValue(), KEYS[part], 0));
            }
            default -> {
                return PGpuDeformerBuffers.NONE;
            }
        }
    }

    private static int angleIndex(int part) {
        return switch (part) {
            case RIGHT_ARM -> R_ELBOW;
            case LEFT_ARM -> L_ELBOW;
            case RIGHT_LEG -> R_KNEE;
            default -> L_KNEE;
        };
    }

    private static boolean still(float a) {
        return Math.abs(a) < 1e-4f;
    }

    /**
     * How an arm's elbow bend moves everything below the bend zone (the hand and what it holds), in the arm's space: the far
     * side of PulseLib's bend turns rigidly by the angle about the arc's centre. Null when the arm is straight.
     */
    @Nullable
    static Matrix4f belowElbow(int part, @Nullable float[] joints) {
        if (joints == null || (part != RIGHT_ARM && part != LEFT_ARM)) return null;
        float a = joints[angleIndex(part)];
        if (still(a)) return null;
        // far point p' = q (p - O) + O + R z - q (R z + e y), with R = e / a (radial axis z = x cross y)
        float r = ELBOW_LEN / a;
        Quaternionf q = new Quaternionf().fromAxisAngleRad(X, a);
        Vector3f o = at(ELBOW_AT);
        Vector3f t = new Vector3f(0, ELBOW_LEN, r);
        q.transform(t);
        Vector3f translation = new Vector3f(o).add(0, 0, r).sub(t);
        return new Matrix4f().translate(translation).rotate(q).translate(-o.x, -o.y, -o.z);
    }
}
