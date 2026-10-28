package io.github.sethwillemsvg.lotrrevival.progression.datagen.quest;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestObjective;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestTemplate;
import java.util.List;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;

/** Datagen of the bounty system: a test bounty template and the lang strings. */
public final class BountyDatagen {
    /** Bounty template of the test group (GameTests; the real bounty templates come with the offers ticket). */
    public static final ResourceKey<QuestTemplate> TEST_BOUNTY =
            ResourceKey.create(QuestTemplate.REGISTRY, LOTRRevival.id("test/bounty"));

    private BountyDatagen() {}

    /** Registers the test bounty template; called from {@link QuestDatagen#addRegistries}. */
    public static void registerTemplates(BootstrapContext<QuestTemplate> context) {
        context.register(
                TEST_BOUNTY,
                new QuestTemplate(QuestDatagen.TEST_GROUP, new QuestObjective.Bounty(), 1.0f, List.of()));
    }

    /** Adds the lang contribution; called from {@link QuestDatagen#gather}. */
    public static void gather() {
        ModLangProvider.contribute(lang -> {
            lang.add("item.lotrrevival.bounty_trophy", "Bounty Trophy");

            lang.add("bounty.lotrrevival.notice", "The %s have put a price on your head.");
            lang.add("bounty.lotrrevival.slain", "%s hunted you down and claimed the price on your head.");
            lang.add("bounty.lotrrevival.immune", "The %s will not seek your head again for a time.");
            lang.add("bounty.lotrrevival.announce.claimed", "%s has claimed the bounty on %s set by the %s.");
            lang.add("bounty.lotrrevival.announce.turned", "%s turned the hunt and slew the bounty hunter %s.");

            lang.add("commands.lotrrevival.bounty.unknown_faction", "Unknown faction: %s");
            lang.add("commands.lotrrevival.bounty.list.empty", "No bounty kill records.");
            lang.add("commands.lotrrevival.bounty.list.entry", "%s  %s  %s live record(s)  (%s)");
            lang.add("commands.lotrrevival.bounty.clear", "Cleared bounty records of %s (%s).");
            lang.add("commands.lotrrevival.bounty.addkills", "Added %s kill record(s) with %s for %s.");
        });
    }
}
