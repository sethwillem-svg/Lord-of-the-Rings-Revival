package io.github.sethwillemsvg.lotrrevival.client.anim;

import com.mojang.logging.LogUtils;
import io.github.sethwillemsvg.lotrrevival.combat.NpcRaces;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Puts each NPC's PulseLib pose (NpcAnimatable, NpcPose) on the Middle-earth mod's NPC model (a vanilla HumanoidModel
 * subclass) by overwriting the six vanilla parts after its own setupAnim, and keeps the elbow and knee bends for the PulseLib
 * renderer's deformers (joints). Vanilla's and Middle-earth's renderers draw the same parts, without bends. Nothing of the
 * Middle-earth mod is compiled against here: its classes are matched by name and its NPC type is read by reflection.
 * Sleeping, swimming, gliding and crouching keep the Middle-earth pose.
 */
public final class NpcAnimator {
    static final Logger LOG = LogUtils.getLogger();

    private static final String NPC_MODEL = "net.sevenstars.middleearth.entity.npcs.renderer.NpcEntityModel";
    private static final String OUR_MODEL = "io.github.sethwillemsvg.lotrrevival.client.render.NpcBipedModel";
    private static final String NPC_ENTITY = "net.sevenstars.middleearth.entity.npcs.NpcEntity";
    /** Joint angles in the order the renderer's deformers read them (client.render.NpcJoints). */
    public static final String[] JOINTS = {"right_elbow", "left_elbow", "right_knee", "left_knee", "waist_yaw", "waist_pitch", "waist_roll"};
    /** The rig bone and vanilla rest pivot (px, Minecraft model space) of each part. */
    private static final String[] BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    private static final Vector3f[] PIVOTS = {new Vector3f(0, 0, 0), new Vector3f(0, 0, 0), new Vector3f(-5, 2, 0), new Vector3f(5, 2, 0),
            new Vector3f(-1.9f, 12, 0), new Vector3f(1.9f, 12, 0)};

    private static final Map<LivingEntity, float[]> JOINT_ANGLES = new WeakHashMap<>();
    private static final Map<Class<?>, Boolean> NPC_CLASSES = new WeakHashMap<>();

    private NpcAnimator() {}

    /** Stance of a unit in formation: "" (none), guard, shield_brace, spear_brace or march (specs/addon/hiring-reputation.md).
     *  The squad code supplies it per unit; until then /lotrrender stance sets testStance for every NPC. */
    public static Function<LivingEntity, String> stanceOf = e -> NpcAnimator.testStance;
    public static String testStance = "";
    /** While true (the viewer export), NPC models are left in Minecraft's rest pose: every part unrotated. */
    public static boolean restPose;

    /** Middle-earth's NPC model, or the biped our own renderers draw (client.render.NpcBipedModel). */
    public static boolean isNpcModel(Object model) {
        String n = model.getClass().getName();
        return n.equals(NPC_MODEL) || n.equals(OUR_MODEL);
    }

    public static boolean isNpc(LivingEntity entity) {
        return NPC_CLASSES.computeIfAbsent(entity.getClass(), c -> {
            for (Class<?> k = c; k != null; k = k.getSuperclass()) if (k.getName().equals(NPC_ENTITY)) return true;
            return false;
        });
    }

