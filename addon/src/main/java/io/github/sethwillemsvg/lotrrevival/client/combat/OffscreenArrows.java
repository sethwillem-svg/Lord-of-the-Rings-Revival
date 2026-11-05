package io.github.sethwillemsvg.lotrrevival.client.combat;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Small arrows near the screen edge pointing toward attackers that are winding up a swing at the local player from outside
 * the view (config offscreen_arrow). Placed on an ellipse by bearing: ahead at the top, behind at the bottom. The arrow is
 * our texture (textures/combat/offscreen_arrow.png, pointing up), turned toward the attacker; heavy swings tint it red,
 * draw it larger and pulse.
 */
public final class OffscreenArrows {
    private OffscreenArrows() {}

    static final ResourceLocation ARROW = LotrRevival.id("textures/combat/offscreen_arrow.png");
    private static final int TEX = 16;

    /** bearing: radians from straight ahead, positive to the right. */
    public record Arrow(float bearing, boolean heavy) {}

    /** Called inside MarkerRenderer.draw, with blending on. */
    static void draw(GuiGraphics g, List<Arrow> arrows, double time) {
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h / 2f, rx = w * 0.42f, ry = h * 0.40f;
        for (Arrow a : arrows) {
            float dx = (float) Math.sin(a.bearing()), dy = (float) -Math.cos(a.bearing());
            float x = cx + dx * rx, y = cy + dy * ry;
            float pulse = a.heavy() ? 0.75f + 0.25f * (float) Math.sin(time * 0.8) : 0.9f;
            float size = a.heavy() ? 16f : 12f;
            // the texture points up (0, -1); turning it by the bearing points it along (dx, dy)
            if (a.heavy()) MarkerRenderer.sprite(g, ARROW, TEX, x, y, size, a.bearing(), 230 / 255f, 40 / 255f, 30 / 255f, pulse);
            else MarkerRenderer.sprite(g, ARROW, TEX, x, y, size, a.bearing(), 1f, 1f, 1f, pulse);
        }
    }
}
