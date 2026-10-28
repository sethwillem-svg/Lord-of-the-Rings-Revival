package io.github.sethwillemsvg.lotrrevival.progression.quest.event;

import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Posted on the game bus after a quest's progress rose. {@code getQuest()} already holds the new progress. */
public class QuestProgressEvent extends Event {
    private final ServerPlayer player;
    private final QuestInstance quest;
    private final int previousProgress;

    public QuestProgressEvent(ServerPlayer player, QuestInstance quest, int previousProgress) {
        this.player = player;
        this.quest = quest;
        this.previousProgress = previousProgress;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public QuestInstance getQuest() {
        return quest;
    }

    public int getPreviousProgress() {
        return previousProgress;
    }
}
