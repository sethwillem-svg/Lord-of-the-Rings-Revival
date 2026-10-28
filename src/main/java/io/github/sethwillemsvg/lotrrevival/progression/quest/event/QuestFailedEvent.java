package io.github.sethwillemsvg.lotrrevival.progression.quest.event;

import io.github.sethwillemsvg.lotrrevival.progression.quest.FailReason;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Posted on the game bus after a quest was marked failed. {@code getQuest()} holds the failed instance. */
public class QuestFailedEvent extends Event {
    private final ServerPlayer player;
    private final QuestInstance quest;
    private final FailReason reason;

    public QuestFailedEvent(ServerPlayer player, QuestInstance quest, FailReason reason) {
        this.player = player;
        this.quest = quest;
        this.reason = reason;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public QuestInstance getQuest() {
        return quest;
    }

    public FailReason getReason() {
        return reason;
    }
}
