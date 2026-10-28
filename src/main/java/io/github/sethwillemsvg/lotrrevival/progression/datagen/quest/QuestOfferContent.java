package io.github.sethwillemsvg.lotrrevival.progression.datagen.quest;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import io.github.sethwillemsvg.lotrrevival.progression.quest.CountRange;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGroup;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestObjective;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestTemplate;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferTags;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Content of the quest offer ticket: the quest giver entity tag, the pilot quest group {@code lotrrevival:hobbits}
 * with its five templates, and the dialogue lines. The full catalogue is a later content spec.
 */
public final class QuestOfferContent {
    public static final ResourceKey<QuestGroup> HOBBITS = group("hobbits");

    /** Backing achievement of the group's first completion; the row lives in the shire tab. */
    public static final ResourceLocation FIRST_HOBBIT_ERRAND = LOTRRevival.id("shire/first_hobbit_errand");

    private QuestOfferContent() {}

    private static ResourceKey<QuestGroup> group(String path) {
        return ResourceKey.create(QuestGroup.REGISTRY, LOTRRevival.id(path));
    }

    private static ResourceKey<QuestTemplate> template(String path) {
        return ResourceKey.create(QuestTemplate.REGISTRY, LOTRRevival.id(path));
    }

    /** Adds the pilot group to the {@code quest_group} registry bootstrap. */
    public static void registerGroups(BootstrapContext<QuestGroup> context) {
        context.register(
                HOBBITS,
                new QuestGroup(
                        Factions.HOBBIT,
                        Optional.empty(),
                        false,
                        0.0f,
                        1.0f,
                        0x6BA539,
                        Optional.of(FIRST_HOBBIT_ERRAND),
                        List.of(),
                        Optional.empty()));
    }

    /** Adds the pilot templates to the {@code mini_quest} registry bootstrap. */
    @SuppressWarnings("deprecation")
    public static void registerTemplates(BootstrapContext<QuestTemplate> context) {
        context.register(
                template("hobbits/collect_bread"),
                collect(ItemPredicate.Builder.item().of(Items.BREAD).build(), 4, 15, 1.0f));
        context.register(
                template("hobbits/collect_pumpkin_pie"),
                collect(ItemPredicate.Builder.item().of(Items.PUMPKIN_PIE).build(), 1, 3, 3.0f));
        context.register(
                template("hobbits/collect_wheat"),
                collect(ItemPredicate.Builder.item().of(Items.WHEAT).build(), 10, 40, 0.25f));
        context.register(
                template("hobbits/collect_oak_logs"),
                collect(ItemPredicate.Builder.item().of(ItemTags.OAK_LOGS).build(), 10, 40, 0.25f));
        context.register(
                template("hobbits/kill_spiders"),
                new QuestTemplate(
                        HOBBITS,
                        new QuestObjective.KillEntity(
                                HolderSet.direct(EntityType.SPIDER.builtInRegistryHolder()), new CountRange(10, 20)),
                        0.5f,
                        List.of()));
    }

    private static QuestTemplate collect(ItemPredicate item, int min, int max, float factor) {
        return new QuestTemplate(
                HOBBITS, new QuestObjective.Collect(item, new CountRange(min, max)), factor, List.of());
    }

    /** Dialogue and command lines (our own wording). */
    public static void gatherLang() {
        ModLangProvider.contribute(lang -> {
            lang.add("quest.lotrrevival.offer.collect.opening.1", "Friend, might you spare a little of your time? I am short of a few things.");
            lang.add("quest.lotrrevival.offer.collect.opening.2", "Ah, a traveller! I have a small errand, if you are willing.");
            lang.add("quest.lotrrevival.offer.collect.opening.3", "I would be grateful for a helping hand with some gathering.");
            lang.add("quest.lotrrevival.offer.kill.opening.1", "Trouble has been stirring nearby. Could you deal with it for us?");
            lang.add("quest.lotrrevival.offer.kill.opening.2", "There are things out there that should not be, and I am no fighter.");
            lang.add("quest.lotrrevival.offer.kill.opening.3", "You look capable. Would you rid us of a nuisance?");
            lang.add("quest.lotrrevival.offer.progress.1", "Not quite yet. I still need %s more.");
            lang.add("quest.lotrrevival.offer.progress.2", "Keep at it, friend. %s more and we are done.");
            lang.add("quest.lotrrevival.offer.thanks.1", "That is just what I needed. You have my thanks!");
            lang.add("quest.lotrrevival.offer.thanks.2", "Well done. This will not be forgotten.");
            lang.add("quest.lotrrevival.offer.too_many", "I would not want to burden you with more errands than you already carry.");
            lang.add("quest.lotrrevival.offer.taken", "Someone else has taken this task.");
            lang.add("quest.lotrrevival.offer.closed.taken", "Someone else has taken this task.");
            lang.add("quest.lotrrevival.offer.closed.gone", "The offer is no longer available.");
            lang.add("quest.lotrrevival.offer.closed.busy", "They are busy right now.");
            lang.add("quest.lotrrevival.offer.closed.too_far", "You wandered too far away.");

            lang.add("commands.lotrrevival.quest.offer.not_giver", "That entity is not a quest giver.");
            lang.add("commands.lotrrevival.quest.offer.no_group", "No quest group matches that giver.");
            lang.add("commands.lotrrevival.quest.offer.failed", "No offer could be generated for that giver.");
            lang.add("commands.lotrrevival.quest.offer.success", "%s now offers %s (count %s).");
        });
    }

    /** Adds the {@code #lotrrevival:quest_givers} entity tag provider. */
    public static void gatherTags(
            GatherDataEvent event, CompletableFuture<HolderLookup.Provider> lookup) {
        PackOutput output = event.getGenerator().getPackOutput();
        event.getGenerator()
                .addProvider(event.includeServer(), new GiverTags(output, lookup, event.getExistingFileHelper()));
    }

    /** {@code #lotrrevival:quest_givers} = {@code #lotrrevival:npcs}. */
    static final class GiverTags extends EntityTypeTagsProvider {
        GiverTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
            super(output, lookup, LOTRRevival.MOD_ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            tag(OfferTags.QUEST_GIVERS).addOptionalTag(ModTags.EntityTypes.NPCS.location());
        }

        @Override
        public String getName() {
            return "Progression Quest Giver Tags";
        }
    }
}
