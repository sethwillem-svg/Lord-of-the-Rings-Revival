package io.github.sethwillemsvg.lotrrevival.client.anim;

import com.arcanc.pulselib.content.animatable.AnimManagerKey;
import com.arcanc.pulselib.content.animatable.PAnimatable;
import com.arcanc.pulselib.content.animatable.PAnimationController;
import com.arcanc.pulselib.content.animatable.PAnimationManager;
import com.arcanc.pulselib.content.model.baked.PBakedModel;
import com.mojang.logging.LogUtils;
import io.github.sethwillemsvg.lotrrevival.combat.AttackClips;
import io.github.sethwillemsvg.lotrrevival.combat.WeaponClass;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 * One NPC's PulseLib animation state: its race's model (PulseClientPlugin.MODELS), a manager with the NpcGraphs graph, and
 * the parameters set from the entity every client tick (tick). The server's attacks arrive through attack(); hurt,
 * ranged releases and unscripted swings are triggered here. Middle-earth's entity class is not touched: animatables live in a
 * weak map keyed by the entity.
 */
public final class NpcAnimatable implements PAnimatable<NpcAnimatable> {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<LivingEntity, NpcAnimatable> ALL = new WeakHashMap<>();
    private static final String GRAPH = "npc";
    private static final String[] IDLES = {"idle", "idle", "idle_2", "idle_3", "idle_4", "idle"};
    private static boolean logged;

    final String race;
    private final String idle;
    private final PAnimationManager<NpcAnimatable> manager;
    @Nullable private PBakedModel bound;
    private String state = "free";
    private int lastHurt, attacks;
    private boolean wasSwinging;
    private long serverAttackAt = -1000;
    /** The pose last applied to the model this frame (NpcAnimator.apply), read by the renderer for held items. */
    @Nullable NpcPose lastPose;
    private final Map<String, Float> pendingSpeeds = new HashMap<>();

    private NpcAnimatable(LivingEntity entity) {
        this.race = NpcAnimator.raceOf(entity);
        this.idle = IDLES[Math.floorMod(entity.getId() * 0x9E3779B1, IDLES.length)];
        this.manager = new PAnimationManager<>(this, AnimManagerKey.ofObject(this));
    }

    /** This NPC's animatable, created on first use. */
    public static NpcAnimatable of(LivingEntity entity) {
        return ALL.computeIfAbsent(entity, NpcAnimatable::new);
    }

    @Nullable
    public static NpcAnimatable get(LivingEntity entity) {
        return ALL.get(entity);
    }

    /** The race's baked model, fetched every time (a resource reload replaces it); null until loaded. */
    @Nullable
    PBakedModel model() {
        var data = PulseClientPlugin.MODELS.getOrDefault(race, PulseClientPlugin.MODELS.get("man"));
        return data == null ? null : data.getModel();
    }

    @Override
    public PAnimationManager<NpcAnimatable> getAnimationManager(AnimManagerKey key) {
        return manager;
    }

    @Override
    public void registerAnimationControllers(PAnimationManager.PAnimationRegistrar<NpcAnimatable> registrar) {
        registrar.addGraph(GRAPH, () -> {
            PBakedModel model = model();
            Set<String> overlays = model == null ? Set.of()
                    : model.animations().keySet().stream().filter(NpcGraphs::isOverlay).collect(Collectors.toSet());
            return NpcGraphs.npc(model != null && model.animations().containsKey(idle) ? idle : "idle", overlays);
        });
    }

    @Nullable
    PAnimationController<NpcAnimatable> controller() {
        return manager.getControllers().get(GRAPH);
    }

    Iterable<PAnimationController<NpcAnimatable>> controllers() {
        return manager.getControllers().values();
    }

    /** Called every client tick for NPCs near the camera: parameters from the entity, triggers, then PulseLib's tick. */
    public void tick(LivingEntity entity) {
        PBakedModel model = model();
        if (model == null) return;
        if (model != bound) {
            manager.bindModel(model);
            bound = model;
            if (!logged) {
                logged = true;
                LOG.info("lotr_revival: NPC rig loaded for {}, {} animations each", PulseClientPlugin.RACES, model.animations().size());
            }
        }
        manager.initializeControllers();
        PAnimationController<NpcAnimatable> c = controller();
        if (c == null) return;
        Entity mover = entity.isPassenger() && entity.getVehicle() != null ? entity.getVehicle() : entity;
        float speed = (float) Math.hypot(mover.getX() - mover.xo, mover.getZ() - mover.zo);
        String next = baseState(entity, speed);
        if (!next.equals(state) && entity.deathTime == 0) {
            if (state.equals("bow_aim")) trigger(c, "bow_release", 1f);
            else if (state.equals("crossbow_aim")) trigger(c, "crossbow_shoot", 1f);
        }
        state = next;
        c.setParameter(NpcGraphs.SPEED, speed);
        for (String s : NpcGraphs.STATES) c.setParameter(s, s.equals(state));
        if (entity.hurtTime > lastHurt && entity.deathTime == 0) trigger(c, "hurt", 1f);
        lastHurt = entity.hurtTime;
        // a swing the server did not time (should not happen with the add-on on the server): the weapon class's clips
        if (entity.swinging && !wasSwinging && entity.tickCount - serverAttackAt > 10 && entity.deathTime == 0) {
            ItemStack hand = entity.getMainHandItem();
            List<AttackClips.Clip> clips = AttackClips.of(race, hand.isEmpty() ? null : Objects.requireNonNullElse(WeaponClass.of(hand), "sword"));
            if (!clips.isEmpty()) trigger(c, handed(entity, clips.get(attacks++ % clips.size()).slot()), 1f);
        }
        wasSwinging = entity.swinging;
        try {
            manager.tick();
        } catch (RuntimeException e) {
            warnOnce(state, e);
        }
        applyPendingSpeeds(c);
    }

