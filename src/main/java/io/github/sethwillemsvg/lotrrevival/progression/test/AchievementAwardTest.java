package io.github.sethwillemsvg.lotrrevival.progression.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.client.progression.ClientAchievements;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.api.AchievementCounters;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEligibility;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntries;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.RankRequirement;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.Relation;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementEntry.Requirement;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementService;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementTab;
import io.github.sethwillemsvg.lotrrevival.progression.api.AchievementCountChangedEvent;
import io.github.sethwillemsvg.lotrrevival.progression.api.ProgressionApi;
import io.github.sethwillemsvg.lotrrevival.progression.network.AchievementSync;
import io.github.sethwillemsvg.lotrrevival.progression.network.AchievementsDeltaPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.AchievementsFullPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionSync;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Achievement eligibility, count, award flow and sync payloads. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AchievementAwardTest {
    private static final AlignmentSource COMMAND = AlignmentSource.of(AlignmentSource.COMMAND);
    private static final ResourceLocation ENEMY_ONLY = LOTRRevival.id("test/enemy_only");

    /** What the award flow produced, captured instead of sent. */
    private static final class Capture implements AchievementService.Sink {
        final List<ResourceLocation> toasts = new ArrayList<>();
        final List<Component> broadcasts = new ArrayList<>();

        @Override
        public void toast(ServerPlayer player, ResourceLocation id) {
            toasts.add(id);
        }

        @Override
        public void broadcast(ServerPlayer player, Component line) {
            broadcasts.add(line);
        }
    }

    private static AchievementEntry entry(List<Requirement> requires, Optional<RankRequirement> rank) {
        return new AchievementEntry(
                AchievementTab.GENERAL,
                new ItemStack(Items.BOW),
                Component.literal("Test Title"),
                Component.literal("Test description"),
                AchievementEntry.SortGroup.OTHER,
                requires,
                rank,
                true);
    }

    private static AchievementEntry enemyOnly() {
        return entry(List.of(new Requirement(Relation.ENEMY, List.of(Factions.GONDOR))), Optional.empty());
    }

    private static AdvancementHolder impossible(GameTestHelper helper, ResourceLocation id) {
        JsonElement json = JsonParser.parseString("{\"criteria\":{\"c\":{\"trigger\":\"minecraft:impossible\"}}}");
        RegistryOps<JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        return new AdvancementHolder(id, Advancement.CODEC.parse(ops, json).getOrThrow());
    }

    private static boolean done(ServerPlayer player, AdvancementHolder holder) {
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static void flush(ServerPlayer player) {
        ProgressionSync.flush(List.of(player));
    }

    private static void cleanUp(GameTestHelper helper, ServerPlayer player, AchievementService.Sink previous) {
        AchievementService.setSink(previous);
        AchievementEntries.clearOverrides();
        AchievementService.forget(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void enemyEarnToastsBroadcastsAndCounts(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Capture capture = new Capture();
        AchievementService.Sink previous = AchievementService.setSink(capture);
        try {
            AchievementEntries.withOverrides(Map.of(ENEMY_ONLY, enemyOnly()));
            AdvancementHolder holder = impossible(helper, ENEMY_ONLY);
            Alignment.set(player, Factions.GONDOR, -10.0f, COMMAND);
            int before = ProgressionApi.achievementCount(player);
            helper.assertTrue(player.getAdvancements().award(holder, "c"), "award");
            helper.assertTrue(done(player, holder), "advancement done");
            helper.assertTrue(ProgressionApi.achievementCount(player) == before + 1, "count raised by one");
            helper.assertTrue(AchievementCounters.count(player) == before + 1, "core counter sees it");
            helper.assertTrue(capture.toasts.equals(List.of(ENEMY_ONLY)), "toast sent once: " + capture.toasts);
            helper.assertTrue(capture.broadcasts.size() == 1, "chat line sent once");
            String line = capture.broadcasts.get(0).getString();
            helper.assertTrue(line.contains("[Test Title]"), "line names the achievement: " + line);
        } finally {
            cleanUp(helper, player, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void ineligibleEarnIsSilentAndCountsOnceEligible(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Capture capture = new Capture();
        AchievementService.Sink previous = AchievementService.setSink(capture);
        try {
            AchievementEntries.withOverrides(Map.of(ENEMY_ONLY, enemyOnly()));
            AdvancementHolder holder = impossible(helper, ENEMY_ONLY);
            // Below the first rank (10), so no real rank entry is earned along the way.
            Alignment.set(player, Factions.GONDOR, 5.0f, COMMAND);
            int before = ProgressionApi.achievementCount(player);
            player.getAdvancements().award(holder, "c");
            helper.assertTrue(done(player, holder), "advancement recorded");
            helper.assertTrue(ProgressionApi.achievementCount(player) == before, "count unchanged");
            helper.assertTrue(capture.toasts.isEmpty(), "no toast");
            helper.assertTrue(capture.broadcasts.isEmpty(), "no chat line");
            Alignment.set(player, Factions.GONDOR, 0.0f, COMMAND);
            helper.assertTrue(ProgressionApi.achievementCount(player) == before + 1, "counts at alignment 0");
            flush(player);
            helper.assertTrue(ProgressionApi.achievementCount(player) == before + 1, "still counted after flush");
            helper.assertTrue(capture.toasts.isEmpty(), "becoming eligible never toasts");
            helper.assertTrue(capture.broadcasts.isEmpty(), "becoming eligible never broadcasts");
        } finally {
            cleanUp(helper, player, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void countChangedEventFiresWithOldAndNew(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        AchievementService.Sink previous = AchievementService.setSink(new Capture());
        List<int[]> seen = new ArrayList<>();
        Consumer<AchievementCountChangedEvent> listener = event -> {
            if (event.getPlayer() == player) {
                seen.add(new int[] {event.getOldCount(), event.getNewCount()});
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, AchievementCountChangedEvent.class, listener);
        try {
            AchievementEntries.withOverrides(Map.of(ENEMY_ONLY, enemyOnly()));
            AdvancementHolder holder = impossible(helper, ENEMY_ONLY);
            Alignment.set(player, Factions.GONDOR, -10.0f, COMMAND);
            int base = ProgressionApi.achievementCount(player);
            flush(player);
            helper.assertTrue(seen.isEmpty(), "no event without a change");

            player.getAdvancements().award(holder, "c");
            helper.assertTrue(seen.isEmpty(), "event waits for the end of the tick");
            flush(player);
            expect(helper, seen, 0, base, base + 1, "earn");

            // Below the first rank (10), so no real rank entry is earned along the way.
            Alignment.set(player, Factions.GONDOR, 5.0f, COMMAND);
            flush(player);
            expect(helper, seen, 1, base + 1, base, "alignment flip to ally");

            Alignment.set(player, Factions.GONDOR, -10.0f, COMMAND);
            flush(player);
            expect(helper, seen, 2, base, base + 1, "alignment flip back");

            player.getAdvancements().revoke(holder, "c");
            helper.assertTrue(ProgressionApi.achievementCount(player) == base, "revoke lowers the count");
            flush(player);
            expect(helper, seen, 3, base + 1, base, "revoke");
            helper.assertTrue(seen.size() == 4, "exactly four events, got " + seen.size());
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            cleanUp(helper, player, previous);
        }
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, List<int[]> seen, int index, int old, int now, String what) {
        helper.assertTrue(seen.size() > index, what + ": event missing");
        int[] event = seen.get(index);
        helper.assertTrue(event[0] == old && event[1] == now, what + ": expected " + old + "->" + now + " got "
                + event[0] + "->" + event[1]);
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void rankAbovePledgeNeedsPledge(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Capture capture = new Capture();
        AchievementService.Sink previous = AchievementService.setSink(capture);
        try {
            Faction gondor = helper.getLevel()
                    .registryAccess()
                    .registryOrThrow(ModDataRegistries.FACTION)
                    .get(Factions.GONDOR);
            List<FactionRank> ladder = gondor.ranks();
            String pledgeRank = gondor.pledgeRank().orElseThrow();
            int pledgeIndex = -1;
            for (int i = 0; i < ladder.size(); i++) {
                if (ladder.get(i).id().equals(pledgeRank)) {
                    pledgeIndex = i;
                }
            }
            helper.assertTrue(pledgeIndex >= 1 && pledgeIndex + 1 < ladder.size(), "ladder has ranks around the pledge");
            AchievementEntry above = entry(
                    List.of(), Optional.of(new RankRequirement(Factions.GONDOR, ladder.get(pledgeIndex + 1).id())));
            AchievementEntry atPledge = entry(List.of(), Optional.of(new RankRequirement(Factions.GONDOR, pledgeRank)));
            AchievementEntry below =
                    entry(List.of(), Optional.of(new RankRequirement(Factions.GONDOR, ladder.get(0).id())));

            helper.assertFalse(AchievementEligibility.isEligible(player, above), "above the pledge rank, no pledge");
            helper.assertTrue(AchievementEligibility.isEligible(player, atPledge), "at the pledge rank");
            helper.assertTrue(AchievementEligibility.isEligible(player, below), "below the pledge rank");

            Alignment.set(player, Factions.GONDOR, -1.0f, COMMAND);
            helper.assertFalse(AchievementEligibility.isEligible(player, below), "negative alignment fails every rank");
            Alignment.set(player, Factions.GONDOR, 0.0f, COMMAND);

            ResourceLocation rankId = LOTRRevival.id("test/rank_above");
            AchievementEntries.withOverrides(Map.of(rankId, above));
            AdvancementHolder holder = impossible(helper, rankId);
            int before = ProgressionApi.achievementCount(player);
            player.getAdvancements().award(holder, "c");
            helper.assertTrue(ProgressionApi.achievementCount(player) == before, "silent without a pledge");
            helper.assertTrue(capture.toasts.isEmpty(), "no toast without a pledge");

            Alignment.tryPledge(player, Factions.GONDOR, true);
            helper.assertTrue(Alignment.pledge(player).isPresent(), "pledged");
            helper.assertTrue(AchievementEligibility.isEligible(player, above), "eligible after the pledge");
            helper.assertTrue(ProgressionApi.achievementCount(player) == before + 1, "counts after the pledge");
            helper.assertTrue(capture.toasts.isEmpty(), "no toast when it becomes eligible");

            Alignment.breakPledge(player, io.github.sethwillemsvg.lotrrevival.core.alignment.PledgeBreakReason.COMMAND);
            helper.assertTrue(ProgressionApi.achievementCount(player) == before, "pledge break drops it again");

            AchievementEntry unknown = entry(
                    List.of(new Requirement(
                            Relation.ALLY,
                            List.of(ResourceKey.create(ModDataRegistries.FACTION, LOTRRevival.id("nowhere"))))),
                    Optional.empty());
            helper.assertFalse(AchievementEligibility.isEligible(player, unknown), "unknown faction is not eligible");
            AchievementEntry unknownRank =
                    entry(List.of(), Optional.of(new RankRequirement(
                            ResourceKey.create(ModDataRegistries.FACTION, LOTRRevival.id("nowhere")), "r1")));
            helper.assertFalse(AchievementEligibility.isEligible(player, unknownRank), "unknown rank faction");
        } finally {
            cleanUp(helper, player, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void allyAndEnemyBoundaries(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            AchievementEntry ally = entry(List.of(new Requirement(Relation.ALLY, List.of(Factions.GONDOR))), Optional.empty());
            AchievementEntry enemy = enemyOnly();
            AchievementEntry both = entry(
                    List.of(
                            new Requirement(Relation.ENEMY, List.of(Factions.GONDOR)),
                            new Requirement(Relation.ENEMY, List.of(Factions.MORDOR))),
                    Optional.empty());
            Alignment.set(player, Factions.GONDOR, 0.0f, COMMAND);
            helper.assertTrue(AchievementEligibility.isEligible(player, ally), "0 is ally");
            helper.assertTrue(AchievementEligibility.isEligible(player, enemy), "0 is enemy");
            Alignment.set(player, Factions.GONDOR, 5.0f, COMMAND);
            helper.assertTrue(AchievementEligibility.isEligible(player, ally), "+5 ally");
            helper.assertFalse(AchievementEligibility.isEligible(player, enemy), "+5 not enemy");
            Alignment.set(player, Factions.GONDOR, -5.0f, COMMAND);
            helper.assertFalse(AchievementEligibility.isEligible(player, ally), "-5 not ally");
            helper.assertTrue(AchievementEligibility.isEligible(player, enemy), "-5 enemy");
            Alignment.set(player, Factions.MORDOR, 5.0f, COMMAND);
            helper.assertFalse(AchievementEligibility.isEligible(player, both), "every requirement must hold");
            Alignment.set(player, Factions.MORDOR, -5.0f, COMMAND);
            helper.assertTrue(AchievementEligibility.isEligible(player, both), "all requirements hold");
        } finally {
            AchievementService.forget(player);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void denylistedIdToastsWithoutBroadcast(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Capture capture = new Capture();
        AchievementService.Sink previous = AchievementService.setSink(capture);
        try {
            ResourceLocation denied = LOTRRevival.id("shire/kill_hobbit");
            helper.assertTrue(AchievementService.isBroadcastDenied(denied), "default denylist holds kill_hobbit");
            helper.assertFalse(AchievementService.isBroadcastDenied(ENEMY_ONLY), "other ids are announced");
            AchievementEntries.withOverrides(Map.of(denied, enemyOnly()));
            AdvancementHolder holder = impossible(helper, denied);
            Alignment.set(player, Factions.GONDOR, -10.0f, COMMAND);
            player.getAdvancements().award(holder, "c");
            helper.assertTrue(capture.toasts.equals(List.of(denied)), "toast still sent");
            helper.assertTrue(capture.broadcasts.isEmpty(), "no broadcast for a denylisted id");
        } finally {
            cleanUp(helper, player, previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void clientMirrorCountsEligibleEntries(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            ResourceLocation ally = LOTRRevival.id("test/ally_only");
            AchievementEntries.withOverrides(Map.of(
                    ENEMY_ONLY,
                    enemyOnly(),
                    ally,
                    entry(List.of(new Requirement(Relation.ALLY, List.of(Factions.GONDOR))), Optional.empty())));
            Alignment.set(player, Factions.GONDOR, -10.0f, COMMAND);
            ClientAchievements.applyFull(new AchievementsFullPayload(List.of(ENEMY_ONLY), false));
            helper.assertTrue(ClientAchievements.earned().isEmpty(), "parts are held until the last arrives");
            ClientAchievements.applyFull(new AchievementsFullPayload(List.of(ally), true));
            helper.assertTrue(ClientAchievements.earned().size() == 2, "both parts applied");
            helper.assertTrue(ClientAchievements.count(player) == 1, "only the eligible one counts");
            ClientAchievements.applyDelta(new AchievementsDeltaPayload(List.of(), List.of(ENEMY_ONLY)));
            helper.assertTrue(ClientAchievements.count(player) == 0, "delta removal applied");
            ClientAchievements.applyFull(new AchievementsFullPayload(List.of(), true));
            helper.assertTrue(ClientAchievements.earned().isEmpty(), "a new full sync replaces the set");
        } finally {
            ClientAchievements.clear();
            AchievementEntries.clearOverrides();
            AchievementService.forget(player);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static List<ResourceLocation> realisticIds(int count) {
        String[] tabs = {"general", "south_east", "misty_mountains", "lothlorien", "gondor", "eriador"};
        List<ResourceLocation> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(LOTRRevival.id(tabs[i % tabs.length] + "/enter_biome_" + (1000 + i)));
        }
        return ids;
    }

    private static int encodedSize(AchievementsFullPayload payload) {
        ByteBuf buf = Unpooled.buffer();
        try {
            AchievementsFullPayload.STREAM_CODEC.encode(buf, payload);
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    @GameTest(template = "empty")
    public static void fullPayloadSizeAndSplitting(GameTestHelper helper) {
        List<ResourceLocation> ids = realisticIds(670);
        helper.assertTrue(
                encodedSize(new AchievementsFullPayload(ids, true)) < AchievementSync.MAX_PAYLOAD_BYTES,
                "670 ids fit in one payload");
        List<AchievementsFullPayload> single = AchievementSync.split(ids);
        helper.assertTrue(single.size() == 1 && single.get(0).last(), "670 ids need one payload");

        List<ResourceLocation> big = realisticIds(2500);
        List<AchievementsFullPayload> parts = AchievementSync.split(big);
        helper.assertTrue(parts.size() > 1, "oversized list is split, got " + parts.size());
        List<ResourceLocation> joined = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            AchievementsFullPayload part = parts.get(i);
            helper.assertTrue(part.last() == (i == parts.size() - 1), "only the final part is last (part " + i + ")");
            helper.assertTrue(
                    encodedSize(part) <= AchievementSync.MAX_PAYLOAD_BYTES,
                    "part " + i + " within the limit: " + encodedSize(part));
            helper.assertTrue(part.ids().size() <= CoreNetwork.MAX_COLLECTION, "part " + i + " within list cap");
            joined.addAll(part.ids());
        }
        helper.assertTrue(joined.equals(big), "ids kept in order and complete");

        // Very short ids hit the list cap before the byte limit.
        List<ResourceLocation> shortIds = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            shortIds.add(ResourceLocation.fromNamespaceAndPath("a", "b" + i));
        }
        List<AchievementsFullPayload> capped = AchievementSync.split(shortIds);
        helper.assertTrue(capped.size() == 3, "3000 short ids take three parts, got " + capped.size());
        helper.assertTrue(capped.get(2).last() && !capped.get(0).last() && !capped.get(1).last(), "last flags");

        List<AchievementsFullPayload> empty = AchievementSync.split(List.of());
        helper.assertTrue(empty.size() == 1 && empty.get(0).last() && empty.get(0).ids().isEmpty(), "empty set");

        // Wire round trip.
        ByteBuf buf = Unpooled.buffer();
        try {
            AchievementsFullPayload.STREAM_CODEC.encode(buf, parts.get(0));
            AchievementsFullPayload back = AchievementsFullPayload.STREAM_CODEC.decode(buf);
            helper.assertTrue(back.equals(parts.get(0)), "payload round trip");
        } finally {
            buf.release();
        }
        helper.succeed();
    }
}
