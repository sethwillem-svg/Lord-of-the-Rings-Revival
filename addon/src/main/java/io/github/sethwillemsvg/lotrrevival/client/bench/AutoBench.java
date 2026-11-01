package io.github.sethwillemsvg.lotrrevival.client.bench;

import com.mojang.logging.LogUtils;
import io.github.sethwillemsvg.lotrrevival.client.render.SwitchingNpcRenderer;
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

/**
 * Unattended benchmark for development (-Dlotr_revival.autotest=COUNT, e.g. ./gradlew runClient -Pautotest=100): creates a
 * flat creative world, spawns COUNT NPCs, gives each renderer 12 s (first 4 s discarded), logs the frame stats, saves a
 * screenshot per renderer to screenshots/, then quits. Does nothing unless the property is set.
 */
public final class AutoBench {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int COUNT = Integer.getInteger("lotr_revival.autotest", 0);
    private static final SwitchingNpcRenderer.Mode[] ORDER = {SwitchingNpcRenderer.Mode.THEIRS, SwitchingNpcRenderer.Mode.VANILLA,
            SwitchingNpcRenderer.Mode.PULSE};

    private static final java.util.List<String> RESULTS = new java.util.ArrayList<>();
    private static boolean worldRequested;
    private static int ticks = -1, step = -1;

    private AutoBench() {}

    public static boolean enabled() {
        return COUNT > 0;
    }

    public static void tick() {
        if (!enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {   // first launch: skip straight to the title screen
                mc.options.onboardAccessibility = false;
                mc.options.save();
                mc.setScreen(new TitleScreen());
                return;
            }
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                LevelSettings settings = new LevelSettings("lotr_bench_" + System.currentTimeMillis(), GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(1234L, false, false),
                        reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                        mc.screen);
            }
            return;
        }
        if (mc.screen != null) mc.setScreen(null);   // close any onboarding screen
        ticks++;
        if (ticks == 100) {
            mc.player.connection.sendCommand("gamerule doDaylightCycle false");
            mc.player.connection.sendCommand("time set noon");
            mc.player.connection.sendCommand("tp @s 0 ~ 0 0 20");
            mc.player.connection.sendCommand("lotrbench spawn " + COUNT);
        }
        if (ticks < 200) return;
        int phase = (ticks - 200) / 240, within = (ticks - 200) % 240;   // 12 s per renderer
        if (phase >= ORDER.length) {
            LOG.info("[lotr_revival bench] done");
            writeResults(mc);
            mc.stop();
            return;
        }
        if (phase != step) {
            step = phase;
            SwitchingNpcRenderer.mode = ORDER[phase];
        }
        if (within == 80) FrameStats.reset();                            // discard the first 4 s
        if (within == 200) Screenshot.grab(mc.gameDirectory, "bench_" + ORDER[phase].id() + ".png", mc.getMainRenderTarget(),
                c -> LOG.info("[lotr_revival bench] screenshot {}", c.getString()));
        if (within == 239) {
            String line = String.format("%-8s %s", ORDER[phase].id(), FrameStats.summary());
            RESULTS.add(line);
            LOG.info("[lotr_revival bench] {}", line);
        }
    }

    /** run/bench_results.txt: the numbers plus the machine they came from (bench.sh prints it). */
    private static void writeResults(Minecraft mc) {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("LOTR Revival renderer benchmark, " + java.time.LocalDateTime.now().withNano(0));
        out.add("GPU: " + com.mojang.blaze3d.platform.GlUtil.getRenderer() + " (" + com.mojang.blaze3d.platform.GlUtil.getVendor() + ")");
        out.add("CPU: " + com.mojang.blaze3d.platform.GlUtil.getCpuInfo() + ", " + Runtime.getRuntime().availableProcessors() + " threads");
        out.add("Window: " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + ", render distance " + mc.options.renderDistance().get());
        out.add("NPCs requested: " + COUNT + "  (12 s per renderer, first 4 s discarded, then 5 s measured)");
        out.add("");
        out.addAll(RESULTS);
        out.add("");
        out.add("theirs  = Middle-earth's renderer (skin + 3D hair/ears/nose/feet + armour + held items)");
        out.add("vanilla = our skin layers on Minecraft's CPU model path");
        out.add("pulse   = the same skin layers on PulseLib's instanced GPU path");
        try {
            java.nio.file.Files.write(mc.gameDirectory.toPath().resolve("bench_results.txt"), out);
        } catch (java.io.IOException e) {
            LOG.warn("could not write bench_results.txt", e);
        }
    }
}
