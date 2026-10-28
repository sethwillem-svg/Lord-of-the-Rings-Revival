package io.github.sethwillemsvg.lotrrevival.progression.quest.event;

import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.RewardBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Posted on the game bus when a quest completes, after the rewards were rolled and before they are applied.
 * Listeners may add to {@link #getRewards()}; Progression applies the result afterwards.
 */
public class QuestCompletedEvent extends Event {
    private final ServerPlayer player;
    private final QuestInstance quest;
    private final RewardBuilder rewards;

    public QuestCompletedEvent(ServerPlayer player, QuestInstance quest, RewardBuilder rewards) {
        this.player = player;
        this.quest = quest;
        this.rewards = rewards;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public QuestInstance getQuest() {
        return quest;
    }

    public RewardBuilder getRewards() {
        return rewards;
    }
}
