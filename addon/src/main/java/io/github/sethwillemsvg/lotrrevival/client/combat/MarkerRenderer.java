package io.github.sethwillemsvg.lotrrevival.client.combat;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
 * (shape differs, not only colour). Swings at the local player draw at full size; others at 60% and half opacity.
 * Occlusion is one block ray per marker (config through_walls). Swings at you from outside the view go to OffscreenArrows.
 * TODO(spec ticket 4/8): textured markers (warn_normal/warn_heavy/warn_ring.png), FOV effects (sprint, zoom), bobbing.
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

    private static void normal(GuiGraphics g, float x, float y, float size, float progress, float alpha) {
        int white = argb(alpha, 255, 255, 255), outline = argb(alpha * 0.8f, 20, 20, 20);
        diamond(g, x, y, size / 2f + 1f, outline);
        diamond(g, x, y, size / 2f, white);
        float ring = size / 2f + (1f - progress) * size * 1.2f + 2f;
        diamondOutline(g, x, y, ring, progress >= 1f ? 2 : 1, argb(alpha * (0.4f + 0.6f * progress), 255, 255, 255));
    }

    private static void heavy(GuiGraphics g, float x, float y, float size, float progress, float alpha, double time) {
        float pulse = 0.8f + 0.2f * (float) Math.sin(time * 0.8);
        int red = argb(alpha * pulse, 230, 40, 30), dark = argb(alpha * 0.8f, 40, 0, 0);
        float r = size / 2f;
        // four spikes (the shape cue), then the diamond over them
        int spike = Math.max(1, Math.round(size / 10f));
        g.fill(Math.round(x - spike), Math.round(y - r * 1.6f), Math.round(x + spike), Math.round(y + r * 1.6f), red);
        g.fill(Math.round(x - r * 1.6f), Math.round(y - spike), Math.round(x + r * 1.6f), Math.round(y + spike), red);
        diamond(g, x, y, r + 1f, dark);
        diamond(g, x, y, r, red);
        float ring = r + (1f - progress) * size * 1.2f + 2f;
        diamondOutline(g, x, y, ring, progress >= 1f ? 3 : 2, argb(alpha * (0.5f + 0.5f * progress), 230, 40, 30));
    }

    /** A filled diamond of half-diagonal r, drawn as horizontal rows. */
    private static void diamond(GuiGraphics g, float x, float y, float r, int color) {
        int rows = Math.max(1, Math.round(r));
        for (int i = -rows; i <= rows; i++) {
            float half = r * (1f - Math.abs(i) / (float) rows);
            if (half < 0.5f) half = 0.5f;
            g.fill(Math.round(x - half), Math.round(y + i), Math.round(x + half), Math.round(y + i + 1), color);
        }
    }

    /** A diamond outline of half-diagonal r and the given thickness. */
    private static void diamondOutline(GuiGraphics g, float x, float y, float r, int thickness, int color) {
        int rows = Math.max(1, Math.round(r));
        for (int i = -rows; i <= rows; i++) {
            float half = r * (1f - Math.abs(i) / (float) rows);
            int left = Math.round(x - half), right = Math.round(x + half), yy = Math.round(y + i);
            g.fill(left - thickness / 2, yy, left + (thickness + 1) / 2, yy + 1, color);
            if (right != left) g.fill(right - thickness / 2, yy, right + (thickness + 1) / 2, yy + 1, color);
        }
    }

    static int argb(float alpha, int r, int g, int b) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return a << 24 | r << 16 | g << 8 | b;
    }
}
