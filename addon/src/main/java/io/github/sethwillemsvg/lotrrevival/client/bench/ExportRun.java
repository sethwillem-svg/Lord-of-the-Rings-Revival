package io.github.sethwillemsvg.lotrrevival.client.bench;

import com.mojang.logging.LogUtils;
import io.github.sethwillemsvg.lotrrevival.client.render.NpcExport;
import io.github.sethwillemsvg.lotrrevival.client.render.SwitchingNpcRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.entity.npcs.renderer.NpcRenderedPart;
import net.sevenstars.middleearth.entity.npcs.data.NpcTextureData;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Unattended viewer export (-Dlotr_revival.export=PER, run it with addon/bench.sh export): opens the bench world, spawns every
 * Middle-earth NPC type (PER of each, AI off), waits until they have their skins, records each with NpcExport into the game
 * folder's viewer_export/ and quits. The NPC clip viewer lists them (tools/anim-viewer/npc.html, "Model").
 */
public final class ExportRun {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int PER = Integer.getInteger("lotr_revival.export", 0);
    private static final long SECOND = 1_000_000_000L;

    private enum Stage { MENU, SETUP, WAIT, DONE }

    private static Stage stage = Stage.MENU;
    private static int inWorldTicks, lastCount = -1;
    private static long stageStart, countStable;

    private ExportRun() {}

    public static void tick() {
        if (PER <= 0 || stage == Stage.DONE) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (mc.level == null || mc.player == null) {
            AutoBench.menu(mc);
            return;
        }
        if (mc.screen != null) mc.setScreen(null);
        switch (stage) {
            case MENU -> {
                if (++inWorldTicks < 20) return;
                mc.player.connection.sendCommand("gamerule doDaylightCycle false");
                mc.player.connection.sendCommand("time set noon");
                mc.player.connection.sendCommand("tp @s 0 ~ 0 0 20");
                next(Stage.SETUP, now);
            }
            case SETUP -> {
                if (now - stageStart < SECOND) return;
                mc.player.connection.sendCommand("lotrbench clear");
                mc.player.connection.sendCommand("lotrbench spawnall " + PER);
                next(Stage.WAIT, now);
            }
            case WAIT -> {
                // wait until the NPC count has not changed for 3 s and every NPC has its skin data (Middle-earth syncs it), 60 s at most
                List<NpcEntity> npcs = npcs(mc);
                if (npcs.size() != lastCount) { lastCount = npcs.size(); countStable = now; }
                boolean ready = !npcs.isEmpty() && now - countStable > 3 * SECOND && npcs.stream().allMatch(ExportRun::hasSkin);
                if (ready || now - stageStart > 60 * SECOND) export(mc, npcs);
            }
            default -> { }
        }
    }

    private static List<NpcEntity> npcs(Minecraft mc) {
        List<NpcEntity> out = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) if (e instanceof NpcEntity n && !n.isPassenger()) out.add(n);
        return out;
    }

    private static boolean hasSkin(NpcEntity npc) {
        NpcTextureData data = npc.retrieveNpcTextureData();
        return data != null && data.get(NpcRenderedPart.BODY) != null && data.get(NpcRenderedPart.HEAD) != null;
    }

    private static void export(Minecraft mc, List<NpcEntity> npcs) {
        stage = Stage.DONE;
        Path dir = mc.gameDirectory.toPath().resolve("viewer_export");
        List<NpcExport.Npc> out = new ArrayList<>();
        int skipped = 0;
        if (npcs.isEmpty()) {
            LOG.warn("[lotr_revival export] no NPCs appeared, nothing recorded");
        } else if (mc.getEntityRenderDispatcher().getRenderer(npcs.get(0)) instanceof SwitchingNpcRenderer r) {
            for (NpcEntity npc : npcs) {
                if (!hasSkin(npc)) { skipped++; continue; }
                try {
                    out.add(NpcExport.capture(npc, r.theirs()));
                } catch (RuntimeException e) {
                    skipped++;
                    LOG.warn("[lotr_revival export] could not record {}", npc.getNpcTypeIdentifier(), e);
                }
            }
        } else {
            LOG.warn("[lotr_revival export] NPCs are not drawn by our renderer, nothing recorded");
        }
        try {
            NpcExport.write(dir, out);
            PoseSamples.write(dir);   // the game's poses for the viewer parity test
            LOG.info("[lotr_revival export] wrote {} NPCs to {} ({} skipped)", out.size(), dir, skipped);
        } catch (Exception e) {
            LOG.error("[lotr_revival export] could not write {}", dir, e);
        }
        mc.stop();
    }

    private static void next(Stage s, long now) {
        stage = s;
        stageStart = now;
    }
}
