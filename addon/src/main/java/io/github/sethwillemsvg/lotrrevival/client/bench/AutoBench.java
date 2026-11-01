package io.github.sethwillemsvg.lotrrevival.client.bench;

import com.mojang.logging.LogUtils;
import io.github.sethwillemsvg.lotrrevival.client.render.SwitchingNpcRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
        if (within == 239) LOG.info("[lotr_revival bench] {} -> {}", ORDER[phase].id(), FrameStats.summary());
    }
}
