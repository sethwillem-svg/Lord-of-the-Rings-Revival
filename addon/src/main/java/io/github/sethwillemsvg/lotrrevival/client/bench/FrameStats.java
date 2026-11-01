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
    private static long last;
    private static int npcs, npcsInView;
    private static long lastCount;

    private FrameStats() {}

    /** Call once per frame (RenderFrameEvent.Pre). */
    public static void frame() {
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

    /** Frame stats over the window, e.g. "frame 8.31 ms   120 fps   1% low 85 fps   (5.0 s)". */
    public static String summary() {
        long total = 0;
        int n = 0;
        long[] in = new long[size];
        for (int i = 0; i < size && total < WINDOW_NS; i++) {   // newest first
            long d = frames[(head - 1 - i + CAP) % CAP];
            in[n++] = d;
            total += d;
        }
        if (n < 10) return "measuring...";
        double avgMs = total / 1e6 / n;
        long[] sorted = Arrays.copyOf(in, n);
        Arrays.sort(sorted);
        int k = Math.max(1, n / 100);
        long worst = 0;
        for (int i = n - k; i < n; i++) worst += sorted[i];
        double lowFps = 1e9 / (worst / (double) k);
        return String.format("frame %.2f ms   %.0f fps   1%% low %.0f fps   (%.1f s, %d NPCs, %d in view)", avgMs, 1000 / avgMs, lowFps,
                total / 1e9, npcs, npcsInView);
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
