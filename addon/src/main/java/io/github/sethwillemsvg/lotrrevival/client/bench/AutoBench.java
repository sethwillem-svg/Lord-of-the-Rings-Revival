package io.github.sethwillemsvg.lotrrevival.client.bench;

import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.logging.LogUtils;
import io.github.sethwillemsvg.lotrrevival.client.render.SwitchingNpcRenderer;
import io.github.sethwillemsvg.lotrrevival.client.render.SwitchingNpcRenderer.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Unattended renderer benchmark for development (-Dlotr_revival.autotest=COUNT; run it with addon/bench.sh). Creates a flat
 * creative world at render distance 12, spawns COUNT NPCs, waits until they exist and the world has finished building, then
 * measures the renderers in the order theirs, vanilla, pulse, then the same reversed (the mirrored order cancels slow drift),
 * each 4 s settle + 8 s measured by the clock. Writes run/bench_results.txt and a screenshot per renderer, then quits.
 */
public final class AutoBench {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int COUNT = Integer.getInteger("lotr_revival.autotest", 0);
    private static final Mode[] ORDER = {Mode.THEIRS, Mode.VANILLA, Mode.PULSE, Mode.PULSE, Mode.VANILLA, Mode.THEIRS};
    private static final int ROUND = ORDER.length / 2;
    private static final long SETTLE_NS = 4_000_000_000L, MEASURE_NS = 8_000_000_000L;
    private static final long SPAWN_TIMEOUT_NS = 60_000_000_000L, CHUNK_TIMEOUT_NS = 120_000_000_000L;
    private static final int RENDER_DISTANCE = 12;
    /** Reused between runs (bench.sh opens it straight from launch with --quickPlaySingleplayer). */
    public static final String WORLD = "lotr_bench";

    private enum Stage { MENU, SETUP, WAIT_NPCS, WAIT_WORLD, RUN, DONE }

    private static Stage stage = Stage.MENU;
    private static boolean worldRequested;
    private static int inWorldTicks, phase = -1, originalDistance = -1;
    private static long stageStart, phaseStart;
    private static boolean measuring;
    private static String waitNote = "";
    private static final List<String> RESULTS = new ArrayList<>();
    private static final Map<Mode, List<double[]>> BY_MODE = new EnumMap<>(Mode.class);

    private AutoBench() {}

    public static boolean enabled() {
        return COUNT > 0;
    }

    public static void tick() {
        if (!enabled() || stage == Stage.DONE) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (mc.level == null || mc.player == null) {
            menu(mc);
            return;
        }
        if (mc.screen != null) mc.setScreen(null);   // close onboarding and other screens
        switch (stage) {
            case MENU -> {
                if (++inWorldTicks < 20) return;    // let the player settle in
                if (originalDistance < 0) originalDistance = mc.options.renderDistance().get();
                mc.options.renderDistance().set(RENDER_DISTANCE);
                mc.player.connection.sendCommand("gamerule doDaylightCycle false");
                mc.player.connection.sendCommand("gamerule doWeatherCycle false");
                mc.player.connection.sendCommand("time set noon");
                mc.player.connection.sendCommand("tp @s 0 ~ 0 0 20");
                next(Stage.SETUP, now);
            }
            case SETUP -> {
                if (now - stageStart < 1_000_000_000L) return;
                mc.player.connection.sendCommand("lotrbench clear");   // NPCs left from an earlier run of this world
                mc.player.connection.sendCommand("lotrbench spawn " + COUNT);
                next(Stage.WAIT_NPCS, now);
            }
            case WAIT_NPCS -> {
                if (FrameStats.npcs() >= COUNT || now - stageStart > SPAWN_TIMEOUT_NS) {
                    if (FrameStats.npcs() < COUNT) waitNote += "only " + FrameStats.npcs() + " NPCs appeared; ";
                    next(Stage.WAIT_WORLD, now);
                }
            }
            case WAIT_WORLD -> {
                boolean built = mc.levelRenderer.hasRenderedAllSections() && mc.levelRenderer.getSectionRenderDispatcher().isQueueEmpty();
                if (built && now - stageStart > 2_000_000_000L || now - stageStart > CHUNK_TIMEOUT_NS) {
                    if (!built) waitNote += "world still building after 120 s; ";
                    LOG.info("[lotr_revival bench] world ready after {} s, starting", (now - stageStart) / 1_000_000_000L);
                    next(Stage.RUN, now);
                }
            }
            case RUN -> run(mc, now);
            default -> { }
        }
    }

