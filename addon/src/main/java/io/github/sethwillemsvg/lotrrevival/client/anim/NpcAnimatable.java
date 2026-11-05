package io.github.sethwillemsvg.lotrrevival.client.anim;

import com.arcanc.pulselib.content.animatable.AnimManagerKey;
import com.arcanc.pulselib.content.animatable.PAnimatable;
import com.arcanc.pulselib.content.animatable.PAnimationController;
import com.arcanc.pulselib.content.animatable.PAnimationManager;
import com.arcanc.pulselib.content.model.baked.PBakedModel;
import com.mojang.logging.LogUtils;
import io.github.sethwillemsvg.lotrrevival.combat.AttackClips;
import io.github.sethwillemsvg.lotrrevival.combat.Hands;
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
import java.util.function.Predicate;
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
    private static final String[] IDLES = {"idle", "idle", "idle_2", "idle_3", "idle"};
    private static final String SHIELD = "_shield";
    /** Overlay speed during a hitstop: PulseLib holds a speed-0 overlay's pose and never ends it, so every freeze is undone here. */
    private static final float FREEZE = 0f;
    /** How long after attack() a hitstop may still freeze its swing (ticks): the server's hit lands within 12 ticks of it. */
    private static final int HITSTOP_WINDOW = 20;
    private static boolean logged;

    final String race;
    private final String idle;
    /** Holds a shield in the off hand (when its animations were set up): plays the clips' shield versions. */
    private final boolean shield;
    /** Its weapon's stance group (stanceGroup: spear or bow, or null), when its animations were set up. */
    @Nullable private final String group;
    private final PAnimationManager<NpcAnimatable> manager;
    @Nullable private PBakedModel bound;
    private String state = "free";
    private int lastHurt, attacks;
    private boolean wasSwinging;
    private long serverAttackAt = -1000;
    /** The pose last applied to the model this frame (NpcAnimator.apply), read by the renderer for held items. */
    @Nullable NpcPose lastPose;
    /** The bow model shown this frame (BowDraw stage, from the bow clips' draw; NpcAnimator.apply), read by the renderer. */
    int bowStage;
    /** The tick bow_aim was entered and the tick bow_release was triggered (-1: not since), for the bow clips' time. */
    private int aimTick = -1, releaseTick = -1;
    private final Map<String, Float> pendingSpeeds = new HashMap<>();
    /** The overlay the last attack() played (its handed name) and its speed: what a hitstop freezes and restores. */
    @Nullable private String lastAttack;
    private float lastSpeed = 1f;
    /** Ticks left on the swing's freeze (hitstop) and the hurt's (hurtStop); a hurt freeze that came before its hurt waits
     *  hurtWait ticks as hurtQueued; sinceHurt counts ticks since "hurt" was triggered. */
    private int freeze, hurtFreeze, hurtQueued, hurtWait, sinceHurt = 100;

    private NpcAnimatable(LivingEntity entity) {
        this.race = NpcAnimator.raceOf(entity);
        this.idle = IDLES[Math.floorMod(entity.getId() * 0x9E3779B1, IDLES.length)];
        this.shield = "shield".equals(WeaponClass.of(Hands.other(entity)));
        this.group = stanceGroup(WeaponClass.of(Hands.weapon(entity)));
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
            String base = model != null && model.animations().containsKey(idle) ? idle : "idle";
            Predicate<String> has = name -> model != null && model.animations().containsKey(name);
            return NpcGraphs.npc(base, name -> version(name, group, shield, has), overlays,
                    name -> {
                        AttackClips.Clip c = AttackClips.clip(race, name);
                        return c == null ? new float[] {NpcGraphs.FADE_IN, NpcGraphs.FADE_OUT} : new float[] {c.fadeIn(), c.fadeOut()};
                    });
        });
    }

    /** The formation stances that have weapon-group variants (specs/addon/npc-formation-stances.md). */
    private static final Set<String> STANCES = Set.of("guard", "march", "shield_brace", "spear_brace");

    /** A weapon class's stance group: spear for the spear class (spears and glaives), bow for bows, longbows and crossbows;
     *  null for everything else (blades, axes, daggers, fists), which play the plain stances. */
    @Nullable
    public static String stanceGroup(@Nullable String weaponClass) {
        if ("spear".equals(weaponClass)) return "spear";
        if ("bow".equals(weaponClass) || "longbow".equals(weaponClass) || "crossbow".equals(weaponClass)) return "bow";
        return null;
    }

    /**
     * The animation an NPC plays for a stance or movement clip (specs/addon/npc-formation-stances.md): a stance's weapon-group
     * variant (stance_spear, stance_bow) where the race file has it, then its shield version (_shield) where the NPC holds
     * a shield and the file has that; else the plain clip, so an older race file without variants plays as before. A spear
     * line with a shield braces behind the shield (shield_brace_spear): a spear cannot be braced two-handed with a shield on
     * the arm.
     */
    public static String version(String name, @Nullable String group, boolean shield, Predicate<String> has) {
        if (shield && "spear".equals(group) && name.equals("spear_brace") && has.test("shield_brace_spear")) return "shield_brace_spear";
        String base = group != null && STANCES.contains(name) && has.test(name + "_" + group) ? name + "_" + group : name;
        return shield && has.test(base + SHIELD) ? base + SHIELD : base;
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
        if (entity.deathTime > 0) thaw(c);
        Entity mover = entity.isPassenger() && entity.getVehicle() != null ? entity.getVehicle() : entity;
        float speed = (float) Math.hypot(mover.getX() - mover.xo, mover.getZ() - mover.zo);
        String next = baseState(entity, speed);
        if (!next.equals(state) && entity.deathTime == 0) {
            if (state.equals("bow_aim")) {
                trigger(c, "bow_release", 1f);
                releaseTick = entity.tickCount;
            } else if (state.equals("crossbow_aim")) trigger(c, "crossbow_shoot", 1f);
        }
        if (next.equals("bow_aim") && !state.equals("bow_aim")) {
            aimTick = entity.tickCount;
            releaseTick = -1;
        }
        state = next;
        c.setParameter(NpcGraphs.SPEED, speed);
        for (String s : NpcGraphs.STATES) c.setParameter(s, s.equals(state));
        if (entity.hurtTime > lastHurt && entity.deathTime == 0) {
            trigger(c, "hurt", 1f);
            sinceHurt = 0;
        }
        lastHurt = entity.hurtTime;
        // a swing the server did not time (should not happen with the add-on on the server): the weapon class's clips
        if (entity.swinging && !wasSwinging && entity.tickCount - serverAttackAt > 10 && entity.deathTime == 0) {
            ItemStack hand = Hands.weapon(entity);
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
        // freezes count down after PulseLib's tick, so a freeze of n ticks holds the pose for n of them
        if (freeze > 0 && --freeze == 0) setSpeed(c, lastAttack, lastSpeed);
        if (hurtFreeze > 0 && --hurtFreeze == 0) setSpeed(c, "hurt", 1f);
        if (hurtQueued > 0 && sinceHurt == 0) hurtStop(hurtQueued);   // the hit came first; its hurt has just started
        else if (hurtWait > 0 && --hurtWait == 0) hurtQueued = 0;
        if (sinceHurt < 100) sinceHurt++;
    }

    /** The base state now: free, riding, a stance, bow_aim, crossbow_aim or crossbow_reload (NpcGraphs.STATES). */
    public String state() {
        return state;
    }

    /**
     * The bow model to show now (BowDraw stage) from the bow clips' own time, not the entity's using-item state: while in
     * bow_aim, its draw since the state was entered; after a release, bow_release's (an overlay plays from the tick after
     * its trigger: until then the full draw holds). Slack otherwise, and without the race's draw timing.
     */
    int bowStage(LivingEntity entity, float partialTick) {
        BowDraw.Timing d = BowDraw.of(race);
        if (d == null) return BowDraw.SLACK;
        if (state.equals("bow_aim") && aimTick >= 0)
            return BowDraw.stageOf(BowDraw.pullAt(d, "bow_aim", (entity.tickCount - aimTick + partialTick) / 20f));
        if (releaseTick >= 0) {
            float t = (entity.tickCount - releaseTick - 1 + partialTick) / 20f;
            return t < 0 ? BowDraw.PULLING_2 : BowDraw.stageOf(BowDraw.pullAt(d, "bow_release", t));
        }
        return BowDraw.SLACK;
    }

    /** The bow model shown this frame (BowDraw stage). */
    public int bowStage() {
        return bowStage;
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
        String handed = handed(entity, animation);
        // a new swing drops the freeze: the same overlay gets its own speed from trigger, another one is let go at its speed
        if (freeze > 0 && !handed.equals(lastAttack)) setSpeed(c, lastAttack, lastSpeed);
        freeze = 0;
        lastAttack = handed;
        lastSpeed = speed;
        trigger(c, handed, speed);
    }

    /** This NPC's hit landed (combat.Hitstop): the swing last started by attack() holds still for ticks. Ignored when no
     *  attack() came in the last HITSTOP_WINDOW ticks (a player who started tracking the NPC after its attack payload). */
    public void hitstop(LivingEntity entity, int ticks) {
        PAnimationController<NpcAnimatable> c = controller();
        if (c == null || lastAttack == null || ticks <= 0 || entity.tickCount - serverAttackAt > HITSTOP_WINDOW) return;
        freeze = setSpeed(c, lastAttack, FREEZE) ? ticks : 0;
    }

    /** This NPC took a held hit: its hurt flinch holds still for ticks, now if it began in the last 2 ticks, else when it
     *  begins within the next 2 (the hit and the hurt reach the client in either order). */
    public void hurtStop(int ticks) {
        PAnimationController<NpcAnimatable> c = controller();
        PBakedModel model = bound;
        if (c == null || ticks <= 0 || model == null || !model.animations().containsKey("hurt")) return;
        hurtQueued = hurtWait = 0;
        if (sinceHurt <= 2) {
            hurtFreeze = setSpeed(c, "hurt", FREEZE) ? ticks : 0;
        } else {
            hurtQueued = ticks;
            hurtWait = 2;
        }
    }

    /** Ends both freezes (on death), giving each frozen overlay its speed back. */
    private void thaw(PAnimationController<NpcAnimatable> c) {
        if (freeze > 0) setSpeed(c, lastAttack, lastSpeed);
        if (hurtFreeze > 0) setSpeed(c, "hurt", 1f);
        freeze = hurtFreeze = hurtQueued = hurtWait = 0;
    }

    /** Sets a playing overlay's speed; false when PulseLib refuses (the overlay has ended), which drops the freeze. */
    private boolean setSpeed(PAnimationController<NpcAnimatable> c, String animation, float speed) {
        try {
            var runtime = c.graphRuntime();
            if (runtime == null) return false;
            runtime.setOverlaySpeed(animation, speed);
            return true;
        } catch (IllegalStateException e) {
            return false;   // expected: PulseLib's "overlay not active" for one that has already ended
        } catch (RuntimeException e) {
            warnOnce(animation, e);
            return false;
        }
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

    /**
     * The attack as this NPC plays it: its shield version (<name>_shield, the off arm keeping the shield up) when it holds a
     * shield, else the mirrored twin (<name>_l) when it holds its weapon in the left hand.
     */
    private String handed(LivingEntity entity, String animation) {
        PBakedModel model = bound;
        if (model == null) return animation;
        if ("shield".equals(WeaponClass.of(Hands.other(entity))) && model.animations().containsKey(animation + SHIELD)) return animation + SHIELD;
        String left = animation + "_l";
        return entity.getMainArm() == HumanoidArm.LEFT && model.animations().containsKey(left) ? left : animation;
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
