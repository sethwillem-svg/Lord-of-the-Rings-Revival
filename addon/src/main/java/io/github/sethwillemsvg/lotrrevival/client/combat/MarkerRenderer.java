package io.github.sethwillemsvg.lotrrevival.client.combat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Draws the attack warning markers as a GUI layer: each NPC's head point is projected to the screen with the camera's
 * rotation and field of view, so a marker keeps a fixed pixel size (14-24 px by distance) and needs no world-space render
 * state. Normal: white diamond with a ring closing on it. Heavy: red diamond with four spikes, thicker ring, gentle pulse
 * (shape differs, not only colour). The shapes are our textures (textures/combat/warn_*.png, drawn by
 * tools/sfx/make_combat_textures.py), scaled and tinted here. Swings at the local player draw at full size; others at 60% and half opacity.
 * Occlusion is one block ray per marker (config through_walls). Swings at you from outside the view go to OffscreenArrows.
 * TODO(spec ticket 4): FOV effects (sprint, zoom) and view bobbing in the projection.
 */
public final class MarkerRenderer {
    private MarkerRenderer() {}

    private record Shown(AttackMarkers.Marker m, Vec3 at, double dist, boolean mine) {}

    public static void draw(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!WarningConfig.enabled() || mc.level == null || mc.player == null || mc.options.hideGui || mc.player.isSpectator()) return;
        if (AttackMarkers.all().isEmpty()) return;
        float partial = delta.getGameTimeDeltaPartialTick(true);
        double time = mc.level.getGameTime() + partial;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        int range = WarningConfig.range();
        boolean others = WarningConfig.showOthers();

        List<Shown> shown = new ArrayList<>();
        for (AttackMarkers.Marker m : AttackMarkers.all()) {
            Entity e = mc.level.getEntity(m.entityId());
            if (!(e instanceof LivingEntity attacker) || !attacker.isAlive()) continue;
            boolean mine = m.targetId() == mc.player.getId();
            if (!mine && !others) continue;
            double above = 0.5 + (attacker.shouldShowName() ? 0.3 : 0.0);
            Vec3 at = attacker.getPosition(partial).add(0, attacker.getBbHeight() + above, 0);
            double dist = at.distanceTo(camPos);
            if (dist > range && !mine) continue;
            shown.add(new Shown(m, at, dist, mine));
        }
        shown.sort(Comparator.comparing((Shown s) -> !s.mine()).thenComparingDouble(Shown::dist));
        int max = WarningConfig.maxDrawn();
        if (shown.size() > max) {
            AttackMarkers.capFrames++;
            // swings at the local player always show, then the nearest others up to the cap
            List<Shown> kept = new ArrayList<>();
            for (Shown s : shown) if (s.mine() || kept.size() < max) kept.add(s);
            shown = kept;
        }
        AttackMarkers.maxShown = Math.max(AttackMarkers.maxShown, shown.size());

