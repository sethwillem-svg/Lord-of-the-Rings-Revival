package io.github.sethwillemsvg.lotrrevival.progression.datagen.quest;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import io.github.sethwillemsvg.lotrrevival.progression.quest.CountRange;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGroup;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestObjective;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestTemplate;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;

/**
 * Datagen for the quest registries and lang. Ships one test group with one {@code collect} and one
 * {@code kill_faction} template so GameTests have data; the real catalogue is the later {@code mini-quests.md}
 * spec.
 */
public final class QuestDatagen {
    /** Test group and templates (also used by GameTests). */
    public static final ResourceKey<QuestGroup> TEST_GROUP =
            ResourceKey.create(QuestGroup.REGISTRY, LOTRRevival.id("test"));

    public static final ResourceKey<QuestTemplate> TEST_COLLECT =
            ResourceKey.create(QuestTemplate.REGISTRY, LOTRRevival.id("test/collect_wheat"));
    public static final ResourceKey<QuestTemplate> TEST_KILL =
            ResourceKey.create(QuestTemplate.REGISTRY, LOTRRevival.id("test/kill_mordor"));

    private QuestDatagen() {}

    /** Adds the {@code quest_group} and {@code mini_quest} entries to the shared registry builder. */
    public static void addRegistries(RegistrySetBuilder registries) {
        registries.add(QuestGroup.REGISTRY, context -> context.register(
                TEST_GROUP,
                new QuestGroup(Factions.GONDOR, Optional.empty(), false, 0.0f, 1.0f, 0xC0C0C0, Optional.empty(), List.of())));
        registries.add(QuestTemplate.REGISTRY, context -> {
            context.register(
                    TEST_COLLECT,
                    new QuestTemplate(
                            TEST_GROUP,
                            new QuestObjective.Collect(
                                    ItemPredicate.Builder.item().of(Items.WHEAT).build(), new CountRange(10, 40)),
                            0.25f,
                            List.of()));
            context.register(
                    TEST_KILL,
                    new QuestTemplate(
                            TEST_GROUP,
                            new QuestObjective.KillFaction(
                                    Optional.of(Factions.MORDOR), Optional.empty(), new CountRange(5, 10)),
                            1.0f,
                            List.of()));
            BountyDatagen.registerTemplates(context);
        });
    }

    /** Adds the lang contribution. */
    public static void gather() {
        BountyDatagen.gather();
        ModLangProvider.contribute(lang -> {
            lang.add("item.lotrrevival.quest_book", "Quest Book");

            lang.add("quest.lotrrevival.failed", "Quest from %s failed: %s.");
            lang.add("quest.lotrrevival.fail.giver_died", "the giver has died");
            lang.add("quest.lotrrevival.fail.invalid", "the quest no longer exists");
            lang.add("quest.lotrrevival.fail.claimed", "someone else claimed it");
            lang.add("quest.lotrrevival.fail.killed_by_target", "the target killed you");
            lang.add("quest.lotrrevival.fail.moved_on", "the giver has moved on");

            lang.add("commands.lotrrevival.quest.giver", "A stranger");
            lang.add("commands.lotrrevival.quest.unknown_template", "Unknown quest template: %s");
            lang.add("commands.lotrrevival.quest.unknown_quest", "No such quest to complete: %s");
            lang.add("commands.lotrrevival.quest.list.header", "%s: %s active quest(s), %s completed, %s failed.");
            lang.add("commands.lotrrevival.quest.list.entry", "%s  %s  %s/%s  (%s)");
            lang.add("commands.lotrrevival.quest.give", "Gave quest %s (count %s) to %s.");
            lang.add("commands.lotrrevival.quest.complete", "Completed quest %s for %s.");
            lang.add("commands.lotrrevival.quest.clear", "Cleared the quests and quest log of %s.");
        });
    }
}
