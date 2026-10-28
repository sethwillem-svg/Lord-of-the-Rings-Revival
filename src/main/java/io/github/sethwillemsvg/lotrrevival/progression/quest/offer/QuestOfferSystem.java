package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;

/** Wires the quest offer system into {@code QuestSystem.init}. */
public final class QuestOfferSystem {
    private QuestOfferSystem() {}

    public static void init() {
        Quests.setCoinPayout(QuestCoinPayout::pay);
    }
}
