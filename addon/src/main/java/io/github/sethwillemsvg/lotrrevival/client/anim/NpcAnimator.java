package io.github.sethwillemsvg.lotrrevival.client.anim;

import com.mojang.logging.LogUtils;
import io.github.sethwillemsvg.lotrrevival.combat.AttackClips;
import io.github.sethwillemsvg.lotrrevival.combat.NpcRaces;
import io.github.sethwillemsvg.lotrrevival.combat.WeaponClass;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.Function;

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
    private static final String OUR_MODEL = "io.github.sethwillemsvg.lotrrevival.client.render.NpcBipedModel";
    private static final String NPC_ENTITY = "net.sevenstars.middleearth.entity.npcs.NpcEntity";

    private static final float WALK_SPEED = 0.1f, RUN_SPEED = 0.7f;   // limbSwingAmount thresholds
    private static final float WALK_CYCLE = 9.43f, RUN_CYCLE = 12f;  // limbSwing per gait cycle (vanilla legs: 2pi / 0.6662)
    private static final float BLEND_TICKS = 4f, DEATH_TICKS = 19f;

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();
    private static final Map<Class<?>, Boolean> NPC_CLASSES = new WeakHashMap<>();

    private NpcAnimator() {}

    /** Stance of a unit in formation: "" (none), guard, shield_brace, spear_brace or march (specs/addon/hiring-reputation.md).
     *  The squad code supplies it per unit; until then /lotrrender stance sets testStance for every NPC. */
    public static Function<LivingEntity, String> stanceOf = e -> NpcAnimator.testStance;
    public static String testStance = "";
    /** While true (the viewer export), NPC models are left in Minecraft's rest pose: every part unrotated. */
    public static boolean restPose;

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
        float slotSpeed = 1f;    // playback speed of the slot (attacks timed by the server)
        String serverSlot;      // attack the server asked for, started on the next frame
        float serverSpeed = 1f, serverAt = -1e9f;
        final float[] fromJoints = new float[NpcClip.JOINTS.length];
        final float[] lastJoints = new float[NpcClip.JOINTS.length];
        State() {
            for (int i = 0; i < 6; i++) {
                fromRot[i] = new Quaternionf(); fromPos[i] = new Vector3f();
                lastRot[i] = new Quaternionf(); lastPos[i] = new Vector3f();
            }
        }
    }

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
        if (restPose) {
            for (ModelPart p : new ModelPart[] {model.head, model.hat, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg})
                p.setRotation(0f, 0f, 0f);
            return;
        }
        if (!keepsOurPose(model, entity)) {   // Minecraft's pose: straight limbs
            State s0 = STATES.get(entity);
            if (s0 != null) Arrays.fill(s0.lastJoints, 0f);
            return;
        }
        State s = STATES.computeIfAbsent(entity, e -> new State());
        if (s.race == null && s.raceRetry-- <= 0) { s.race = race(entity); s.raceRetry = 20; }
        String race = s.race == null ? "man" : s.race;

        // ---- triggers ----
        boolean swinging = entity.swinging;
        if (s.serverSlot != null && entity.deathTime == 0) {   // the server's attack (combat.AttackTiming): its clip and speed
            if (NpcClip.get(race, s.serverSlot) != null) {
                start(s, s.serverSlot, ageInTicks);
                s.slotSpeed = s.serverSpeed;
                s.serverAt = ageInTicks;
            }
            s.serverSlot = null;
        } else if (swinging && !s.wasSwinging && entity.deathTime == 0 && ageInTicks - s.serverAt > 10f) {
            // a swing the server did not time (should not happen with the add-on on the server): the weapon class's clips
            ItemStack hand = entity.getMainHandItem();
            var clips = AttackClips.of(race, hand.isEmpty() ? null : Objects.requireNonNullElse(WeaponClass.of(hand), "sword"));
            if (!clips.isEmpty()) start(s, clips.get(s.attacks++ % clips.size()).slot(), ageInTicks);
        }
        // drawing a bow or aiming / loading a crossbow plays the ranged clips; letting go plays the release
        String ranged = rangedSlot(model);
        if (ranged == null && s.slot != null && (s.slot.equals("bow_aim") || s.slot.equals("crossbow_aim")) && entity.deathTime == 0) {
            String release = s.slot.equals("bow_aim") ? "bow_release" : "crossbow_shoot";
            if (NpcClip.get(race, release) != null) start(s, release, ageInTicks);
        }
        s.wasSwinging = swinging;
        if (entity.hurtTime > s.lastHurtTime && !isAttack(s.slot) && rangedSlot(model) == null && entity.deathTime == 0) start(s, "hurt", ageInTicks);
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
        } else if (s.slot != null && (isAttack(s.slot) || isOneShot(s.slot)) && !finished(race, s, ageInTicks)) {
            slot = s.slot;
            t = (ageInTicks - s.slotStart) / 20f * s.slotSpeed;
        } else if (ranged != null && NpcClip.get(race, ranged) != null) {
            if (!ranged.equals(s.slot)) start(s, ranged, ageInTicks);
            slot = ranged;
            NpcClip c = NpcClip.get(race, ranged);
            t = c.loop ? ageInTicks / 20f : (ageInTicks - s.slotStart) / 20f;
        } else {
            String loco = limbSwingAmount < WALK_SPEED ? "idle" : limbSwingAmount < RUN_SPEED ? "walk" : "run";
            String stance = stanceSlot(race, loco, stanceOf.apply(entity));
            if (stance != null) loco = stance;
            else loco = variant(race, loco, entity);
            if (!loco.equals(s.slot)) start(s, loco, ageInTicks);
            slot = loco;
            NpcClip c = NpcClip.get(race, loco);
            if (c == null) return;
            boolean cyclic = loco.startsWith("walk") || loco.startsWith("run") || loco.equals("march");
            t = !cyclic ? ageInTicks / 20f : limbSwing / (loco.startsWith("run") ? RUN_CYCLE : WALK_CYCLE) * c.length / c.cycles;
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
        clip.sampleJoints(t, s.lastJoints);
        if (w < 1f) for (int i = 0; i < s.lastJoints.length; i++) s.lastJoints[i] = Mth.lerp(w, s.fromJoints[i], s.lastJoints[i]);
        model.hat.copyFrom(model.head);
    }

    /** The server started an attack (combat.AttackTiming): play slot at speed from the next frame. */
    public static void startAttack(LivingEntity entity, String slot, float speed) {
        State s = STATES.computeIfAbsent(entity, e -> new State());
        s.serverSlot = slot;
        s.serverSpeed = speed;
    }

    /** The clip slot this NPC is playing (idle, walk, attack_sword_1, spear_brace, ...), or null. */
    public static String slot(LivingEntity entity) {
        State s = STATES.get(entity);
        return s == null ? null : s.slot;
    }

    /** The joint angles of the pose last applied to this NPC (NpcClip.JOINTS order), or null if it has none. Read by the
     *  PulseLib renderer, which bends the limbs and waist with them. */
    public static float[] joints(LivingEntity entity) {
        State s = STATES.get(entity);
        return s == null ? null : s.lastJoints;
    }

    /**
     * Each NPC keeps one of its race's variants of a locomotion clip (walk, walk_2; idle twice as often as idle_2), chosen
     * from its id, so a crowd does not move in lockstep.
     */
    private static String variant(String race, String loco, LivingEntity entity) {
        int h = Math.floorMod(entity.getId() * 0x9E3779B1, 1024);
        if (loco.equals("walk")) return h % 2 == 1 && NpcClip.get(race, "walk_2") != null ? "walk_2" : "walk";
        if (loco.equals("idle")) return h % 3 == 2 && NpcClip.get(race, "idle_2") != null ? "idle_2" : "idle";
        return loco;
    }

    /** The stance clip replacing a locomotion clip, or null to keep it: standing plays the stance, walking in column marches. */
    private static String stanceSlot(String race, String loco, String stance) {
        if (stance == null || stance.isEmpty()) return null;
        String slot = switch (loco) {
            case "idle" -> switch (stance) {
                case "guard", "shield_brace", "spear_brace" -> stance;
                default -> null;
            };
            case "walk" -> stance.equals("march") ? "march" : null;
            default -> null;
        };
        return slot != null && NpcClip.get(race, slot) != null ? slot : null;
    }

    private static boolean keepsOurPose(HumanoidModel<?> model, LivingEntity entity) {
        if (entity.isPassenger() || entity.isSleeping() || entity.isSwimming() || entity.isFallFlying() || model.crouching) return false;
        return simple(model.rightArmPose) && simple(model.leftArmPose) || rangedSlot(model) != null;
    }

    /** The ranged clip for the model's arm pose: drawing a bow, loading or aiming a crossbow; null otherwise. */
    private static String rangedSlot(HumanoidModel<?> model) {
        HumanoidModel.ArmPose r = model.rightArmPose, l = model.leftArmPose;
        if (r == HumanoidModel.ArmPose.BOW_AND_ARROW || l == HumanoidModel.ArmPose.BOW_AND_ARROW) return "bow_aim";
        if (r == HumanoidModel.ArmPose.CROSSBOW_CHARGE || l == HumanoidModel.ArmPose.CROSSBOW_CHARGE) return "crossbow_reload";
        if (r == HumanoidModel.ArmPose.CROSSBOW_HOLD || l == HumanoidModel.ArmPose.CROSSBOW_HOLD) return "crossbow_aim";
        return null;
    }

    private static boolean isOneShot(String slot) {
        return slot.equals("hurt") || slot.equals("bow_release") || slot.equals("crossbow_shoot");
    }

    private static boolean simple(HumanoidModel.ArmPose pose) {
        return pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM;
    }

    private static boolean isAttack(String slot) {
        return slot != null && slot.startsWith("attack_");
    }

    private static boolean finished(String race, State s, float age) {
        NpcClip c = NpcClip.get(race, s.slot);
        return c == null || (age - s.slotStart) / 20f * s.slotSpeed >= c.length;
    }

    private static void start(State s, String slot, float age) {
        if (slot.equals(s.slot) && !isAttack(slot) && !slot.equals("hurt")) return;
        for (int i = 0; i < 6; i++) { s.fromRot[i].set(s.lastRot[i]); s.fromPos[i].set(s.lastPos[i]); }
        System.arraycopy(s.lastJoints, 0, s.fromJoints, 0, s.lastJoints.length);
        s.blendStart = s.slot == null ? -1e9f : age;   // first clip: no blend from the zero pose
        s.slot = slot;
        s.slotStart = age;
        s.slotSpeed = 1f;
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
