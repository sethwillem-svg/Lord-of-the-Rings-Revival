package io.github.sethwillemsvg.lotrrevival.progression.datagen.quest;

import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;

/** Strings of the quest offer screen (client.progression.quest.offer). */
final class OfferScreenLang {
    private OfferScreenLang() {}

    static void register() {
        ModLangProvider.contribute(lang -> {
            lang.add("gui.lotrrevival.offer.title", "Quest Offer");
            lang.add("gui.lotrrevival.offer.unknown_giver", "A stranger");
            lang.add("gui.lotrrevival.offer.accept", "Accept");
            lang.add("gui.lotrrevival.offer.decline", "Decline");
            lang.add("gui.lotrrevival.offer.objective.collect", "Bring me %1$s %2$s");
            lang.add("gui.lotrrevival.offer.objective.kill_entity", "Slay %1$s %2$s");
            lang.add("gui.lotrrevival.offer.objective.kill_faction", "Defeat %1$s foes of %2$s");
            lang.add("gui.lotrrevival.offer.objective.bounty", "Hunt down %s");
            lang.add("gui.lotrrevival.offer.reward.bounty", "Reward: %1$s alignment with %2$s and %3$s coins");
            lang.add("gui.lotrrevival.offer.reward.unknown_faction", "the givers");
        });
    }
}
