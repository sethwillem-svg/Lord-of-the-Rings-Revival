package io.github.sethwillemsvg.lotrrevival.client.combat;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Small arrows near the screen edge pointing toward attackers that are winding up a swing at the local player from outside
 * the view (config offscreen_arrow). Placed on an ellipse by bearing: ahead at the top, behind at the bottom.
 * TODO(spec ticket 8): the offscreen_arrow.png texture instead of drawn rows.
 */
public final class OffscreenArrows {
    private OffscreenArrows() {}

    /** bearing: radians from straight ahead, positive to the right. */
    public record Arrow(float bearing, boolean heavy) {}

    static void draw(GuiGraphics g, List<Arrow> arrows, double time) {
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h / 2f, rx = w * 0.42f, ry = h * 0.40f;
        for (Arrow a : arrows) {
            float dx = (float) Math.sin(a.bearing()), dy = (float) -Math.cos(a.bearing());
            float x = cx + dx * rx, y = cy + dy * ry;
            float pulse = a.heavy() ? 0.75f + 0.25f * (float) Math.sin(time * 0.8) : 0.9f;
            int color = a.heavy() ? MarkerRenderer.argb(pulse, 230, 40, 30) : MarkerRenderer.argb(pulse, 255, 255, 255);
            // a triangle pointing outward along (dx, dy): rows of shrinking width stepped from base to tip
            int len = a.heavy() ? 12 : 9;
            for (int i = 0; i < len; i++) {
                float half = (len - i) * 0.5f;
                float px = x + dx * i, py = y + dy * i;
                float ox = -dy * half, oy = dx * half;   // perpendicular
                int x0 = Math.round(Math.min(px - ox, px + ox)), x1 = Math.round(Math.max(px - ox, px + ox));
                int y0 = Math.round(Math.min(py - oy, py + oy)), y1 = Math.round(Math.max(py - oy, py + oy));
                g.fill(x0, y0, Math.max(x1, x0 + 1), Math.max(y1, y0 + 1), color);
            }
        }
    }
}