    private static void menu(Minecraft mc) {
        if (mc.screen instanceof AccessibilityOnboardingScreen) {   // first launch: skip straight to the title screen
            mc.options.onboardAccessibility = false;
            mc.options.save();
            mc.setScreen(new TitleScreen());
            return;
        }
        if (!worldRequested && mc.screen instanceof TitleScreen) {
            worldRequested = true;
            if (mc.getLevelSource().levelExists(WORLD)) {   // reuse the world from an earlier run: no world generation
                mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
                return;
            }
            LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false,
                    Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1234L, false, false),
                    reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    mc.screen);
        }
    }

    private static void run(Minecraft mc, long now) {
        if (phase < 0 || now - phaseStart >= SETTLE_NS + MEASURE_NS) {
            if (phase >= 0) record();
            phase++;
            if (phase >= ORDER.length) {
                finish(mc);
                return;
            }
            SwitchingNpcRenderer.mode = ORDER[phase];
            phaseStart = now;
            measuring = false;
            return;
        }
        if (!measuring && now - phaseStart >= SETTLE_NS) {
            FrameStats.reset();
            measuring = true;
            if (phase < ROUND) Screenshot.grab(mc.gameDirectory, "bench_" + ORDER[phase].id() + ".png", mc.getMainRenderTarget(), c -> { });
        }
    }

    private static void record() {
        double[] s = FrameStats.window(MEASURE_NS);   // {avg ms, fps, 1% low fps, frames}
        Mode m = ORDER[phase];
        BY_MODE.computeIfAbsent(m, k -> new ArrayList<>()).add(s);
        String line = String.format("round %d  %-12s %7.2f ms  %5.1f fps  1%% low %5.1f fps  (%d frames, %d NPCs, %d in view)",
                phase < ROUND ? 1 : 2, m.id(), s[0], s[1], s[2], (int) s[3], FrameStats.npcs(), FrameStats.npcsInView());
        RESULTS.add(line);
        LOG.info("[lotr_revival bench] {}", line);
    }

    private static void finish(Minecraft mc) {
        stage = Stage.DONE;
        List<String> out = new ArrayList<>();
        out.add("LOTR Revival renderer benchmark, " + LocalDateTime.now().withNano(0));
        out.add("GPU: " + GlUtil.getRenderer() + " (" + GlUtil.getVendor() + ")");
        out.add("CPU: " + GlUtil.getCpuInfo() + ", " + Runtime.getRuntime().availableProcessors() + " threads");
        out.add("Window: " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + ", render distance " + RENDER_DISTANCE);
        out.add("NPCs requested: " + COUNT + ". Each renderer: 4 s settle, 8 s measured; order theirs, vanilla, pulse, then reversed.");
        if (!waitNote.isEmpty()) out.add("Note: " + waitNote);
        out.add("");
        out.add("Average of both rounds:");
        for (Mode m : Mode.values()) {
            List<double[]> r = BY_MODE.getOrDefault(m, List.of());
            if (r.isEmpty()) continue;
            double ms = r.stream().mapToDouble(a -> a[0]).average().orElse(0), low = r.stream().mapToDouble(a -> a[2]).average().orElse(0);
            out.add(String.format("  %-12s %7.2f ms  %5.1f fps  1%% low %5.1f fps", m.id(), ms, ms > 0 ? 1000 / ms : 0, low));
        }
        out.add("");
        out.addAll(RESULTS);
        out.add("");
        out.add("theirs       = Middle-earth's renderer (skin + 3D hair/ears/nose/feet + armour + held items)");
        out.add("vanilla      = our skin layers + armour + held items on Minecraft's CPU path (no 3D hair/ears/nose/feet yet)");
        out.add("pulse        = the same skin, armour and held items on PulseLib's instanced GPU path");
        try {
            Files.write(mc.gameDirectory.toPath().resolve("bench_results.txt"), out);
        } catch (IOException e) {
            LOG.warn("could not write bench_results.txt", e);
        }
        LOG.info("[lotr_revival bench] done");
        if (originalDistance > 0) mc.options.renderDistance().set(originalDistance);
        mc.stop();
    }

    private static void next(Stage s, long now) {
        stage = s;
        stageStart = now;
    }
}