        Quaternionf toCamera = new Quaternionf(cam.rotation()).conjugate();
        int w = g.guiWidth(), h = g.guiHeight();
        float tanHalf = (float) Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        float aspect = (float) w / h;
        List<OffscreenArrows.Arrow> arrows = new ArrayList<>();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (Shown s : shown) {
            Vector3f v = new Vector3f((float) (s.at().x - camPos.x), (float) (s.at().y - camPos.y), (float) (s.at().z - camPos.z)).rotate(toCamera);
            float bearing = (float) Math.atan2(v.x, -v.z);   // 0 ahead, +pi/2 right, pi behind
            if (v.z > -0.05f) {
                if (s.mine()) arrows.add(new OffscreenArrows.Arrow(bearing, s.m().heavy()));
                continue;
            }
            float sx = w / 2f * (1f + (v.x / -v.z) / (tanHalf * aspect));
            float sy = h / 2f * (1f - (v.y / -v.z) / tanHalf);
            if (sx < 0 || sx > w || sy < 0 || sy > h) {
                if (s.mine()) arrows.add(new OffscreenArrows.Arrow(bearing, s.m().heavy()));
                continue;
            }
            if (!WarningConfig.throughWalls() && blocked(mc, camPos, s.at())) continue;
            float size = (float) (24.0 - 10.0 * Math.min(1.0, Math.max(0.0, (s.dist() - 2.0) / 22.0)));   // 24 px near, 14 px at 24 blocks
            float alpha = 1f;
            if (!s.mine()) {
                size *= 0.6f;
                alpha = 0.5f;
            }
            float progress = AttackMarkers.progress(s.m(), time);
            if (s.m().heavy()) heavy(g, sx, sy, size, progress, alpha, time);
            else normal(g, sx, sy, size, progress, alpha);
        }
        if (WarningConfig.offscreenArrow() && !arrows.isEmpty()) OffscreenArrows.draw(g, arrows, time);
        RenderSystem.disableBlend();
    }

    private static boolean blocked(Minecraft mc, Vec3 from, Vec3 to) {
        try {
            HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            return hit.getType() != HitResult.Type.MISS;
        } catch (RuntimeException e) {
            LotrLog.dbg("combat", "marker occlusion ray failed: {}", e.toString());
            return false;
        }
    }

    static final ResourceLocation WARN_NORMAL = LotrRevival.id("textures/combat/warn_normal.png");
    static final ResourceLocation WARN_HEAVY = LotrRevival.id("textures/combat/warn_heavy.png");
    static final ResourceLocation WARN_RING = LotrRevival.id("textures/combat/warn_ring.png");
    static final ResourceLocation WARN_RING_HEAVY = LotrRevival.id("textures/combat/warn_ring_heavy.png");
    /** Texture sizes and how far each shape reaches from the texture's centre, as a share of its half-width. */
    private static final int MARK_TEX = 32, RING_TEX = 64;
    private static final float NORMAL_REACH = 0.78f, HEAVY_REACH = 0.98f, RING_REACH = 0.97f;

    private static void normal(GuiGraphics g, float x, float y, float size, float progress, float alpha) {
        float r = size / 2f;
        sprite(g, WARN_NORMAL, MARK_TEX, x, y, 2f * (r + 1f) / NORMAL_REACH, 1f, 1f, 1f, alpha);
        float ring = r + (1f - progress) * size * 1.2f + 2f;
        sprite(g, WARN_RING, RING_TEX, x, y, 2f * ring / RING_REACH, 1f, 1f, 1f, alpha * (0.4f + 0.6f * progress));
    }

    private static void heavy(GuiGraphics g, float x, float y, float size, float progress, float alpha, double time) {
        float pulse = 0.8f + 0.2f * (float) Math.sin(time * 0.8);
        float r = size / 2f;
        // the spikes reach 1.6 r: the shape cue, not only the colour
        sprite(g, WARN_HEAVY, MARK_TEX, x, y, 2f * r * 1.6f / HEAVY_REACH * (0.95f + 0.05f * pulse), 1f, 1f, 1f, alpha * pulse);
        float ring = r + (1f - progress) * size * 1.2f + 2f;
        sprite(g, WARN_RING_HEAVY, RING_TEX, x, y, 2f * ring / RING_REACH, 230 / 255f, 40 / 255f, 30 / 255f, alpha * (0.5f + 0.5f * progress));
    }

    /** One texture centred on (x, y), drawn drawSize GUI pixels wide, tinted and faded. */
    static void sprite(GuiGraphics g, ResourceLocation tex, int texSize, float x, float y, float drawSize, float r, float gr, float b, float a) {
        sprite(g, tex, texSize, x, y, drawSize, 0f, r, gr, b, a);
    }

    /** As sprite, turned by angle radians (clockwise on screen). */
    static void sprite(GuiGraphics g, ResourceLocation tex, int texSize, float x, float y, float drawSize, float angle, float r, float gr,
                       float b, float a) {
        if (a <= 0.01f || drawSize <= 0.5f) return;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0f);
        if (angle != 0f) pose.mulPose(Axis.ZP.rotation(angle));
        float s = drawSize / texSize;
        pose.scale(s, s, 1f);
        g.setColor(r, gr, b, Math.min(1f, a));
        g.blit(tex, -texSize / 2, -texSize / 2, 0f, 0f, texSize, texSize, texSize, texSize);
        g.setColor(1f, 1f, 1f, 1f);
        pose.popPose();
    }
}
