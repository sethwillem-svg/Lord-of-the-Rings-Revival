package io.github.sethwillemsvg.lotrrevival.npc.anchor;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Game event wiring for spawn anchors. */
public final class NpcAnchorEvents {
    private NpcAnchorEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NpcAnchorEvents::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(NpcAnchorEvents::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(NpcAnchorEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, NpcAnchorEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(NpcAnchorEvents::onServerStopped);
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            NpcAnchors.trackChunk(level, chunk);
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            NpcAnchors.untrackChunk(level, event.getChunk().getPos());
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % NpcAnchors.CHECK_INTERVAL == 0) {
            NpcAnchors.checkLoadedChunks(level);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof NpcEntity npc && npc.level() instanceof ServerLevel level) {
            NpcAnchors.onNpcDeath(level, npc);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        NpcAnchors.clearTracking();
    }
}
