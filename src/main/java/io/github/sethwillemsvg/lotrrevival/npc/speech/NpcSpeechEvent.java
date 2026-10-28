package io.github.sethwillemsvg.lotrrevival.npc.speech;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Posted on {@code NeoForge.EVENT_BUS} after an NPC has said a line to a player. Informational, not cancelable. */
public class NpcSpeechEvent extends Event {
    private final NpcEntity npc;
    private final ServerPlayer player;
    private final String category;
    private final String key;

    public NpcSpeechEvent(NpcEntity npc, ServerPlayer player, String category, String key) {
        this.npc = npc;
        this.player = player;
        this.category = category;
        this.key = key;
    }

    public NpcEntity getNpc() {
        return npc;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** The category actually used (after any fallback to {@code neutral}). */
    public String getCategory() {
        return category;
    }

    /** Lang key of the line. */
    public String getKey() {
        return key;
    }
}
