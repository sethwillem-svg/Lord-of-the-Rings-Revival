package io.github.sethwillemsvg.lotrrevival.progression.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.client.progression.ClientQuests;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionKillEvent;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import io.github.sethwillemsvg.lotrrevival.progression.network.QuestsFullPayload;
import io.github.sethwillemsvg.lotrrevival.progression.quest.DeadGivers;
import io.github.sethwillemsvg.lotrrevival.progression.quest.FailReason;
import io.github.sethwillemsvg.lotrrevival.progression.quest.ObjectiveData;
import io.github.sethwillemsvg.lotrrevival.progression.quest.PlayerQuests;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestObjective;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestOutcome;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestRecord;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestRewardSummary;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestRewards;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestStatus;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestTemplate;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests.AcceptResult;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Mini-quest data model, caps, failure, rewards, hand-in, kill credit, persistence and sync size. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuestTest {
    private static final ResourceLocation COLLECT = LOTRRevival.id("test/collect_wheat");
    private static final ResourceLocation KILL = LOTRRevival.id("test/kill_mordor");

    private QuestTest() {}

    private static QuestInstance start(
            GameTestHelper helper, ServerPlayer player, ResourceLocation template, int count, UUID giver,
            ResourceKey<io.github.sethwillemsvg.lotrrevival.core.faction.Faction> faction) {
        return Quests.start(
                        player, template, count, giver, Component.literal("Test Giver"), faction.location(), Optional.empty())
                .orElseThrow();
    }

    private static QuestInstance find(ServerPlayer player, UUID id) {
        return Quests.get(player).find(id).orElseThrow();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void capsRefuseSixthPerFactionAndTwentyFirstTotal(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            List<UUID> hobbitQuests = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                UUID giver = UUID.randomUUID();
                helper.assertTrue(
                        Quests.canAccept(player, COLLECT, giver, Factions.HOBBIT.location()) == AcceptResult.OK,
                        "hobbit quest " + i + " accepted");
                hobbitQuests.add(start(helper, player, COLLECT, 10, giver, Factions.HOBBIT).uuid());
            }
            AcceptResult sixth = Quests.canAccept(player, COLLECT, UUID.randomUUID(), Factions.HOBBIT.location());
            helper.assertTrue(sixth == AcceptResult.FACTION_CAP, "6th for one faction refused, got " + sixth);

            // Fill to 20 across four factions; a fifth faction is then refused by the total cap.
            for (var faction : List.of(Factions.BREE, Factions.DUNEDAIN_NORTH, Factions.ROHAN)) {
                for (int i = 0; i < 5; i++) {
                    UUID giver = UUID.randomUUID();
                    helper.assertTrue(
                            Quests.canAccept(player, COLLECT, giver, faction.location()) == AcceptResult.OK,
                            faction + " quest " + i + " accepted");
                    start(helper, player, COLLECT, 10, giver, faction);
                }
            }
            helper.assertTrue(Quests.get(player).liveCount() == 20, "20 live quests");
            AcceptResult twentyFirst = Quests.canAccept(player, COLLECT, UUID.randomUUID(), Factions.DALE.location());
            helper.assertTrue(twentyFirst == AcceptResult.TOTAL_CAP, "21st across factions refused, got " + twentyFirst);

            // A failed quest frees its slot in both counts.
            helper.assertTrue(Quests.fail(player, hobbitQuests.get(0), FailReason.GIVER_DIED), "failed one");
            helper.assertTrue(Quests.get(player).liveCount() == 19, "failed quest does not count");
            helper.assertTrue(
                    Quests.canAccept(player, COLLECT, UUID.randomUUID(), Factions.HOBBIT.location()) == AcceptResult.OK,
                    "hobbit slot is free again");
            helper.assertTrue(
                    Quests.canAccept(player, COLLECT, UUID.randomUUID(), Factions.DALE.location()) == AcceptResult.OK,
                    "total slot is free again");

            // One live quest per giver per player; a failed one does not block.
            UUID failedGiver = find(player, hobbitQuests.get(0)).giver();
            helper.assertTrue(
                    Quests.canAccept(player, COLLECT, failedGiver, Factions.DALE.location()) == AcceptResult.OK,
                    "failed quest does not block its giver");
            UUID liveGiver = find(player, hobbitQuests.get(1)).giver();
            helper.assertTrue(
                    Quests.canAccept(player, COLLECT, liveGiver, Factions.DALE.location())
                            == AcceptResult.ALREADY_HAS_QUEST_FROM_GIVER,
                    "one quest per giver");
            helper.assertTrue(
                    Quests.canAccept(player, LOTRRevival.id("test/nope"), UUID.randomUUID(), Factions.DALE.location())
                            == AcceptResult.UNKNOWN_TEMPLATE,
                    "unknown template");

            Alignment.set(player, Factions.GONDOR, -5.0f, io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource.command());
            helper.assertTrue(
                    Quests.canAccept(player, COLLECT, UUID.randomUUID(), Factions.DALE.location()) == AcceptResult.HOSTILE,
                    "hostile faction refused");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void giverDeathFailsQuestAndOfflineHoldersFailOnLogin(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            UUID giver = UUID.randomUUID();
            QuestInstance quest = start(helper, player, COLLECT, 10, giver, Factions.HOBBIT);
            int failed = Quests.failQuestsFromGiver(helper.getLevel().getServer(), giver);
            helper.assertTrue(failed >= 1, "at least our quest failed");
            QuestInstance after = find(player, quest.uuid());
            helper.assertTrue(after.state() == QuestStatus.FAILED, "state failed");
            helper.assertTrue(after.failReason().equals(Optional.of(FailReason.GIVER_DIED)), "giver_died");
            helper.assertTrue(Quests.get(player).active().size() == 1, "stays in active until removed");
            helper.assertTrue(Quests.get(player).failedTotal() == 1, "failed_total counted");
            helper.assertTrue(
                    DeadGivers.get(helper.getLevel().getServer()).contains(giver), "giver remembered for offline holders");

            // Offline holder: the giver died while the player was away, so the login check fails the quest.
            UUID offlineGiver = UUID.randomUUID();
            QuestInstance offline = start(helper, player, COLLECT, 10, offlineGiver, Factions.BREE);
            DeadGivers.get(helper.getLevel().getServer())
                    .add(offlineGiver, helper.getLevel().getServer().overworld().getGameTime());
            helper.assertTrue(find(player, offline.uuid()).live(), "still live before the login check");
            helper.assertTrue(Quests.checkDeadGivers(player) == 1, "login check fails one quest");
            helper.assertTrue(
                    find(player, offline.uuid()).failReason().equals(Optional.of(FailReason.GIVER_DIED)),
                    "failed with giver_died on login");
            helper.assertTrue(Quests.checkDeadGivers(player) == 0, "second login check is a no-op");

            // The player removes failed entries; they move to the log.
            helper.assertTrue(Quests.remove(player, quest.uuid()), "failed quest removed");
            helper.assertFalse(Quests.remove(player, UUID.randomUUID()), "unknown id not removed");
            PlayerQuests quests = Quests.get(player);
            helper.assertTrue(quests.find(quest.uuid()).isEmpty(), "gone from active");
            helper.assertTrue(quests.log().size() == 1 && quests.log().get(0).outcome() == QuestOutcome.FAILED, "logged");
            helper.assertTrue(
                    quests.log().get(0).failReason().equals(Optional.of(FailReason.GIVER_DIED)), "log keeps the reason");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failedEntriesAreCappedAndOldestMoveToTheLog(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < PlayerQuests.FAILED_CAP + 1; i++) {
                ids.add(start(helper, player, COLLECT, 10, UUID.randomUUID(), Factions.HOBBIT).uuid());
            }
            for (UUID id : ids) {
                helper.assertTrue(Quests.fail(player, id, FailReason.INVALID), "failed");
            }
            PlayerQuests quests = Quests.get(player);
            helper.assertTrue(quests.failedCount() == PlayerQuests.FAILED_CAP, "20 failed entries stay");
            helper.assertTrue(quests.find(ids.get(0)).isEmpty(), "the oldest moved out of active");
            helper.assertTrue(
                    quests.log().size() == 1 && quests.log().get(0).outcome() == QuestOutcome.FAILED,
                    "and into the log");
            helper.assertTrue(quests.failedTotal() == PlayerQuests.FAILED_CAP + 1, "failed_total counts all");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rewardRollsStayInBoundsAndBonusIsOneInTwelve(GameTestHelper helper) {
        RandomSource random = RandomSource.create(20_261_028L);
        final int rolls = 10_000;
        final float alignmentBase = 10.0f;
        final int coinBase = 100;
        final float eps = 1.0e-3f;
        int bonuses = 0;
        for (int i = 0; i < rolls; i++) {
            float alignment = QuestRewards.rollAlignment(alignmentBase, random);
            helper.assertTrue(
                    alignment >= alignmentBase * 0.75f - eps && alignment <= alignmentBase * 1.25f + eps,
                    "alignment " + alignment + " within base x [0.75, 1.25]");
            QuestRewards.CoinRoll coins = QuestRewards.rollCoins(coinBase, random);
            if (coins.bonus()) {
                bonuses++;
                // 2 to 5 times a value in [75, 125]
                helper.assertTrue(coins.coins() >= 150 && coins.coins() <= 625, "bonus coins " + coins.coins());
            } else {
                helper.assertTrue(
                        coins.coins() >= 75 && coins.coins() <= 125, "coins " + coins.coins() + " within base x [0.75, 1.25]");
            }
        }
        // Expected 1/12 of 10,000 = 833.3 with sigma ~27.6. Tolerance: +/- 120 (about 4.3 sigma), so the seeded run
        // passes with a wide margin and a real bug (for example 1 in 10 or 1 in 15) still fails.
        helper.assertTrue(Math.abs(bonuses - rolls / 12.0) <= 120.0, "bonus rate " + bonuses + " of " + rolls);
        // Minimum 1 on both.
        helper.assertTrue(QuestRewards.rollAlignment(0.1f, random) >= 1.0f, "alignment minimum 1");
        helper.assertTrue(QuestRewards.rollCoins(0, random).coins() >= 1, "coin minimum 1");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void missingTemplateFailsInvalidWithoutCrashing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            QuestInstance orphan = new QuestInstance(
                    UUID.randomUUID(),
                    LOTRRevival.id("test/removed_by_datapack"),
                    LOTRRevival.id("test"),
                    UUID.randomUUID(),
                    Component.literal("Gone"),
                    Factions.GONDOR.location(),
                    Optional.empty(),
                    0L,
                    Optional.empty(),
                    5,
                    2.0f,
                    4,
                    0,
                    QuestStatus.ACTIVE,
                    Optional.empty(),
                    ObjectiveData.EMPTY);
            QuestInstance fine = start(helper, player, COLLECT, 10, UUID.randomUUID(), Factions.HOBBIT);
            player.setData(
                    ProgressionAttachments.QUESTS, Quests.get(player).withAdded(orphan));

            helper.assertTrue(Quests.validate(player) == 1, "one quest invalid");
            QuestInstance failed = find(player, orphan.uuid());
            helper.assertTrue(failed.state() == QuestStatus.FAILED, "failed");
            helper.assertTrue(failed.failReason().equals(Optional.of(FailReason.INVALID)), "reason invalid");
            helper.assertTrue(find(player, fine.uuid()).live(), "valid quest untouched");
            helper.assertTrue(Quests.get(player).failedTotal() == 1, "counted as failed, no other penalty");

            // Completing an orphan fails it instead of throwing.
            QuestInstance orphan2 = new QuestInstance(
                    UUID.randomUUID(), LOTRRevival.id("test/removed_too"), LOTRRevival.id("test"), UUID.randomUUID(),
                    Component.literal("Gone"), Factions.GONDOR.location(), Optional.empty(), 0L, Optional.empty(), 5,
                    2.0f, 4, 0, QuestStatus.ACTIVE, Optional.empty(), ObjectiveData.EMPTY);
            player.setData(ProgressionAttachments.QUESTS, Quests.get(player).withAdded(orphan2));
            helper.assertTrue(Quests.complete(player, orphan2.uuid(), true).isEmpty(), "no completion");
            helper.assertTrue(find(player, orphan2.uuid()).failed(), "orphan failed invalid instead of completing");
            helper.assertTrue(Quests.get(player).completedTotal() == 0, "nothing completed");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void questsSurviveDeathAndSerializationUnchanged(GameTestHelper helper) {
        ServerPlayer old = helper.makeMockServerPlayerInLevel();
        ServerPlayer copy = helper.makeMockServerPlayerInLevel();
        try {
            QuestInstance active = start(helper, old, COLLECT, 12, UUID.randomUUID(), Factions.HOBBIT);
            QuestInstance failed = start(helper, old, KILL, 7, UUID.randomUUID(), Factions.GONDOR);
            Quests.fail(old, failed.uuid(), FailReason.GIVER_DIED);
            Quests.abandon(old, active.uuid());
            QuestInstance tracked = start(helper, old, COLLECT, 9, UUID.randomUUID(), Factions.BREE);
            PlayerQuests value = Quests.get(old)
                    .withLogged(new QuestRecord(
                            COLLECT,
                            Component.literal("Rosie"),
                            Factions.HOBBIT.location(),
                            QuestOutcome.COMPLETED,
                            Optional.empty(),
                            1234L,
                            new QuestRewardSummary(3.5f, 11, List.of(), false)))
                    .withCompleted(Factions.HOBBIT.location());
            old.setData(ProgressionAttachments.QUESTS, value);

            copy.copyAttachmentsFrom(old, true);
            PlayerQuests actual = Quests.get(copy);
            helper.assertTrue(actual.equals(value), "copied on death: " + actual);
            helper.assertTrue(actual.tracked().equals(Optional.of(tracked.uuid())), "tracked quest copied");

            // Codec round trip (what is saved with the player file).
            DataResult<net.minecraft.nbt.Tag> encoded = PlayerQuests.CODEC.encodeStart(NbtOps.INSTANCE, value);
            PlayerQuests decoded = PlayerQuests.CODEC.parse(NbtOps.INSTANCE, encoded.getOrThrow()).getOrThrow();
            helper.assertTrue(decoded.equals(value), "codec round trip");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(old);
            helper.getLevel().getServer().getPlayerList().remove(copy);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void collectHandInTakesHeldSlotFirstAndKeepsPartial(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            var inventory = player.getInventory();
            inventory.selected = 2;
            inventory.items.set(2, new ItemStack(Items.WHEAT, 5));
            inventory.items.set(20, new ItemStack(Items.WHEAT, 3));
            inventory.items.set(21, new ItemStack(Items.BREAD, 4));

            QuestInstance first = start(helper, player, COLLECT, 6, UUID.randomUUID(), Factions.HOBBIT);
            Quests.TurnIn result = Quests.handIn(player, first.uuid(), RandomSource.create(7L));
            helper.assertTrue(result.taken() == 6 && result.completed(), "six taken and completed: " + result);
            helper.assertTrue(inventory.items.get(2).isEmpty(), "held slot taken first");
            helper.assertTrue(inventory.items.get(20).getCount() == 2, "then the next slot, one taken");
            helper.assertTrue(inventory.items.get(21).getCount() == 4, "other items untouched");
            PlayerQuests afterFirst = Quests.get(player);
            helper.assertTrue(afterFirst.find(first.uuid()).isEmpty(), "completed quest left active");
            helper.assertTrue(afterFirst.completedTotal() == 1, "completed_total");
            helper.assertTrue(
                    afterFirst.completedByFaction().get(Factions.HOBBIT.location()) == 1, "completed_by_faction");
            QuestRecord record = afterFirst.log().get(0);
            helper.assertTrue(record.outcome() == QuestOutcome.COMPLETED, "completed record");
            helper.assertTrue(
                    record.rewards().alignment() >= 1.0f && record.rewards().coins() >= 1, "rewards recorded");
            helper.assertTrue(Alignment.get(player, Factions.GONDOR) > 0.0f, "alignment applied to the group's faction");

            // Partial: only four of twelve are in the inventory; the quest stays active with progress 4.
            inventory.items.set(20, new ItemStack(Items.WHEAT, 4));
            QuestInstance second = start(helper, player, COLLECT, 12, UUID.randomUUID(), Factions.BREE);
            Quests.TurnIn partial = Quests.handIn(player, second.uuid(), RandomSource.create(8L));
            helper.assertTrue(partial.taken() == 4 && !partial.completed(), "partial hand-in of four: " + partial);
            QuestInstance kept = find(player, second.uuid());
            helper.assertTrue(kept.progress() == 4 && kept.state() == QuestStatus.ACTIVE, "partial progress kept");
            helper.assertTrue(Quests.handIn(player, second.uuid(), RandomSource.create(9L)).taken() == 0, "nothing left");
            inventory.items.set(5, new ItemStack(Items.WHEAT, 30));
            Quests.TurnIn rest = Quests.handIn(player, second.uuid(), RandomSource.create(10L));
            helper.assertTrue(rest.taken() == 8 && rest.completed(), "only what is owed is taken: " + rest);
            helper.assertTrue(inventory.items.get(5).getCount() == 22, "the surplus stays");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void hiredUnitKillCountsForKillFactionQuest(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            QuestInstance quest = start(helper, player, KILL, 2, UUID.randomUUID(), Factions.GONDOR);
            LivingEntity victim = EntityType.PIG.create(helper.getLevel());
            NeoForge.EVENT_BUS.post(new FactionKillEvent(player, victim, Factions.GONDOR, 1.0f, false, true));
            helper.assertTrue(find(player, quest.uuid()).progress() == 0, "a kill of another faction does not count");
            NeoForge.EVENT_BUS.post(new FactionKillEvent(player, victim, Factions.MORDOR, 1.0f, false, true));
            helper.assertTrue(find(player, quest.uuid()).progress() == 1, "hired-unit kill counts");
            NeoForge.EVENT_BUS.post(new FactionKillEvent(player, victim, Factions.MORDOR, 1.0f, false, false));
            QuestInstance ready = find(player, quest.uuid());
            helper.assertTrue(ready.progress() == 2 && ready.state() == QuestStatus.READY_TO_TURN_IN, "ready to turn in");
            NeoForge.EVENT_BUS.post(new FactionKillEvent(player, victim, Factions.MORDOR, 1.0f, false, false));
            helper.assertTrue(find(player, quest.uuid()).progress() == 2, "no progress beyond the count");

            Quests.TurnIn turnIn = Quests.turnIn(player, quest.uuid(), RandomSource.create(3L));
            helper.assertTrue(turnIn.completed(), "completes when returned to the giver");
            helper.assertTrue(Quests.get(player).find(quest.uuid()).isEmpty(), "left active");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void abandonIsFreeAndTrackingFollowsTheQuest(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            QuestInstance a = start(helper, player, COLLECT, 10, UUID.randomUUID(), Factions.HOBBIT);
            QuestInstance b = start(helper, player, COLLECT, 10, UUID.randomUUID(), Factions.HOBBIT);
            helper.assertTrue(Quests.get(player).tracked().equals(Optional.of(b.uuid())), "new quest auto-tracked");
            helper.assertTrue(Quests.track(player, Optional.of(a.uuid())), "track another");
            helper.assertFalse(Quests.track(player, Optional.of(UUID.randomUUID())), "cannot track an unknown quest");
            float before = Alignment.get(player, Factions.HOBBIT);
            helper.assertTrue(Quests.abandon(player, a.uuid()), "abandoned");
            PlayerQuests quests = Quests.get(player);
            helper.assertTrue(quests.tracked().isEmpty(), "abandoning the tracked quest clears tracking");
            helper.assertTrue(quests.failedTotal() == 0, "abandoning is not a failure");
            helper.assertTrue(Alignment.get(player, Factions.HOBBIT) == before, "no penalty");
            helper.assertTrue(quests.log().get(0).outcome() == QuestOutcome.ABANDONED, "logged as abandoned");
            helper.assertTrue(Quests.deleteRecord(player, 0), "record deleted");
            helper.assertTrue(Quests.get(player).log().isEmpty(), "log empty");
            helper.assertFalse(Quests.deleteRecord(player, 0), "nothing more to delete");
            Quests.clear(player);
            helper.assertTrue(Quests.get(player).active().isEmpty(), "clear drops active quests");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void templateJsonParsesAndBaseValuesFollowTheSpecTable(GameTestHelper helper) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        String collect = "{\"group\":\"lotrrevival:test\",\"objective\":{\"type\":\"lotrrevival:collect\","
                + "\"item\":{\"items\":\"minecraft:wheat\"},\"count\":{\"min\":10,\"max\":40}},\"reward_factor\":0.25,"
                + "\"extra_rewards\":[]}";
        QuestTemplate parsed =
                QuestTemplate.CODEC.parse(ops, JsonParser.parseString(collect)).getOrThrow();
        helper.assertTrue(parsed.objective() instanceof QuestObjective.Collect, "collect objective");
        helper.assertTrue(parsed.objective().count().min() == 10 && parsed.objective().count().max() == 40, "range");
        // collect: alignment max(N x factor, 1), coins round(alignment x 2)
        helper.assertTrue(parsed.alignmentBase(20) == 5.0f && parsed.coinBase(20) == 10, "collect N=20");
        helper.assertTrue(parsed.alignmentBase(2) == 1.0f && parsed.coinBase(2) == 2, "collect minimum alignment 1");

        String kill = "{\"group\":\"lotrrevival:test\",\"objective\":{\"type\":\"lotrrevival:kill_faction\","
                + "\"faction\":\"lotrrevival:mordor\",\"count\":{\"min\":10,\"max\":30}},\"reward_factor\":0.5,"
                + "\"extra_rewards\":[{\"type\":\"lotrrevival:coins\",\"amount\":3},{\"type\":\"lotrrevival:hire_giver\"}]}";
        QuestTemplate killTemplate =
                QuestTemplate.CODEC.parse(ops, JsonParser.parseString(kill)).getOrThrow();
        helper.assertTrue(killTemplate.alignmentBase(5) == 2.5f && killTemplate.coinBase(5) == 5, "kill N=5 factor 0.5");
        helper.assertTrue(killTemplate.extraRewards().size() == 2, "extra rewards parsed");

        String entity = "{\"group\":\"lotrrevival:test\",\"objective\":{\"type\":\"lotrrevival:kill_entity\","
                + "\"entity\":\"minecraft:zombie\",\"count\":{\"min\":1,\"max\":10}},\"reward_factor\":2}";
        QuestTemplate entityTemplate =
                QuestTemplate.CODEC.parse(ops, JsonParser.parseString(entity)).getOrThrow();
        helper.assertTrue(
                entityTemplate.objective() instanceof QuestObjective.KillEntity kill1 && kill1.matches(EntityType.ZOMBIE)
                        && !kill1.matches(EntityType.PIG),
                "kill_entity matches its type");

        String unknown = "{\"group\":\"lotrrevival:test\",\"objective\":{\"type\":\"lotrrevival:dance\"},"
                + "\"reward_factor\":1}";
        helper.assertTrue(
                QuestTemplate.CODEC.parse(ops, JsonParser.parseString(unknown)).result().isEmpty(),
                "unknown objective type is rejected");
        String both = "{\"group\":\"lotrrevival:test\",\"objective\":{\"type\":\"lotrrevival:kill_faction\","
                + "\"count\":{\"min\":1,\"max\":2}},\"reward_factor\":1}";
        helper.assertTrue(
                QuestTemplate.CODEC.parse(ops, JsonParser.parseString(both)).result().isEmpty(),
                "kill_faction needs a faction or a tag");
        // The datagen test templates are in the registry.
        var registry = helper.getLevel().registryAccess().registryOrThrow(QuestTemplate.REGISTRY);
        helper.assertTrue(registry.containsKey(COLLECT) && registry.containsKey(KILL), "test templates registered");
        helper.succeed();
    }

    private static QuestInstance filler(int i, long now) {
        return new QuestInstance(
                UUID.randomUUID(),
                LOTRRevival.id("shire/collect_pipeweed_leaves"),
                LOTRRevival.id("hobbits"),
                UUID.randomUUID(),
                Component.literal("Rosie Cotton of Bywater " + i),
                LOTRRevival.id("hobbit"),
                Optional.of(GlobalPos.of(Level.OVERWORLD, new BlockPos(1000 + i, 70, -2000 - i))),
                now + i,
                Optional.of(LOTRRevival.id("shire")),
                40,
                10.0f,
                20,
                i,
                i % 3 == 0 ? QuestStatus.READY_TO_TURN_IN : QuestStatus.ACTIVE,
                Optional.empty(),
                ObjectiveData.EMPTY);
    }

    private static QuestRecord recordOf(int i) {
        return new QuestRecord(
                LOTRRevival.id("shire/collect_pipeweed_leaves"),
                Component.literal("Rosie Cotton of Bywater " + i),
                LOTRRevival.id("hobbit"),
                QuestOutcome.COMPLETED,
                Optional.empty(),
                1_000_000L + i,
                new QuestRewardSummary(10.4f, 21, List.of(new ItemStack(Items.WHEAT, 3)), false));
    }

    @GameTest(template = "empty")
    public static void questsFullStaysUnder32KiBForTwentyActiveAndFiftyLog(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        List<QuestInstance> active = new ArrayList<>();
        List<QuestRecord> log = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            active.add(filler(i, 5_000_000L));
        }
        for (int i = 0; i < 50; i++) {
            log.add(recordOf(i));
        }
        PlayerQuests state = new PlayerQuests(
                active,
                Optional.of(active.get(0).uuid()),
                log,
                Map.of(LOTRRevival.id("hobbit"), 40, LOTRRevival.id("bree"), 12, LOTRRevival.id("gondor"), 5),
                57,
                3,
                0);
        List<QuestsFullPayload> payloads = QuestsFullPayload.split(state, access);
        int size = QuestsFullPayload.encodedSize(state, access);
        helper.assertTrue(payloads.size() == 1, "20 active and 50 log fit in one chunk, size " + size);
        helper.assertTrue(payloads.get(0).first() && payloads.get(0).last(), "flags on the single chunk");
        helper.assertTrue(size < 32 * 1024, "quests_full size " + size + " is under 32 KiB");

        // A state far beyond the cap splits into several chunks, each under the limit.
        List<QuestInstance> manyActive = new ArrayList<>();
        List<QuestRecord> manyLog = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            manyActive.add(filler(i, 5_000_000L));
            manyLog.add(recordOf(i));
        }
        PlayerQuests huge = new PlayerQuests(manyActive, Optional.empty(), manyLog, Map.of(), 1, 2, 0);
        List<QuestsFullPayload> chunks = QuestsFullPayload.split(huge, access);
        helper.assertTrue(chunks.size() > 1, "split into several chunks: " + chunks.size());
        int activeTotal = 0;
        int logTotal = 0;
        for (int i = 0; i < chunks.size(); i++) {
            QuestsFullPayload chunk = chunks.get(i);
            helper.assertTrue(chunk.first() == (i == 0) && chunk.last() == (i == chunks.size() - 1), "flags " + i);
            helper.assertTrue(
                    QuestsFullPayload.encodedSize(chunk.part(), access) <= QuestsFullPayload.MAX_BYTES,
                    "chunk " + i + " within the limit");
            activeTotal += chunk.part().active().size();
            logTotal += chunk.part().log().size();
        }
        helper.assertTrue(activeTotal == 300 && logTotal == 300, "no entry lost across chunks");

        // Wire round trip and client assembly of the chunks.
        List<QuestsFullPayload> decoded = new ArrayList<>();
        for (QuestsFullPayload chunk : chunks) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
            QuestsFullPayload.STREAM_CODEC.encode(buf, chunk);
            decoded.add(QuestsFullPayload.STREAM_CODEC.decode(buf));
            buf.release();
        }
        decoded.forEach(ClientQuests::applyFull);
        try {
            PlayerQuests mirrored = ClientQuests.get();
            helper.assertTrue(
                    mirrored.active().size() == 300 && mirrored.log().size() == 300, "client mirror assembled the chunks");
            helper.assertTrue(mirrored.completedTotal() == 1 && mirrored.failedTotal() == 2, "counters from the first chunk");
            helper.assertTrue(mirrored.active().get(0).equals(manyActive.get(0)), "entries survive the wire");
        } finally {
            ClientQuests.reset();
        }
        helper.succeed();
    }
}
