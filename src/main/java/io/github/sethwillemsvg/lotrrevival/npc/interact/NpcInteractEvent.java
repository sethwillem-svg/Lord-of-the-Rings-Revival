package io.github.sethwillemsvg.lotrrevival.npc.interact;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Posted on {@code NeoForge.EVENT_BUS} when a player right-clicks an NPC. Trade and hire handlers cancel it to say
 * they took over the interaction; if nothing cancels TRADE or HIRE the NPC says it has nothing to offer. Cancelling
 * TALK suppresses the NPC's speech.
 */
public class NpcInteractEvent extends Event implements ICancellableEvent {
    /** What the player is trying to do. */
    public enum Kind {
        TALK,
        TRADE,
        HIRE
    }

    private final NpcEntity npc;
    private final ServerPlayer player;
    private final Kind kind;

    public NpcInteractEvent(NpcEntity npc, ServerPlayer player, Kind kind) {
        this.npc = npc;
        this.player = player;
        this.kind = kind;
    }

    public NpcEntity getNpc() {
        return npc;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Kind getKind() {
        return kind;
    }
}
