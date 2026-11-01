package io.github.sethwillemsvg.lotrrevival.client.bench;

import io.github.sethwillemsvg.lotrrevival.client.render.SwitchingNpcRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;

import java.util.Arrays;

/**
 * Frame-time meter for the renderer benchmark: average frame time, FPS and 1% low over the last 5 seconds, plus the
 * renderer in use and how many Middle-earth NPCs are loaded and in view. Toggle with /lotrrender hud.
 */
public final class FrameStats {
    private static final long WINDOW_NS = 5_000_000_000L;
    private static final int CAP = 8192;

    public static boolean visible = true;

    private static final long[] frames = new long[CAP];   // frame durations, ring buffer
    private static int head, size;
    private static long last, frameIndex;
    private static int npcs, npcsInView;
    private static long lastCount;

    private FrameStats() {}

    /** Call once per frame (RenderFrameEvent.Pre). */
    public static void frame() {
        frameIndex++;
        long now = System.nanoTime();
        if (last != 0) {
            frames[head] = now - last;
            head = (head + 1) % CAP;
            if (size < CAP) size++;
        }
        last = now;
        if (now - lastCount > 1_000_000_000L) { count(); lastCount = now; }
    }

    /** Forget the window (after switching renderer, so the numbers are not mixed). */
    public static void reset() {
        size = 0;
    }

    private static void count() {
        Minecraft mc = Minecraft.getInstance();
        npcs = 0;
        npcsInView = 0;
        if (mc.level == null) return;
        var dispatcher = mc.getEntityRenderDispatcher();
        var frustum = mc.levelRenderer.getFrustum();
        var cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof NpcEntity)) continue;
            npcs++;
            if (frustum != null && dispatcher.shouldRender(e, frustum, cam.x, cam.y, cam.z)) npcsInView++;
        }
    }

    public static int npcs() { return npcs; }

    /** Counts rendered frames (renderers use it for per-frame budgets). */
    public static long frameIndex() { return frameIndex; }

    public static int npcsInView() { return npcsInView; }

    /** {average ms, fps, 1% low fps, frame count} over the newest frames up to windowNs. */
    public static double[] window(long windowNs) {
        long total = 0;
        int n = 0;
        long[] in = new long[size];
        for (int i = 0; i < size && total < windowNs; i++) {   // newest first
            long d = frames[(head - 1 - i + CAP) % CAP];
            in[n++] = d;
            total += d;
        }
        if (n == 0) return new double[] {0, 0, 0, 0};
        double avgMs = total / 1e6 / n;
        long[] sorted = Arrays.copyOf(in, n);
        Arrays.sort(sorted);
        int k = Math.max(1, n / 100);
        long worst = 0;
        for (int i = n - k; i < n; i++) worst += sorted[i];
        return new double[] {avgMs, 1000 / avgMs, 1e9 / (worst / (double) k), n};
    }

    /** Frame stats over the last 5 s, e.g. "frame 8.31 ms   120 fps   1% low 85 fps   (5.0 s)". */
    public static String summary() {
        double[] w = window(WINDOW_NS);
        if (w[3] < 3) return "measuring...";
        return String.format("frame %.2f ms   %.0f fps   1%% low %.0f fps   (%d frames, %d NPCs, %d in view)", w[0], w[1], w[2], (int) w[3],
                npcs, npcsInView);
    }

    public static void draw(GuiGraphics g, DeltaTracker delta) {
        if (!visible) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        String line1 = "renderer: " + SwitchingNpcRenderer.mode.id() + "   NPCs: " + npcs + " (" + npcsInView + " in view)";
        String line2 = summary();
        int x = 4, y = mc.getWindow().getGuiScaledHeight() - 24;
        g.fill(x - 2, y - 2, x + 2 + Math.max(mc.font.width(line1), mc.font.width(line2)), y + 20, 0x90000000);
        g.drawString(mc.font, line1, x, y, 0xFFD9A441);
        g.drawString(mc.font, line2, x, y + 10, 0xFFFFFFFF);
    }
}
