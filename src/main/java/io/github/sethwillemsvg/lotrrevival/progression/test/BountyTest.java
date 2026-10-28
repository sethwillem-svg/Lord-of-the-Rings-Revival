package io.github.sethwillemsvg.lotrrevival.progression.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.alignment.PledgeBreakReason;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionKillEvent;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementService;
import io.github.sethwillemsvg.lotrrevival.progression.bounty.Bounties;
import io.github.sethwillemsvg.lotrrevival.progression.bounty.BountyData;
import io.github.sethwillemsvg.lotrrevival.progression.bounty.BountyItems;
import io.github.sethwillemsvg.lotrrevival.progression.bounty.BountyResolution;
import io.github.sethwillemsvg.lotrrevival.progression.bounty.BountySystem;
import io.github.sethwillemsvg.lotrrevival.progression.datagen.quest.BountyDatagen;
import io.github.sethwillemsvg.lotrrevival.progression.quest.FailReason;
import io.github.sethwillemsvg.lotrrevival.progression.quest.ObjectiveData;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestRecord;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestStatus;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Bounty kill records, valid targets, both resolution swings, fixed rewards and the trophy. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BountyTest {
    private static final ResourceKey<Faction> FACTION = Factions.GONDOR;
    private static final ResourceLocation FACTION_ID = FACTION.location();
    private static final AlignmentSource TEST_SOURCE = AlignmentSource.command();

    private BountyTest() {}

    /** Swallows toasts and chat so mock players need no connection. */
    private static final class Capture implements AchievementService.Sink {
        final List<ResourceLocation> toasts = new ArrayList<>();

        @Override
        public void toast(ServerPlayer player, ResourceLocation id) {
            toasts.add(id);
        }

        @Override
        public void broadcast(ServerPlayer player, Component line) {}
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static boolean done(GameTestHelper helper, ServerPlayer player, ResourceLocation id) {
        AdvancementHolder holder = server(helper).getAdvancements().get(id);
        helper.assertTrue(holder != null, "advancement " + id + " exists");
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Gives {@code hunter} a bounty quest on {@code target} with the fixed values, as an offer would. */
    private static QuestInstance bounty(
            ServerPlayer hunter, UUID target, int records, int coins, long givenAt) {
        QuestInstance base = Quests.start(
                        hunter,
                        BountyDatagen.TEST_BOUNTY.location(),
                        records,
                        UUID.randomUUID(),
                        Component.literal("Test Giver"),
                        FACTION_ID,
                        Optional.empty())
                .orElseThrow();
        QuestInstance fixed = new QuestInstance(
                base.uuid(),
                base.template(),
                base.group(),
                base.giver(),
                base.giverName(),
                base.giverFaction(),
                base.giverPos(),
                givenAt,
                base.givenBiome(),
                records,
                Bounties.bountyValue(records),
                coins,
                0,
                QuestStatus.ACTIVE,
                Optional.empty(),
                new ObjectiveData(List.of(), Optional.of(target), Optional.of("Target")));
        hunter.setData(ProgressionAttachments.QUESTS, Quests.get(hunter).replace(fixed));
        return fixed;
    }

    private static QuestInstance find(ServerPlayer player, UUID id) {
        return Quests.get(player).find(id).orElseThrow();
    }

    private static void pledge(ServerPlayer player, float value) {
        Alignment.set(player, FACTION, value, TEST_SOURCE);
        Alignment.tryPledge(player, FACTION, true);
    }

    // ------------------------------------------------------------------ records and targets

    @GameTest(template = "empty")
    public static void twentyFiveKillsMakeATargetTwentyFourDoNot(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        BountyData data = BountyData.get(server);
        long now = Bounties.now(server);
        UUID player = UUID.randomUUID();
        data.addKills(FACTION_ID, player, now, 24);
        helper.assertTrue(Bounties.liveRecords(server, player, FACTION) == 24, "24 live records");
        helper.assertFalse(Bounties.isValidTarget(server, player, FACTION), "24 kills are not enough");
        helper.assertFalse(Bounties.validTargets(server, FACTION).contains(player), "not in the target list");
        data.addKills(FACTION_ID, player, now, 1);
        helper.assertTrue(Bounties.isValidTarget(server, player, FACTION), "25 kills make a valid target");
        helper.assertTrue(Bounties.validTargets(server, FACTION).contains(player), "listed as a valid target");
        helper.assertFalse(
                Bounties.isValidTarget(server, player, Factions.MORDOR), "records are per faction");
        data.clearKills(player, null);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recordsExpireAfterFortyEightHoursAndKeepNewest128(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        BountyData data = BountyData.get(server);
        long now = Bounties.now(server);
        UUID player = UUID.randomUUID();
        data.addKills(FACTION_ID, player, now - BountyData.RECORD_LIFETIME - 1, 10);
        data.addKills(FACTION_ID, player, now - BountyData.RECORD_LIFETIME, 15);
        helper.assertTrue(Bounties.liveRecords(server, player, FACTION) == 15, "the 10 older records dropped out");
        helper.assertFalse(Bounties.isValidTarget(server, player, FACTION), "15 live records are not enough");
        data.addKills(FACTION_ID, player, now, 10);
        helper.assertTrue(Bounties.isValidTarget(server, player, FACTION), "15 + 10 live records are enough");
        data.prune(now);
        helper.assertTrue(Bounties.liveRecords(server, player, FACTION) == 25, "prune keeps the live ones");

        data.addKills(FACTION_ID, player, now, 300);
        helper.assertTrue(Bounties.liveRecords(server, player, FACTION) == 128, "newest 128 kept");
        data.clearKills(player, null);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aClaimGivesTwelveHoursOfImmunity(GameTestHelper helper) {
        MinecraftServer server = server(helper);
        BountyData data = BountyData.get(server);
        long now = Bounties.now(server);
        UUID player = UUID.randomUUID();
        data.addKills(FACTION_ID, player, now, 30);
        helper.assertTrue(Bounties.isValidTarget(server, player, FACTION), "valid before the claim");
        data.setClaimed(FACTION_ID, player, now);
        helper.assertFalse(Bounties.isValidTarget(server, player, FACTION), "immune right after the claim");
        data.setClaimed(FACTION_ID, player, now - (BountyData.IMMUNITY - 1));
        helper.assertFalse(Bounties.isValidTarget(server, player, FACTION), "still immune one tick short");
        data.setClaimed(FACTION_ID, player, now - BountyData.IMMUNITY);
        helper.assertTrue(Bounties.isValidTarget(server, player, FACTION), "valid again after 864,000 ticks");
        data.setClaimed(FACTION_ID, player, now);
        helper.assertTrue(Bounties.isValidTarget(server, player, Factions.ROHAN) == false, "no records with Rohan");
        data.addKills(Factions.ROHAN.location(), player, now, 25);
        helper.assertTrue(Bounties.isValidTarget(server, player, Factions.ROHAN), "immunity is per faction");
        data.clearKills(player, null);
        helper.assertTrue(data.lastClaimed(FACTION_ID, player).isEmpty(), "clear also forgets the claim");
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void onlyDirectSurvivalKillsAreRecorded(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            MinecraftServer server = server(helper);
            LivingEntity victim = (LivingEntity) helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 2, 1));
            // Mock players report creative, so their kills must not count; hired-unit kills never count either.
            NeoForge.EVENT_BUS.post(new FactionKillEvent(player, victim, FACTION, 1.0f, false, false));
            NeoForge.EVENT_BUS.post(new FactionKillEvent(player, victim, FACTION, 1.0f, false, true));
            helper.assertTrue(
                    Bounties.liveRecords(server, player.getUUID(), FACTION) == 0,
                    "creative and hired kills leave no record");
            Bounties.recordKill(server, player.getUUID(), FACTION);
            helper.assertTrue(Bounties.liveRecords(server, player.getUUID(), FACTION) == 1, "recordKill adds one");
        } finally {
            BountyData.get(server(helper)).clearKills(player.getUUID(), null);
            server(helper).getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void valueAndCoinsFollowTheTable(GameTestHelper helper) {
        helper.assertTrue(Bounties.bountyValue(0) == 1, "v clamps up to 1");
        helper.assertTrue(Bounties.bountyValue(30) == 30, "v is the record count");
        helper.assertTrue(Bounties.bountyValue(128) == 50, "v clamps down to 50");
        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i < 500; i++) {
            int coins = Bounties.rollCoins(30, random);
            helper.assertTrue(coins >= 225 && coins <= 375, "30 records roll 225..375 coins, got " + coins);
        }
        helper.assertTrue(Bounties.rollCoins(0, random) == 1, "coins clamp up to 1");
        helper.assertTrue(Bounties.rollCoins(128, random) <= 1000, "coins clamp down to 1000");
        helper.succeed();
    }

    // ------------------------------------------------------------------ resolution

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void hunterKillsTargetTakesTwoVAndFailsOtherHunters(GameTestHelper helper) {
        ServerPlayer hunter = helper.makeMockServerPlayerInLevel();
        ServerPlayer rival = helper.makeMockServerPlayerInLevel();
        ServerPlayer target = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = server(helper);
        try {
            long now = Bounties.now(server);
            BountyData.get(server).addKills(FACTION_ID, target.getUUID(), now, 30);
            pledge(target, 300.0f);
            QuestInstance mine = bounty(hunter, target.getUUID(), 30, 137, now);
            QuestInstance theirs = bounty(rival, target.getUUID(), 30, 137, now);

            BountyResolution.onPlayerKill(hunter, target);

            helper.assertTrue(Alignment.get(target, FACTION) == 240.0f, "pledged +300 with v = 30 drops to +240");
            helper.assertTrue(find(hunter, mine.uuid()).state() == QuestStatus.READY_TO_TURN_IN, "hunter's quest is done");
            QuestInstance lost = find(rival, theirs.uuid());
            helper.assertTrue(lost.failed(), "the other hunter's bounty failed");
            helper.assertTrue(lost.failReason().equals(Optional.of(FailReason.CLAIMED)), "failed as claimed");
            helper.assertFalse(Bounties.isValidTarget(server, target.getUUID(), FACTION), "target is immune now");
            helper.assertTrue(
                    BountyData.get(server).lastClaimed(FACTION_ID, target.getUUID()).isPresent(), "claim recorded");

            // Floor: +130 drops to +100, +90 is untouched.
            Alignment.set(target, FACTION, 130.0f, TEST_SOURCE);
            QuestInstance second = bounty(hunter, target.getUUID(), 30, 137, now);
            BountyResolution.onPlayerKill(hunter, target);
            helper.assertTrue(Alignment.get(target, FACTION) == 100.0f, "+130 drops to the +100 floor");
            helper.assertTrue(find(hunter, second.uuid()).state() == QuestStatus.READY_TO_TURN_IN, "second quest done");

            // Below the floor a pledge cannot stand, so +90 is an unpledged target (nothing above +100 to lose from).
            Alignment.breakPledge(target, PledgeBreakReason.COMMAND);
            Alignment.set(target, FACTION, 90.0f, TEST_SOURCE);
            bounty(hunter, target.getUUID(), 30, 137, now);
            BountyResolution.onPlayerKill(hunter, target);
            helper.assertTrue(
                    Alignment.get(target, FACTION) == 90.0f, "+90 is untouched, got " + Alignment.get(target, FACTION));
        } finally {
            BountyData.get(server).clearKills(target.getUUID(), null);
            server.getPlayerList().remove(hunter);
            server.getPlayerList().remove(rival);
            server.getPlayerList().remove(target);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void unpledgedTargetLosesFromTheirHighestFactionAbove100(GameTestHelper helper) {
        ServerPlayer hunter = helper.makeMockServerPlayerInLevel();
        ServerPlayer target = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = server(helper);
        try {
            long now = Bounties.now(server);
            Alignment.set(target, FACTION, 300.0f, TEST_SOURCE);
            Alignment.set(target, Factions.ROHAN, 160.0f, TEST_SOURCE);
            helper.assertTrue(Alignment.pledge(target).isEmpty(), "target is not pledged");
            bounty(hunter, target.getUUID(), 20, 100, now);
            BountyResolution.onPlayerKill(hunter, target);
            helper.assertTrue(Alignment.get(target, FACTION) == 260.0f, "highest faction loses 2v = 40");
            helper.assertTrue(Alignment.get(target, Factions.ROHAN) == 160.0f, "other factions are untouched");
        } finally {
            server.getPlayerList().remove(hunter);
            server.getPlayerList().remove(target);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void claimedBountiesFailOnLoginForOfflineHuntersOnly(GameTestHelper helper) {
        ServerPlayer hunter = helper.makeMockServerPlayerInLevel();
        ServerPlayer offline = helper.makeMockServerPlayerInLevel();
        ServerPlayer target = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = server(helper);
        try {
            long now = Bounties.now(server);
            pledge(target, 300.0f);
            bounty(hunter, target.getUUID(), 30, 100, now);
            BountyResolution.onPlayerKill(hunter, target);

            // Quests given after the kill appear now so the online fail does not touch them.
            QuestInstance before = bounty(offline, target.getUUID(), 30, 100, now - 5);
            QuestInstance after = bounty(offline, UUID.randomUUID(), 30, 100, now + 5);
            helper.assertTrue(before.live() && after.live(), "both live before the login check");
            helper.assertTrue(BountyResolution.failClaimed(offline) == 1, "one bounty failed on login");
            helper.assertTrue(find(offline, before.uuid()).failed(), "bounty on the claimed target failed");
            helper.assertTrue(
                    find(offline, before.uuid()).failReason().equals(Optional.of(FailReason.CLAIMED)), "as claimed");
            helper.assertTrue(find(offline, after.uuid()).live(), "bounty on another target stays");
            helper.assertTrue(BountyResolution.failClaimed(offline) == 0, "second login check is a no-op");
        } finally {
            server.getPlayerList().remove(hunter);
            server.getPlayerList().remove(offline);
            server.getPlayerList().remove(target);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void targetKillsHunterSwingsBothWays(GameTestHelper helper) {
        ServerPlayer hunter = helper.makeMockServerPlayerInLevel();
        ServerPlayer target = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = server(helper);
        Capture capture = new Capture();
        AchievementService.Sink previous = AchievementService.setSink(capture);
        try {
            long now = Bounties.now(server);
            Alignment.set(hunter, FACTION, 150.0f, TEST_SOURCE);
            pledge(target, 150.0f);
            QuestInstance quest = bounty(hunter, target.getUUID(), 40, 100, now);

            BountyResolution.onPlayerKill(target, hunter);

            QuestInstance failed = find(hunter, quest.uuid());
            helper.assertTrue(failed.failed(), "hunter's bounty failed");
            helper.assertTrue(
                    failed.failReason().equals(Optional.of(FailReason.KILLED_BY_TARGET)), "killed_by_target");
            helper.assertTrue(Alignment.get(hunter, FACTION) == 100.0f, "hunter at +150 with v = 40 drops to +100");
            helper.assertTrue(Alignment.get(target, FACTION) == 190.0f, "target gains 40 with their pledge, got " + Alignment.get(target, FACTION));
            helper.assertTrue(done(helper, target, BountySystem.TURNED_THE_HUNT), "turned_the_hunt awarded");
            helper.assertFalse(done(helper, hunter, BountySystem.TURNED_THE_HUNT), "not awarded to the hunter");
            helper.assertTrue(
                    BountyData.get(server).lastClaimed(FACTION_ID, target.getUUID()).isEmpty(),
                    "a killed hunter does not claim the bounty");

            // A target with no positive faction gains nothing and nothing throws.
            ServerPlayer blank = helper.makeMockServerPlayerInLevel();
            try {
                bounty(hunter, blank.getUUID(), 10, 50, now);
                BountyResolution.onPlayerKill(blank, hunter);
                helper.assertTrue(Alignment.get(blank, FACTION) == 0.0f, "no positive faction, no gain");
            } finally {
                server.getPlayerList().remove(blank);
            }
        } finally {
            AchievementService.setSink(previous);
            server.getPlayerList().remove(hunter);
            server.getPlayerList().remove(target);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ completion

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void completionPaysFixedCoinsAndTheFifthGivesATrophy(GameTestHelper helper) {
        ServerPlayer hunter = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = server(helper);
        Capture capture = new Capture();
        AchievementService.Sink previous = AchievementService.setSink(capture);
        List<Integer> paid = new ArrayList<>();
        Quests.CoinPayout previousPayout = Quests.setCoinPayout((player, coins, giver) -> paid.add(coins));
        try {
            long now = Bounties.now(server);
            for (int n = 1; n <= 5; n++) {
                QuestInstance quest = bounty(hunter, UUID.randomUUID(), 30, 137, now);
                helper.assertTrue(Quests.markObjectiveDone(hunter, quest.uuid()), "objective marked done " + n);
                helper.assertTrue(find(hunter, quest.uuid()).state() == QuestStatus.READY_TO_TURN_IN, "ready " + n);
                QuestRecord record = Quests.complete(hunter, quest.uuid(), false, RandomSource.create(n)).orElseThrow();
                helper.assertTrue(record.rewards().coins() == 137, "coins are exactly the fixed 137");
                helper.assertTrue(record.rewards().alignment() == 30.0f, "alignment is exactly v = 30");
                helper.assertTrue(paid.size() == n && paid.get(n - 1) == 137, "paid out 137 coins");
                helper.assertTrue(Quests.get(hunter).bountiesCompleted() == n, "tally is " + n);
                int trophies = hunter.getInventory().countItem(BountyItems.BOUNTY_TROPHY.get());
                helper.assertTrue(trophies == (n >= 5 ? 1 : 0), "trophies after " + n + ": " + trophies);
                helper.assertTrue(
                        done(helper, hunter, BountySystem.FIRST_BOUNTY), "first_bounty awarded from the first on");
                helper.assertTrue(
                        done(helper, hunter, BountySystem.BOUNTY_HUNTER) == (n >= 5), "bounty_hunter at the fifth");
            }
        } finally {
            Quests.setCoinPayout(previousPayout);
            AchievementService.setSink(previous);
            server.getPlayerList().remove(hunter);
        }
        helper.succeed();
    }
}
