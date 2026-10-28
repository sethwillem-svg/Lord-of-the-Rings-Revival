package io.github.sethwillemsvg.lotrrevival.progression.quest.event;

import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Posted on the game bus after a player started a quest. */
public class QuestAcceptedEvent extends Event {
    private final ServerPlayer player;
    private final QuestInstance quest;

    public QuestAcceptedEvent(ServerPlayer player, QuestInstance quest) {
        this.player = player;
        this.quest = quest;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public QuestInstance getQuest() {
        return quest;
    }
}