    /** The base state now: free, riding, a stance, bow_aim, crossbow_aim or crossbow_reload (NpcGraphs.STATES). */
    public String state() {
        return state;
    }

    /** The pose applied to this NPC's model this frame, or null. */
    @Nullable
    public NpcPose lastPose() {
        return lastPose;
    }

    /** The server started an attack (combat.AttackTiming): play it at speed. */
    public void attack(LivingEntity entity, String animation, float speed) {
        PAnimationController<NpcAnimatable> c = controller();
        if (c == null) return;
        serverAttackAt = entity.tickCount;
        PBakedModel model = bound;
        if (model != null && !model.animations().containsKey(animation)) {
            // not in this race's file (a server with other clips): the class's first attack
            String first = animation.replaceFirst("_\\d+$", "_1");
            animation = model.animations().containsKey(first) ? first : "attack_sword_1";
        }
        trigger(c, handed(entity, animation), speed);
    }

    /**
     * Starts an overlay. PulseLib only accepts a speed for an overlay that is playing (setOverlaySpeed throws otherwise), and
     * an overlay starts playing on the tick after its trigger: a running one is restarted and sped now, a new one is
     * triggered and gets its speed after the next tick (applyPendingSpeeds). Never lets an animation fault reach the game.
     */
    private void trigger(PAnimationController<NpcAnimatable> c, String animation, float speed) {
        PBakedModel model = bound;
        if (model == null || !model.animations().containsKey(animation)) return;
        try {
            var runtime = c.graphRuntime();
            if (runtime != null && runtime.restartOverlay(animation)) {
                runtime.setOverlaySpeed(animation, speed);
                return;
            }
            c.trigger(animation);
            if (speed != 1f) pendingSpeeds.put(animation, speed);
        } catch (RuntimeException e) {
            warnOnce(animation, e);
        }
    }

    private void applyPendingSpeeds(PAnimationController<NpcAnimatable> c) {
        if (pendingSpeeds.isEmpty()) return;
        var runtime = c.graphRuntime();
        pendingSpeeds.forEach((animation, speed) -> {
            try {
                if (runtime != null) runtime.setOverlaySpeed(animation, speed);
            } catch (RuntimeException e) {
                warnOnce(animation, e);
            }
        });
        pendingSpeeds.clear();
    }

    private static boolean warned;

    private static void warnOnce(String animation, RuntimeException e) {
        if (warned) return;
        warned = true;
        LOG.warn("lotr_revival: could not play NPC animation {} (further errors are not logged)", animation, e);
    }

    /** The attack as this NPC plays it: the mirrored twin (<name>_l) when it holds its weapon in the left hand. */
    private String handed(LivingEntity entity, String animation) {
        PBakedModel model = bound;
        String left = animation + "_l";
        return entity.getMainArm() == HumanoidArm.LEFT && model != null && model.animations().containsKey(left) ? left : animation;
    }

    /**
     * The base state. Riding comes first (aiming clips have no legs, so a mounted archer keeps sitting); aiming only while
     * standing (a strafing archer keeps stepping) and, for a loaded crossbow, only while the NPC is fighting; then a formation
     * stance (still, or marching); else moving freely.
     */
    private static String baseState(LivingEntity entity, float speed) {
        if (entity.isPassenger()) return "riding";
        if (speed < NpcGraphs.WALK) {
            ItemStack use = entity.getUseItem();
            String cls = entity.isUsingItem() ? WeaponClass.of(use) : null;
            if ("bow".equals(cls) || "longbow".equals(cls)) return "bow_aim";
            if ("crossbow".equals(cls)) return "crossbow_reload";
            ItemStack main = entity.getMainHandItem();
            if (main.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(main) && entity instanceof Mob mob && mob.isAggressive())
                return "crossbow_aim";
        }
        String stance = NpcAnimator.stanceOf.apply(entity);
        if (stance != null && !stance.isEmpty()) {
            if (stance.equals("march")) return "march";
            if (speed < NpcGraphs.WALK) return stance;
        }
        return "free";
    }
}