    /** Called at the end of HumanoidModel.setupAnim (see HumanoidModelMixin). */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, float limbSwing, float limbSwingAmount,
                             float ageInTicks, float netHeadYaw, float headPitch) {
        if (!isNpcModel(model)) return;
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        if (restPose) {
            for (ModelPart p : parts) p.setRotation(0f, 0f, 0f);
            model.hat.setRotation(0f, 0f, 0f);
            return;
        }
        NpcAnimatable a = NpcAnimatable.get(entity);
        boolean ours = a != null && !entity.isSleeping() && !entity.isSwimming() && !entity.isFallFlying() && !model.crouching;
        String state = a == null ? "" : a.state();
        float aim = state.equals("bow_aim") || state.equals("crossbow_aim") ? headPitch * Mth.DEG_TO_RAD : 0f;
        NpcPose pose = ours ? NpcPose.of(entity, ageInTicks - entity.tickCount, aim) : null;
        if (a != null) a.lastPose = pose;
        if (pose == null) {   // Minecraft's pose: straight limbs
            JOINT_ANGLES.remove(entity);
            return;
        }
        for (int i = 0; i < parts.length; i++) {
            Matrix4f m = pose.part(BONES[i]);
            Vector3f pivot = m.transformPosition(new Vector3f(PIVOTS[i]).div(16f)).mul(16f);
            Quaternionf q = m.getUnnormalizedRotation(new Quaternionf()).normalize();
            if (i == 0) q.mul(new Quaternionf().rotationZYX(0f, netHeadYaw * Mth.DEG_TO_RAD, headPitch * Mth.DEG_TO_RAD));
            set(parts[i], q, pivot);
        }
        model.hat.copyFrom(model.head);
        float[] j = JOINT_ANGLES.computeIfAbsent(entity, e -> new float[JOINTS.length]);
        for (int i = 0; i < 4; i++) j[i] = pose.bend(JOINTS[i]);
    }

    /** The server started an attack (combat.AttackTiming): play it at speed. */
    public static void startAttack(LivingEntity entity, String animation, float speed) {
        if (isNpc(entity)) NpcAnimatable.of(entity).attack(entity, animation, speed);
    }

    /** The NPC's held hit landed (combat.Hitstop): its swing freezes for ticks. */
    public static void hitstop(LivingEntity entity, int ticks) {
        if (isNpc(entity)) NpcAnimatable.of(entity).hitstop(entity, ticks);
    }

    /** An NPC took a held hit: its hurt flinch freezes for ticks. Players get nothing (vanilla's hurt shake is enough). */
    public static void hurtStop(LivingEntity entity, int ticks) {
        if (isNpc(entity)) NpcAnimatable.of(entity).hurtStop(ticks);
    }

    /** The joint angles of the pose last applied to this NPC (JOINTS order, radians), or null if it has none. Read by the
     *  PulseLib renderer, which bends the limbs with them. */
    public static float[] joints(LivingEntity entity) {
        return JOINT_ANGLES.get(entity);
    }

    /** ModelPart uses rotationZYX(zRot, yRot, xRot): decompose q = Rz * Ry * Rx. */
    private static void set(ModelPart part, Quaternionf q, Vector3f p) {
        float x = q.x, y = q.y, z = q.z, w = q.w;
        part.xRot = (float) Math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y));
        part.yRot = (float) Math.asin(Mth.clamp(2 * (w * y - z * x), -1f, 1f));
        part.zRot = (float) Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z));
        part.x = p.x;
        part.y = p.y;
        part.z = p.z;
    }

    // ---- race: from the NPC type's race id (synced NpcData), else from the NPC type id ----
    private static Method typeMethod, raceMethod, idMethod;

    /** Which race's clips an NPC plays: man, elf, dwarf, hobbit or orc. */
    public static String raceOf(LivingEntity entity) {
        String r = race(entity);
        return r == null ? "man" : r;
    }

    private static String race(LivingEntity entity) {
        String key = null;
        try {
            if (typeMethod == null) typeMethod = entity.getClass().getMethod("getNpcType");
            Object type = typeMethod.invoke(entity);
            if (type != null) {
                if (raceMethod == null) raceMethod = type.getClass().getMethod("getRace");
                if (raceMethod.invoke(type) instanceof ResourceLocation r) key = r.getPath();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        if (key == null) {
            try {
                if (idMethod == null) idMethod = entity.getClass().getMethod("getNpcTypeIdentifier");
                if (idMethod.invoke(entity) instanceof ResourceLocation r) key = r.getPath();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return key == null ? null : NpcRaces.fromKey(key);
    }
}
