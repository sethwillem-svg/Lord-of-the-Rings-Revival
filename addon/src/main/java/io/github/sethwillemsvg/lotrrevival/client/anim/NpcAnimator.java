package io.github.sethwillemsvg.lotrrevival.client.anim;

import com.mojang.logging.LogUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Plays our baked clips on the Middle-earth mod's NPC model (a vanilla HumanoidModel subclass) by overwriting the six
 * vanilla parts after its own setupAnim. Nothing of the Middle-earth mod is compiled against: its classes are matched by
 * name and its NPC type is read by reflection.
 *
 * Clip choice per frame: death > attack (on each swing) > hurt (on each hit) > idle / walk / run by limb swing speed.
 * Bows, crossbows, shields, spears, riding, swimming, sleeping and crouching keep the Middle-earth pose.
 */
public final class NpcAnimator {
    static final Logger LOG = LogUtils.getLogger();

    private static final String NPC_MODEL = "net.sevenstars.middleearth.entity.npcs.renderer.NpcEntityModel";
    private static final String NPC_ENTITY = "net.sevenstars.middleearth.entity.npcs.NpcEntity";

    private static final float WALK_SPEED = 0.1f, RUN_SPEED = 0.7f;   // limbSwingAmount thresholds
    private static final float WALK_CYCLE = 9.43f, RUN_CYCLE = 12f;  // limbSwing per gait cycle (vanilla legs: 2pi / 0.6662)
    private static final float BLEND_TICKS = 4f, DEATH_TICKS = 19f;

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();
    private static final Map<Class<?>, Boolean> NPC_CLASSES = new WeakHashMap<>();

    private NpcAnimator() {}

    private static final class State {
        String race;
        int raceRetry;
        String slot;            // clip playing now
        float slotStart;        // ageInTicks when it started (one-shots)
        boolean wasSwinging;
        int lastHurtTime;
        int attacks;
        float blendStart = -1e9f;
        final Quaternionf[] fromRot = new Quaternionf[6];
        final Vector3f[] fromPos = new Vector3f[6];
        final Quaternionf[] lastRot = new Quaternionf[6];
        final Vector3f[] lastPos = new Vector3f[6];
        State() {
            for (int i = 0; i < 6; i++) {
                fromRot[i] = new Quaternionf(); fromPos[i] = new Vector3f();
                lastRot[i] = new Quaternionf(); lastPos[i] = new Vector3f();
            }
        }
    }

    public static boolean isNpcModel(Object model) {
        return model.getClass().getName().equals(NPC_MODEL);
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
        if (!isNpcModel(model) || !keepsOurPose(model, entity)) return;
        State s = STATES.computeIfAbsent(entity, e -> new State());
        if (s.race == null && s.raceRetry-- <= 0) { s.race = race(entity); s.raceRetry = 20; }
        String race = s.race == null ? "man" : s.race;

        // ---- triggers ----
        boolean swinging = entity.swinging;
        if (swinging && !s.wasSwinging && entity.deathTime == 0) {
            String kind = entity.getMainHandItem().isEmpty() ? "unarmed" : "sword";
            int n = NpcClip.attackCount(race, kind);
            if (n > 0) start(s, "attack_" + kind + "_" + (1 + (s.attacks++ % n)), ageInTicks);
        }
        s.wasSwinging = swinging;
        if (entity.hurtTime > s.lastHurtTime && !isAttack(s.slot) && entity.deathTime == 0) start(s, "hurt", ageInTicks);
        s.lastHurtTime = entity.hurtTime;

        // ---- pick the clip and its time ----
        String slot;
        float t;
        if (entity.deathTime > 0) {
            if (!"death".equals(s.slot)) start(s, "death", ageInTicks - (entity.deathTime - 1));
            NpcClip c = NpcClip.get(race, "death");
            // the Middle-earth NPC is removed 20 ticks after dying: squeeze the clip into that time
            float speed = c == null ? 1f : Math.max(1f, c.length * 20f / DEATH_TICKS);
            slot = "death";
            t = (ageInTicks - s.slotStart) / 20f * speed;
        } else if (s.slot != null && (isAttack(s.slot) || s.slot.equals("hurt")) && !finished(race, s, ageInTicks)) {
            slot = s.slot;
            t = (ageInTicks - s.slotStart) / 20f;
        } else {
            String loco = limbSwingAmount < WALK_SPEED ? "idle" : limbSwingAmount < RUN_SPEED ? "walk" : "run";
            if (!loco.equals(s.slot)) start(s, loco, ageInTicks);
            slot = loco;
            NpcClip c = NpcClip.get(race, loco);
            if (c == null) return;
            t = loco.equals("idle") ? ageInTicks / 20f : limbSwing / (loco.equals("walk") ? WALK_CYCLE : RUN_CYCLE) * c.length;
        }
        NpcClip clip = NpcClip.get(race, slot);
        if (clip == null) return;

        // ---- sample, blend from the pose shown when the clip changed, write the parts ----
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        float w = Mth.clamp((ageInTicks - s.blendStart) / BLEND_TICKS, 0f, 1f);
        Quaternionf q = new Quaternionf();
        Vector3f p = new Vector3f();
        for (int i = 0; i < 6; i++) {
            clip.sample(i, t, q, p);
            if (w < 1f) {
                s.fromRot[i].slerp(q, w, q);
                s.fromPos[i].lerp(p, w, p);
            }
            if (i == 0) {
                float look = slot.equals("death") ? 0f : isAttack(slot) ? 0.5f : 1f;
                q.mul(new Quaternionf().rotationZYX(0f, netHeadYaw * Mth.DEG_TO_RAD * look, headPitch * Mth.DEG_TO_RAD * look));
            }
            s.lastRot[i].set(q);
            s.lastPos[i].set(p);
            set(parts[i], q, p);
        }
        model.hat.copyFrom(model.head);
    }

    private static boolean keepsOurPose(HumanoidModel<?> model, LivingEntity entity) {
        if (entity.isPassenger() || entity.isSleeping() || entity.isSwimming() || entity.isFallFlying() || model.crouching) return false;
        return simple(model.rightArmPose) && simple(model.leftArmPose);
    }

    private static boolean simple(HumanoidModel.ArmPose pose) {
        return pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM;
    }

    private static boolean isAttack(String slot) {
        return slot != null && slot.startsWith("attack_");
    }

    private static boolean finished(String race, State s, float age) {
        NpcClip c = NpcClip.get(race, s.slot);
        return c == null || (age - s.slotStart) / 20f >= c.length;
    }

    private static void start(State s, String slot, float age) {
        if (slot.equals(s.slot) && !isAttack(slot) && !slot.equals("hurt")) return;
        for (int i = 0; i < 6; i++) { s.fromRot[i].set(s.lastRot[i]); s.fromPos[i].set(s.lastPos[i]); }
        s.blendStart = s.slot == null ? -1e9f : age;   // first clip: no blend from the zero pose
        s.slot = slot;
        s.slotStart = age;
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
        if (key == null) return null;
        String k = key.toLowerCase(Locale.ROOT);
        if (k.matches(".*(orc|uruk|goblin|snaga|gundabad|mordor|isengard|moria).*")) return "orc";
        if (k.contains("dwar") || k.contains("erebor") || k.contains("durin")) return "dwarf";
        if (k.contains("hobbit") || k.contains("shire")) return "hobbit";
        if (k.contains("elf") || k.contains("elv") || k.contains("lorien") || k.contains("woodland") || k.contains("rivendell")) return "elf";
        return "man";
    }
}
