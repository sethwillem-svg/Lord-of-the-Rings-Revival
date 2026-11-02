package io.github.sethwillemsvg.lotrrevival.client.anim;

import com.arcanc.pulselib.content.model.animation.PAnimationGraph;
import com.arcanc.pulselib.content.model.animation.PAnimationState;
import com.arcanc.pulselib.content.model.animation.PAnimationTransition;
import com.arcanc.pulselib.content.model.animation.PAnimationType;
import com.arcanc.pulselib.content.model.animation.PCondition;
import com.arcanc.pulselib.content.model.animation.PInterpolationType;
import com.arcanc.pulselib.content.model.animation.PInterruptionPolicy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The NPC's animation graph: one base state at a time (moving, riding, a formation stance or aiming), chosen by exactly one
 * true boolean parameter (STATES), crossfading over BLEND ticks; attacks, hurt and ranged releases play as one-shot
 * overlays on top (their trigger is the animation name), so the legs keep walking under a swing.
 */
public final class NpcGraphs {
    private NpcGraphs() {}

    /** Base states, in graph order; each is entered when its parameter is the true one. */
    public static final List<String> STATES = List.of("free", "riding", "guard", "shield_brace", "spear_brace", "march",
            "bow_aim", "crossbow_aim", "crossbow_reload");
    /** speed parameter: horizontal blocks per tick. */
    public static final String SPEED = "speed";
    static final float WALK = 0.04f, RUN = 0.18f, RIDE_MOVE = 0.08f;
    private static final float BLEND = 3f, FADE_IN = 2f, FADE_OUT = 3f;

    /** @param idle this NPC's idle (idle, idle_2 ...)   @param overlays the one-shot animations the race's file has */
    public static PAnimationGraph npc(String idle, Collection<String> overlays) {
        List<PAnimationState> states = new ArrayList<>();
        states.add(blend("free", new float[] {0f, WALK, RUN}, idle, "walk", "run"));
        states.add(blend("riding", new float[] {0f, RIDE_MOVE}, "ride_idle", "ride_move"));
        states.add(clip("guard", PAnimationType.CYCLE));
        states.add(clip("shield_brace", PAnimationType.CYCLE));
        states.add(clip("spear_brace", PAnimationType.CYCLE));
        states.add(blend("march", new float[] {0f, WALK}, idle, "march"));
        states.add(clip("bow_aim", PAnimationType.HOLD_LAST_FRAME));
        states.add(clip("crossbow_aim", PAnimationType.CYCLE));
        states.add(clip("crossbow_reload", PAnimationType.HOLD_LAST_FRAME));
        List<PAnimationTransition> transitions = new ArrayList<>();
        for (int from = 0; from < STATES.size(); from++)
            for (int to = 0; to < STATES.size(); to++)
                if (from != to) transitions.add(new PAnimationTransition(from, to, PCondition.parameter(STATES.get(to)), 0f, BLEND, 0,
                        PInterruptionPolicy.FROM_CURRENT));
        for (String name : overlays)
            states.add(new PAnimationState.OneShotOverlay(name, name, name, FADE_IN, FADE_OUT, PInterpolationType.LINEAR, 1f, false));
        return new PAnimationGraph(states, transitions);
    }

    /** Animations that play as overlays: attacks, hurt and the ranged releases. */
    public static boolean isOverlay(String animation) {
        return animation.startsWith("attack_") || animation.equals("hurt") || animation.equals("bow_release") || animation.equals("crossbow_shoot");
    }

    private static PAnimationState blend(String name, float[] at, String... animations) {
        List<PAnimationState.BlendSpace1D.Point> points = new ArrayList<>();
        for (int i = 0; i < at.length; i++) points.add(new PAnimationState.BlendSpace1D.Point(at[i], animations[i]));
        return new PAnimationState.BlendSpace1D(name, SPEED, points, PAnimationType.CYCLE, PInterpolationType.LINEAR, 1f, true);
    }

    private static PAnimationState clip(String name, PAnimationType type) {
        return new PAnimationState.Clip(name, name, type, PInterpolationType.LINEAR, 1f, false);
    }
}
