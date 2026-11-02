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
 * Unattended renderer benchmark for development (with -Dlotr_revival.profile: a 30 s break, then a minute each of vanilla and pulse under Spark, instead of the measured rounds) (-Dlotr_revival.autotest=COUNT; run it with addon/bench.sh). Creates a flat
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

    private enum Stage { MENU, BASELINE, SETUP, WAIT_NPCS, WAIT_WORLD, RUN, PROFILE, DONE }

    /** Profile run (bench.sh's default, -Pprofile): instead of the measured rounds, a 30 s break, then a minute of vanilla and a
     *  minute of pulse, each under Spark's client profiler (Java sampler, stopped and uploaded by Spark after 60 s), with the
     *  frame times of each minute written to bench_results.txt. */
    private static final boolean PROFILE = Boolean.getBoolean("lotr_revival.profile");
    private static final Mode[] PROFILED = {Mode.VANILLA, Mode.PULSE};
    private static final long PROFILE_WAIT_NS = 30_000_000_000L, PROFILE_SETTLE_NS = 3_000_000_000L, PROFILE_NS = 60_000_000_000L,
            PROFILE_UPLOAD_NS = 15_000_000_000L;
    private static int profilePhase = -1;
    private static long profileStart;
    private static boolean profiling, stopped;

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
                next(Stage.BASELINE, now);
            }
            case BASELINE -> baseline(mc, now);
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
                    boolean spark = PROFILE && net.neoforged.fml.ModList.get().isLoaded("spark");
                    if (PROFILE && !spark) LOG.info("[lotr_revival bench] spark is not loaded, no profiles");
                    next(spark ? Stage.PROFILE : Stage.RUN, now);
                    if (PROFILE && !spark) waitNote += "Spark is not loaded, so this was a measuring run; ";
                }
            }
            case RUN -> run(mc, now);
            case PROFILE -> profile(mc, now);
            default -> { }
        }
    }

    /** From the title screen into the bench world (created flat on first use); ExportRun uses it too. */
    static void menu(Minecraft mc) {
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

    /** 30 s break, then per renderer: settle 3 s, start Spark for 60 s (it stops and uploads by itself), record that minute's
     *  frame times, give the upload 15 s. After the last renderer, write the results and quit. */
    private static void profile(Minecraft mc, long now) {
        if (profilePhase < 0) {
            if (now - stageStart < PROFILE_WAIT_NS) return;
            startProfilePhase(0, now);
            return;
        }
        long t = now - profileStart;
        if (!profiling && t >= PROFILE_SETTLE_NS) {
            profiling = true;
            FrameStats.reset();
            spark("start", "sparkc profiler start --timeout " + PROFILE_NS / 1_000_000_000L + " --force-java-sampler");
            Screenshot.grab(mc.gameDirectory, "bench_" + PROFILED[profilePhase].id() + ".png", mc.getMainRenderTarget(), c -> { });
        } else if (profiling && !stopped && t >= PROFILE_SETTLE_NS + PROFILE_NS) {
            stopped = true;
            phase = profilePhase;   // record() reads ORDER[phase]; profile phases use their own order
            recordProfiled();
        } else if (stopped && t >= PROFILE_SETTLE_NS + PROFILE_NS + PROFILE_UPLOAD_NS) {
            if (profilePhase + 1 < PROFILED.length) startProfilePhase(profilePhase + 1, now);
            else finish(mc);
        }
    }

    private static void recordProfiled() {
        double[] s = FrameStats.window(PROFILE_NS);
        Mode m = PROFILED[profilePhase];
        BY_MODE.computeIfAbsent(m, k -> new ArrayList<>()).add(s);
        String line = String.format("%-12s %7.2f ms  %5.1f fps  1%% low %5.1f fps  (%d frames over a minute under Spark, %d NPCs, %d in view)",
                m.id(), s[0], s[1], s[2], (int) s[3], FrameStats.npcs(), FrameStats.npcsInView());
        RESULTS.add(line);
        LOG.info("[lotr_revival bench] {}", line);
    }

    /** The empty world (no NPCs yet): wait until it is built, settle 3 s, measure 10 s. Shows what the machine does without
     *  the crowd, so the NPC cost is the difference. */
    private static boolean baselineMeasuring;

    private static void baseline(Minecraft mc, long now) {
        long t = now - stageStart;
        if (!baselineMeasuring) {
            boolean built = mc.levelRenderer.hasRenderedAllSections() && mc.levelRenderer.getSectionRenderDispatcher().isQueueEmpty();
            if ((built && t > 3_000_000_000L) || t > CHUNK_TIMEOUT_NS) {
                FrameStats.reset();
                baselineMeasuring = true;
                stageStart = now;
            }
            return;
        }
        if (t < 10_000_000_000L) return;
        double[] s = FrameStats.window(10_000_000_000L);
        String line = String.format("%-12s %7.2f ms  %5.1f fps  1%% low %5.1f fps  (%d frames, no NPCs spawned yet)", "empty world", s[0], s[1], s[2], (int) s[3]);
        RESULTS.add(0, line);
        LOG.info("[lotr_revival bench] {}", line);
        next(Stage.SETUP, now);
    }

    private static void startProfilePhase(int i, long now) {
        profilePhase = i;
        SwitchingNpcRenderer.mode = PROFILED[i];
        profileStart = now;
        profiling = false;
        stopped = false;
    }

    private static void spark(String what, String command) {
        boolean ok = net.neoforged.neoforge.client.ClientCommandHandler.runCommand(command);
        LOG.info("[lotr_revival bench] spark profile of {} {} ({}: {})", PROFILED[profilePhase].id(), what, command, ok ? "accepted" : "NOT accepted");
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
        out.add(PROFILE ? "NPCs requested: " + COUNT + ". Profile run: 30 s break, then a minute each of vanilla and pulse under Spark (frame times include Spark's overhead)."
                : "NPCs requested: " + COUNT + ". Each renderer: 4 s settle, 8 s measured; order theirs, vanilla, pulse, then reversed.");
        if (!waitNote.isEmpty()) out.add("Note: " + waitNote);
        out.add("");
        out.add(PROFILE ? "Frame times:" : "Average of both rounds:");
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
