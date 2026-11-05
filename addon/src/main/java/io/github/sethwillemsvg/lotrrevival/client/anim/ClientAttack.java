package io.github.sethwillemsvg.lotrrevival.client.anim;

import io.github.sethwillemsvg.lotrrevival.client.combat.AttackMarkers;
import io.github.sethwillemsvg.lotrrevival.combat.AttackClips;
import io.github.sethwillemsvg.lotrrevival.combat.NpcAttackEndPayload;
import io.github.sethwillemsvg.lotrrevival.combat.NpcAttackPayload;
import io.github.sethwillemsvg.lotrrevival.combat.ParryFxPayload;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client side of the combat payloads: the NPC plays the attack clip the server picked, time-warped over the wind-up (the
 * first 60% up to the hit frame stretched, the last 40% at normal speed: a slow draw-back, then the strike), and the
 * warning marker shows; the end payload clears the marker (and plays stagger when parried); the fx payload plays parries.
 */
public final class ClientAttack {
    private ClientAttack() {}

    /** Share of the clip's draw-back (up to its hit frame) that plays at normal speed, at the end. */
    static final float STRIKE_SHARE = 0.4f;

    /** The time warp of one swing: play at slowSpeed for slowTicks, then at 1. */
    public record Warp(float slowSpeed, int slowTicks) {}

    /** Clip hit frame hitTicks (clip time), stretched so it lands windupTicks after the start. */
    public static Warp warp(float hitTicks, int windupTicks) {
        if (hitTicks <= 0 || windupTicks <= 0) return new Warp(1f, 0);
        float strike = STRIKE_SHARE * hitTicks;
        float slowReal = windupTicks - strike;
        if (slowReal <= 1f) return new Warp(Math.max(0.05f, hitTicks / windupTicks), windupTicks);   // clip longer than the wind-up
        float speed = (hitTicks - strike) / slowReal;
        return new Warp(speed, Math.round(slowReal));
    }

    public static void handle(NpcAttackPayload p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        AttackMarkers.add(p, level.getGameTime());
        if (!p.hasClip() || !(level.getEntity(p.entityId()) instanceof LivingEntity e)) return;
        AttackClips.Clip clip = AttackClips.byName(NpcAnimator.raceOf(e), p.slot());
        float hitTicks = clip != null ? clip.hit() * 20f : 3f;
        Warp w = warp(hitTicks, p.windupTicks());
        LotrLog.dbg("combat", "client swing: npc={} clip={} windup={} hit={}t warp x{} for {}t heavy={}", p.entityId(), p.slot(),
                p.windupTicks(), hitTicks, String.format("%.2f", w.slowSpeed()), w.slowTicks(), p.heavy());
        NpcAnimator.startAttack(e, p.slot(), w.slowSpeed(), w.slowTicks());
    }

    public static void end(NpcAttackEndPayload p) {
        AttackMarkers.remove(p.entityId());
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (p.reasonValue() == NpcAttackEndPayload.Reason.PARRIED && level.getEntity(p.entityId()) instanceof LivingEntity e)
            NpcAnimator.play(e, "stagger");   // per race, with _shield and _l versions (NpcAnimatable.handed)
    }

    public static void fx(ParryFxPayload p) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(p.defenderId()) instanceof LivingEntity e)) return;
        switch (p.kindValue()) {
            case NPC_PARRY -> NpcAnimator.play(e, NpcAnimator.isShieldBearer(e) ? "parry_shield" : "parry_blade");
            // TODO(spec "Out of scope" / ticket 6): the player's short guard flash; the swing below stands in
            case PERFECT, GLANCE -> {
                if (e == Minecraft.getInstance().player) e.swing(InteractionHand.MAIN_HAND);
            }
        }
    }
}
