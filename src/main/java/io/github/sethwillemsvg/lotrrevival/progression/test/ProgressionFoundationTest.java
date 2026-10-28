package io.github.sethwillemsvg.lotrrevival.progression.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressCounters;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionCounters;
import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionSync;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.BiomeTracker;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Progression foundation: config, sync scheduler, progress_counters and the biome triggers. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProgressionFoundationTest {
    private static final List<ResourceKey<Biome>> TEN_BIOMES = List.of(
            Biomes.PLAINS,
            Biomes.FOREST,
            Biomes.TAIGA,
            Biomes.DESERT,
            Biomes.SWAMP,
            Biomes.JUNGLE,
            Biomes.SAVANNA,
            Biomes.BIRCH_FOREST,
            Biomes.DARK_FOREST,
            Biomes.SNOWY_PLAINS);

    private static Holder<Biome> biome(GameTestHelper helper, ResourceKey<Biome> key) {
        return helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(key);
    }

    private static AdvancementHolder advancement(GameTestHelper helper, String id, String json) {
        JsonElement element = JsonParser.parseString(json);
        RegistryOps<JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        Advancement advancement = Advancement.CODEC.parse(ops, element).getOrThrow();
        return new AdvancementHolder(LOTRRevival.id(id), advancement);
    }

    private static <T extends CriterionTriggerInstance> void listen(
            PlayerAdvancements advancements, Criterion<T> criterion, AdvancementHolder holder) {
        criterion
                .trigger()
                .addPlayerListener(
                        advancements, new CriterionTrigger.Listener<>(criterion.triggerInstance(), holder, "c"));
    }

    private static void attach(ServerPlayer player, AdvancementHolder holder) {
        listen(player.getAdvancements(), holder.value().criteria().get("c"), holder);
    }

    private static boolean done(ServerPlayer player, AdvancementHolder holder) {
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void tenDistinctBiomesCompletesAdvancement(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            AdvancementHolder holder = advancement(
                    helper,
                    "test/ten_biomes",
                    "{\"criteria\":{\"c\":{\"trigger\":\"lotrrevival:distinct_biomes\",\"conditions\":{\"min_count\":10}}}}");
            attach(player, holder);
            ResourceKey<Level> me = ModDimensions.MIDDLE_EARTH;
            for (int i = 0; i < 9; i++) {
                helper.assertTrue(
                        BiomeTracker.observe(player, me, biome(helper, TEN_BIOMES.get(i))), "change at biome " + i);
            }
            helper.assertTrue(ProgressionCounters.visitedBiomeCount(player) == 9, "nine visited");
            helper.assertFalse(done(player, holder), "not done at nine");
            // Revisit: go back to the first biome. last_biome differs, set must not grow.
            helper.assertTrue(BiomeTracker.observe(player, me, biome(helper, TEN_BIOMES.get(0))), "revisit changes");
            helper.assertTrue(ProgressionCounters.visitedBiomeCount(player) == 9, "revisit keeps nine");
            helper.assertFalse(done(player, holder), "revisit does not complete");
            // A biome outside Middle-earth never counts.
            BiomeTracker.observe(player, Level.OVERWORLD, biome(helper, TEN_BIOMES.get(9)));
            helper.assertTrue(ProgressionCounters.visitedBiomeCount(player) == 9, "overworld does not count");
            helper.assertFalse(done(player, holder), "overworld does not complete");
            // Step back into a visited Middle-earth biome, then reach the tenth distinct one.
            BiomeTracker.observe(player, me, biome(helper, TEN_BIOMES.get(1)));
            BiomeTracker.observe(player, me, biome(helper, TEN_BIOMES.get(9)));
            helper.assertTrue(ProgressionCounters.visitedBiomeCount(player) == 10, "ten visited");
            helper.assertTrue(done(player, holder), "completed at ten");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void enterBiomeTagFiresOnceOnChange(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            AdvancementHolder holder = advancement(
                    helper,
                    "test/enter_forest",
                    "{\"criteria\":{\"c\":{\"trigger\":\"lotrrevival:enter_biome\",\"conditions\":{\"biome\":\"#minecraft:is_forest\"}}}}");
            attach(player, holder);
            Holder<Biome> plains = biome(helper, Biomes.PLAINS);
            Holder<Biome> forest = biome(helper, Biomes.FOREST);
            helper.assertTrue(BiomeTracker.observe(player, Level.OVERWORLD, plains), "first poll is a change");
            helper.assertFalse(done(player, holder), "plains does not match the tag");
            helper.assertTrue(BiomeTracker.observe(player, Level.OVERWORLD, forest), "plains -> forest changes");
            helper.assertTrue(done(player, holder), "forest matches the tag");
            for (int i = 0; i < 5; i++) {
                helper.assertFalse(BiomeTracker.observe(player, Level.OVERWORLD, forest), "repeat poll " + i);
            }
            // Overworld biomes never count toward the explorer set.
            helper.assertTrue(ProgressionCounters.visitedBiomeCount(player) == 0, "no visited biomes outside ME");
            helper.assertTrue(
                    ProgressionCounters.get(player).lastBiome().equals(Optional.of(Biomes.FOREST.location())),
                    "last_biome tracks any dimension");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void enterBiomeDimensionConditionAndArrival(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            AdvancementHolder holder = advancement(
                    helper,
                    "test/enter_nether",
                    "{\"criteria\":{\"c\":{\"trigger\":\"lotrrevival:enter_biome\",\"conditions\":{\"dimension\":\"minecraft:the_nether\"}}}}");
            attach(player, holder);
            BiomeTracker.observe(player, Level.OVERWORLD, biome(helper, Biomes.PLAINS));
            helper.assertFalse(done(player, holder), "overworld does not match");
            BiomeTracker.observe(player, Level.NETHER, biome(helper, Biomes.NETHER_WASTES));
            helper.assertTrue(done(player, holder), "fires on arrival in the nether");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void progressCountersSurviveDeath(GameTestHelper helper) {
        ServerPlayer old = helper.makeMockServerPlayerInLevel();
        ServerPlayer copy = helper.makeMockServerPlayerInLevel();
        try {
            ProgressCounters value = ProgressCounters.EMPTY
                    .withVisitedBiome(LOTRRevival.id("shire"))
                    .withVisitedBiome(ResourceLocation.fromNamespaceAndPath("removed_pack", "lost_biome"))
                    .withLastBiome(LOTRRevival.id("shire"))
                    .withKill(LOTRRevival.id("gondor"))
                    .withKill(LOTRRevival.id("gondor"))
                    .withTrade();
            old.setData(ProgressionAttachments.PROGRESS_COUNTERS, value);
            copy.copyAttachmentsFrom(old, true);
            ProgressCounters actual = copy.getData(ProgressionAttachments.PROGRESS_COUNTERS);
            helper.assertTrue(actual.equals(value), "copied on death: " + actual);
            helper.assertTrue(ProgressionCounters.killsByFaction(copy, LOTRRevival.id("gondor")) == 2, "kills");
            helper.assertTrue(ProgressionCounters.tradesTotal(copy) == 1, "trades");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(old);
            helper.getLevel().getServer().getPlayerList().remove(copy);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void progressCountersCodecRoundTripsUnknownIds(GameTestHelper helper) {
        String json = "{\"visited_biomes\":[\"gone:a\",\"lotrrevival:shire\"],\"last_biome\":\"gone:b\","
                + "\"kills_by_faction\":{\"gone:fac\":7},\"trades_total\":3}";
        ProgressCounters parsed = ProgressCounters.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow();
        helper.assertTrue(parsed.visitedBiomes().equals(Set.of(
                ResourceLocation.parse("gone:a"), ResourceLocation.parse("lotrrevival:shire"))), "visited");
        helper.assertTrue(parsed.lastBiome().equals(Optional.of(ResourceLocation.parse("gone:b"))), "last");
        helper.assertTrue(parsed.killsByFaction().equals(Map.of(ResourceLocation.parse("gone:fac"), 7)), "kills");
        JsonElement back = ProgressCounters.CODEC.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow();
        ProgressCounters again = ProgressCounters.CODEC.parse(JsonOps.INSTANCE, back).getOrThrow();
        helper.assertTrue(again.equals(parsed), "round trip");
        helper.assertTrue(
                ProgressCounters.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow()
                        .equals(ProgressCounters.EMPTY),
                "empty object decodes to defaults");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void configDefaultsAndRanges(GameTestHelper helper) {
        helper.assertTrue(ProgressionConfig.MINI_QUESTS_ENABLED.getDefault(), "miniQuestsEnabled");
        helper.assertTrue(ProgressionConfig.BOUNTIES_ENABLED.getDefault(), "bountiesEnabled");
        helper.assertTrue(ProgressionConfig.MAX_ACTIVE_QUESTS_PER_FACTION.getDefault() == 5, "per faction default");
        helper.assertTrue(ProgressionConfig.MAX_ACTIVE_QUESTS_TOTAL.getDefault() == 20, "total default");
        helper.assertTrue(ProgressionConfig.TITLES_ENABLED.getDefault(), "titlesEnabled");
        helper.assertFalse(ProgressionConfig.STRICT_FACTION_TITLES.getDefault(), "strictFactionTitles");
        helper.assertTrue(
                ProgressionConfig.ACHIEVEMENT_BROADCAST_DENYLIST
                        .getDefault()
                        .equals(List.of("lotrrevival:shire/kill_hobbit")),
                "denylist default");
        helper.assertTrue(ProgressionConfig.QUEST_TRACKER_SIDE.getDefault().equals("left"), "tracker side");
        var perFaction = ProgressionConfig.MAX_ACTIVE_QUESTS_PER_FACTION.getSpec();
        helper.assertTrue(perFaction.test(1) && perFaction.test(20), "per faction bounds accepted");
        helper.assertFalse(perFaction.test(0) || perFaction.test(21), "per faction bounds enforced");
        var total = ProgressionConfig.MAX_ACTIVE_QUESTS_TOTAL.getSpec();
        helper.assertTrue(total.test(5) && total.test(100), "total bounds accepted");
        helper.assertFalse(total.test(4) || total.test(101), "total bounds enforced");
        var side = ProgressionConfig.QUEST_TRACKER_SIDE.getSpec();
        helper.assertTrue(side.test("right"), "right accepted");
        helper.assertFalse(side.test("top"), "top rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void syncFlushesOncePerDirtyTypeAndPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AtomicInteger sent = new AtomicInteger();
        ProgressionSync.registerDeltaSender(ProgressionSync.Type.QUESTS, p -> sent.incrementAndGet());
        try {
            ProgressionSync.markDirty(player, ProgressionSync.Type.QUESTS);
            ProgressionSync.markDirty(player, ProgressionSync.Type.QUESTS);
            ProgressionSync.markDirty(player, ProgressionSync.Type.TITLES); // no sender registered: dropped
            helper.assertTrue(ProgressionSync.isDirty(player, ProgressionSync.Type.QUESTS), "dirty before flush");
            ProgressionSync.flush(List.of(player));
            helper.assertTrue(sent.get() == 1, "one delta for two marks, got " + sent.get());
            helper.assertFalse(ProgressionSync.isDirty(player, ProgressionSync.Type.QUESTS), "clean after flush");
            ProgressionSync.flush(List.of(player));
            helper.assertTrue(sent.get() == 1, "nothing resent");
            ProgressionSync.markDirty(player, ProgressionSync.Type.QUESTS);
            ProgressionSync.fullSync(player);
            helper.assertFalse(ProgressionSync.isDirty(player, ProgressionSync.Type.QUESTS), "full sync clears flags");
        } finally {
            ProgressionSync.unregisterDeltaSender(ProgressionSync.Type.QUESTS);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }
}
