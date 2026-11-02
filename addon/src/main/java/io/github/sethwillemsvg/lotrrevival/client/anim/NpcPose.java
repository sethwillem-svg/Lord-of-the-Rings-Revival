package io.github.sethwillemsvg.lotrrevival.client.anim;

import com.arcanc.pulselib.content.animatable.PAnimationController;
import com.arcanc.pulselib.content.model.animation.PPose;
import com.arcanc.pulselib.content.model.baked.PBakedModel;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An NPC's PulseLib pose for one frame, as Minecraft model-space transforms. PulseLib draws a bone as translate(pose
 * translation) then rotate(pose rotation) under its parent, so a bone's motion is W_pose * W_bind^-1 (rest point to posed
 * point) whatever its pivots; that is then moved from rig space (blocks, y up, facing -z; tools/anim-viewer/gecko) into
 * Minecraft's model space (blocks, y down, x mirrored, origin at the neck): m -> C^-1 A C with C(m) = Rz(180°) m + (0, 1.5, 0).
 * The baked model is fetched on every call, never kept, so a resource reload is safe.
 */
public final class NpcPose {
    private static final Matrix4f C = new Matrix4f().translation(0, 1.5f, 0).rotateZ((float) Math.PI);
    private static final Matrix4f C_INV = new Matrix4f(C).invert();

    private final PBakedModel model;
    private final Matrix4f[] rig;   // rest -> posed, rig space

    private NpcPose(PBakedModel model, Matrix4f[] rig) {
        this.model = model;
        this.rig = rig;
    }

    /** The pose of an NPC that has an animatable and a loaded model; null otherwise. */
    @Nullable
    public static NpcPose of(LivingEntity entity, float partialTick) {
        return of(entity, partialTick, 0f);
    }

    /**
     * As of(entity, partialTick), with both arms (and what they hold) turned about their shoulders by aimPitch (radians,
     * Minecraft's head pitch: positive looks down), as Minecraft aims a bow with the head.
     */
    @Nullable
    public static NpcPose of(LivingEntity entity, float partialTick, float aimPitch) {
        NpcAnimatable a = NpcAnimatable.get(entity);
        if (a == null) return null;
        PBakedModel model = a.model();
        if (model == null || a.controller() == null) return null;
        List<PAnimationController<NpcAnimatable>> controllers = new ArrayList<>();
        a.controllers().forEach(controllers::add);
        PPose pose = model.evaluate(controllers, Map.of(), partialTick);
        Matrix4f[] posed = world(model, pose), bind = world(model, model.bindPose());
        Matrix4f[] rig = new Matrix4f[posed.length];
        for (int i = 0; i < posed.length; i++) rig[i] = new Matrix4f(posed[i]).mul(new Matrix4f(bind[i]).invert());
        if (aimPitch != 0f) for (String side : new String[] {"right", "left"}) aim(model, rig, side, aimPitch);
        return new NpcPose(model, rig);
    }

    /** Turns an arm, its elbow and its weapon bone about the posed shoulder (rig space: Minecraft's +x pitch is -x here). */
    private static void aim(PBakedModel model, Matrix4f[] rig, String side, float pitch) {
        int arm = model.boneIndex(side + "_arm");
        if (arm < 0) return;
        Vector3f shoulder = rig[arm].transformPosition(shoulderRest(side));
        Matrix4f turn = new Matrix4f().translation(shoulder).rotateX(-pitch).translate(-shoulder.x, -shoulder.y, -shoulder.z);
        for (String bone : new String[] {side + "_arm", side + "_elbow", side.equals("right") ? "weapon_r" : "weapon_l"}) {
            int i = model.boneIndex(bone);
            if (i >= 0) rig[i] = new Matrix4f(turn).mul(rig[i]);
        }
    }

    /** The shoulder in rig space at rest (blocks): AnimationsPlus' arm pivots (tools/anim-viewer/gecko/rig.js). */
    private static Vector3f shoulderRest(String side) {
        return new Vector3f(side.equals("right") ? 4.5f / 16f : -4.5f / 16f, 22.5f / 16f, 0f);
    }

    private static Matrix4f[] world(PBakedModel model, PPose pose) {
        int n = model.boneCount();
        int[] parent = model.parentIndices();
        Matrix4f[] w = new Matrix4f[n];
        for (int i = 0; i < n; i++) {
            Vector3f t = pose.translation(i);
            Quaternionf q = pose.rotation(i);
            Matrix4f local = new Matrix4f().translation(t).rotate(q);
            w[i] = parent[i] < 0 || w[parent[i]] == null ? local : new Matrix4f(w[parent[i]]).mul(local);
        }
        return w;
    }

    private Matrix4f rig(String bone) {
        int i = model.boneIndex(bone);
        return i < 0 || rig[i] == null ? new Matrix4f() : rig[i];
    }

    /** A bone's motion in Minecraft model space (blocks): rest point -> posed point. Identity for an unknown bone. */
    public Matrix4f part(String bone) {
        return new Matrix4f(C_INV).mul(rig(bone)).mul(C);
    }

    /**
     * An elbow or knee bend (radians) about the limb's x axis in Minecraft's part space, as the bend deformers take it
     * (client.render.NpcJoints): negative folds the lower half forward (elbows), positive back (knees). Only the hinge
     * part of the joint's turn is kept.
     */
    public float bend(String joint) {
        String parent = joint.replace("elbow", "arm").replace("knee", "leg");
        Matrix4f local = new Matrix4f(rig(parent)).invert().mul(rig(joint));
        Vector3f d = local.transformDirection(new Vector3f(0, -1, 0));
        return (float) Math.atan2(d.z, -d.y);
    }

    /** The held item's motion (weapon_r or weapon_l) in Minecraft model space (blocks), elbow and nudge included. */
    public Matrix4f weapon(boolean left) {
        return part(left ? "weapon_l" : "weapon_r");
    }
}
