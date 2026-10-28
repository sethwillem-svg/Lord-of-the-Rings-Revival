package io.github.sethwillemsvg.lotrrevival.progression.test;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.coin.Coins;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.test.TestArea;
import io.github.sethwillemsvg.lotrrevival.npc.entity.HobbitEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.NpcUnitData;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitList;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementService;
import io.github.sethwillemsvg.lotrrevival.progression.quest.DeadGivers;
import io.github.sethwillemsvg.lotrrevival.progression.quest.FailReason;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGiverData;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGroup;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestRecord;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.FirstCompletion;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferDialogue;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferRoller;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferService;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferTicker;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferViewers;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.QuestGivers;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.QuestInteraction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Quest offers: roll rate, eligibility, lifetime, interaction, accept, coins, first completion, giver death. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuestOfferTest {
    private static final ResourceLocation BREAD = LOTRRevival.id("hobbits/collect_bread");
    private static final ResourceLocation FIRST_ERRAND = LOTRRevival.id("shire/first_hobbit_errand");

    private QuestOfferTest() {}

    // ------------------------------------------------------------------ fixtures

    /** A frozen hobbit that is not persistent yet (helper.spawn would set persistence). */
    private static HobbitEntity giver(GameTestHelper helper, double x, double z) {
        HobbitEntity npc = NpcEntityTypes.HOBBIT.get().create(helper.getLevel());
        Vec3 pos = helper.absoluteVec(new Vec3(x, 0, z));
        npc.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
        npc.setNoAi(true);
        helper.getLevel().addFreshEntity(npc);
        return npc;
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, double x, double z) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 pos = helper.absoluteVec(new Vec3(x, 0, z));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    private static void remove(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            OfferViewers.removePlayer(player.getUUID());
            AchievementService.forget(player);
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    private static QuestGiverData.Offer breadOffer(int ticks) {
        return new QuestGiverData.Offer(BREAD, 5, 5.0f, 10, ticks, Optional.empty());
    }

    private static void startBread(ServerPlayer player, UUID giver, int count) {
        Quests.start(
                player,
                BREAD,
                count,
                giver,
                Component.literal("Test Hobbit"),
                Factions.HOBBIT.location(),
                Optional.empty());
    }

    // ------------------------------------------------------------------ roll

    @GameTest(template = "empty")
    public static void offerRollRateAndKindWeights(GameTestHelper helper) {
        helper.assertTrue(OfferRoller.rate(1.0f) == 20_000, "rate at multiplier 1");
        helper.assertTrue(OfferRoller.rate(2.0f) == 10_000, "rate at multiplier 2");
        helper.assertTrue(OfferRoller.rate(0.0f) == 0, "multiplier 0 never rolls");
        helper.assertTrue(!OfferRoller.shouldRoll(RandomSource.create(1L), 0), "rate 0 never rolls");

        RandomSource random = RandomSource.create(20_261_028L);
        int hits = 0;
        for (int tick = 0; tick < 100_000; tick++) {
            if (OfferRoller.shouldRoll(random, 20_000)) {
                hits++;
            }
        }
        helper.assertTrue(hits >= 1 && hits <= 12, "about 5 offers per 100,000 ticks, got " + hits);

        HobbitEntity npc = giver(helper, 2, 2);
        QuestGivers.GroupMatch group = QuestGivers.eligible(npc).orElseThrow(() -> new AssertionError("no group"));
        helper.assertTrue(group.id().equals(LOTRRevival.id("hobbits")), "hobbit group " + group.id());
        int collect = 0;
        int total = 3000;
        for (int i = 0; i < total; i++) {
            QuestGiverData.Offer offer = OfferRoller.generate(helper.getLevel(), npc, group, random)
                    .orElseThrow(() -> new AssertionError("no offer generated"));
            helper.assertTrue(offer.ticksLeft() == QuestGiverData.OFFER_TICKS, "offer lifetime");
            helper.assertTrue(offer.count() >= 1, "offer count");
            if (offer.template().getPath().startsWith("hobbits/collect_")) {
                collect++;
            } else {
                helper.assertTrue(offer.template().equals(LOTRRevival.id("hobbits/kill_spiders")), "kill template");
            }
        }
        double share = collect / (double) total;
        helper.assertTrue(share > 0.51 && share < 0.60, "collect share about 10/18, got " + share);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ineligibleGiversNeverRoll(GameTestHelper helper) {
        ServerPlayer owner = player(helper, 4, 4);
        try {
            HobbitEntity npc = giver(helper, 2, 2);
            helper.assertTrue(QuestGivers.eligible(npc).isPresent(), "plain hobbit is eligible");

            npc.setRole(NpcRole.FARMER);
            helper.assertTrue(QuestGivers.eligible(npc).isEmpty(), "trader is not eligible");
            npc.setRole(NpcRole.VILLAGER);
            helper.assertTrue(QuestGivers.eligible(npc).isPresent(), "villager is eligible again");

            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 0, 5));
            zombie.setNoAi(true);
            npc.setTarget(zombie);
            helper.assertTrue(QuestGivers.eligible(npc).isEmpty(), "giver with a target is not eligible");
            npc.setTarget(null);
            helper.assertTrue(QuestGivers.eligible(npc).isPresent(), "idle again");

            UnitList list = NpcUnitData.list(NpcEntityTypes.HOBBIT_SHIRRIFF.get(), NpcRole.UNIT_TRADER)
                    .orElseThrow(() -> new AssertionError("unit list missing"));
            HiredUnits.hire(npc, owner, list, list.entry("bounder").orElseThrow(), "");
            helper.assertTrue(QuestGivers.eligible(npc).isEmpty(), "hired unit is not eligible");

            var bree = helper.spawn(NpcEntityTypes.BREE_MAN.get(), new BlockPos(6, 0, 6));
            helper.assertTrue(QuestGivers.eligible(bree).isEmpty(), "no group for Bree, no offers");
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    @GameTest(template = "empty")
    public static void groupWithGiversWinsThenLowestId(GameTestHelper helper) {
        QuestGroup open = new QuestGroup(
                Factions.HOBBIT, Optional.empty(), false, 0.0f, 1.0f, 0x112233, Optional.empty(), List.of());
        QuestGroup restricted = new QuestGroup(
                Factions.HOBBIT,
                Optional.empty(),
                false,
                0.0f,
                1.0f,
                0x112233,
                Optional.empty(),
                List.of(),
                Optional.of(HolderSet.direct(NpcEntityTypes.HOBBIT.get().builtInRegistryHolder())));
        List<QuestGivers.GroupMatch> candidates = List.of(
                new QuestGivers.GroupMatch(LOTRRevival.id("b_open"), open),
                new QuestGivers.GroupMatch(LOTRRevival.id("a_open"), open),
                new QuestGivers.GroupMatch(LOTRRevival.id("z_restricted"), restricted));
        helper.assertTrue(
                QuestGivers.pick(candidates, NpcEntityTypes.HOBBIT.get())
                        .orElseThrow()
                        .id()
                        .equals(LOTRRevival.id("z_restricted")),
                "group with givers wins");
        helper.assertTrue(
                QuestGivers.pick(candidates, NpcEntityTypes.HOBBIT_BOUNDER.get())
                        .orElseThrow()
                        .id()
                        .equals(LOTRRevival.id("a_open")),
                "other types take the lowest id of the open groups");
        helper.assertTrue(
                QuestGivers.pick(List.of(candidates.get(2)), NpcEntityTypes.HOBBIT_BOUNDER.get()).isEmpty(),
                "a restricted group does not match other types");

        // The givers field round-trips through the codec.
        RegistryOps<JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        JsonElement json = QuestGroup.CODEC.encodeStart(ops, restricted).getOrThrow();
        QuestGroup decoded = QuestGroup.CODEC.parse(ops, json).getOrThrow();
        helper.assertTrue(decoded.allowsGiver(NpcEntityTypes.HOBBIT.get()), "decoded group allows hobbit");
        helper.assertTrue(!decoded.allowsGiver(NpcEntityTypes.BREE_MAN.get()), "decoded group refuses Bree men");
        QuestGroup decodedOpen = QuestGroup.CODEC
                .parse(ops, QuestGroup.CODEC.encodeStart(ops, open).getOrThrow())
                .getOrThrow();
        helper.assertTrue(decodedOpen.givers().isEmpty(), "no givers field stays empty");
        helper.succeed();
    }

    // ------------------------------------------------------------------ lifetime

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void offerExpiresUnlessAViewerIsRegistered(GameTestHelper helper) {
        // Both stand on the template's own block: only chunks around the template are guaranteed to tick.
        HobbitEntity paused = giver(helper, 0.5, 0.5);
        HobbitEntity free = giver(helper, 0.5, 0.5);
        ServerPlayer viewer = player(helper, 0.5, 0.5);
        OfferService.setOffer(paused, breadOffer(2));
        OfferService.setOffer(free, breadOffer(2));
        OfferViewers.add(paused.getUUID(), viewer.getUUID());
        helper.runAfterDelay(10, () -> {
            try {
                helper.assertTrue(OfferService.offerOf(free).isEmpty(), "offer without a viewer expired");
                helper.assertTrue(
                        OfferService.offerOf(paused).map(QuestGiverData.Offer::ticksLeft).orElse(-1) == 2,
                        "offer with a viewer did not age");
                OfferViewers.remove(paused.getUUID(), viewer.getUUID());
            } catch (RuntimeException e) {
                remove(helper, viewer);
                throw e;
            }
        });
        helper.runAfterDelay(20, () -> {
            try {
                helper.assertTrue(OfferService.offerOf(paused).isEmpty(), "offer expired after the viewer left");
                helper.succeed();
            } finally {
                remove(helper, viewer);
            }
        });
    }

    @GameTest(template = "empty")
    public static void viewerFarAwayStopsPausingTheOffer(GameTestHelper helper) {
        HobbitEntity npc = giver(helper, 2, 2);
        ServerPlayer viewer = player(helper, 2, 3);
        try {
            OfferService.setOffer(npc, breadOffer(5));
            OfferViewers.add(npc.getUUID(), viewer.getUUID());
            Vec3 far = helper.absoluteVec(new Vec3(2, 0, 40));
            viewer.setPos(far.x, far.y, far.z);
            OfferTicker.tick(helper.getLevel(), npc);
            helper.assertTrue(!OfferViewers.has(npc.getUUID()), "a viewer beyond 16 blocks is dropped");
            helper.assertTrue(
                    OfferService.offerOf(npc).map(QuestGiverData.Offer::ticksLeft).orElse(-1) == 4,
                    "the offer ages again");
            helper.succeed();
        } finally {
            remove(helper, viewer);
        }
    }

    // ------------------------------------------------------------------ interaction and accept

    @GameTest(template = "empty")
    public static void acceptPathGivesQuestAndSecondPlayerGetsTaken(GameTestHelper helper) {
        HobbitEntity npc = giver(helper, 2, 2);
        ServerPlayer a = player(helper, 2, 3);
        ServerPlayer b = player(helper, 3, 2);
        try {
            OfferService.setOffer(npc, breadOffer(QuestGiverData.OFFER_TICKS));
            helper.assertTrue(QuestInteraction.handle(npc, a), "talk with an offer opens the screen");
            helper.assertTrue(QuestInteraction.handle(npc, b), "both players can open it");
            helper.assertTrue(OfferViewers.isViewing(npc.getUUID(), a.getUUID()), "a is a viewer");
            helper.assertTrue(OfferViewers.isViewing(npc.getUUID(), b.getUUID()), "b is a viewer");
            helper.assertTrue(!npc.isPersistenceRequired(), "not persistent before accept");

            OfferService.AcceptOutcome stale = OfferService.accept(a, npc.getId(), BREAD, 6);
            helper.assertTrue(stale == OfferService.AcceptOutcome.TAKEN, "a screen with another count is stale: " + stale);

            OfferService.AcceptOutcome first = OfferService.accept(a, npc.getId(), BREAD, 5);
            helper.assertTrue(first == OfferService.AcceptOutcome.ACCEPTED, "a accepts: " + first);
            QuestInstance quest = Quests.get(a).active().get(0);
            helper.assertTrue(quest.giver().equals(npc.getUUID()), "quest is from the giver");
            helper.assertTrue(quest.count() == 5, "count from the offer");
            helper.assertTrue(Quests.get(a).tracked().equals(Optional.of(quest.uuid())), "auto-tracked");
            helper.assertTrue(OfferService.offerOf(npc).isEmpty(), "offer removed");
            helper.assertTrue(npc.isPersistenceRequired(), "giver is persistent");
            helper.assertTrue(
                    OfferService.data(npc).holders().contains(a.getUUID()), "giver lists a as a holder");
            helper.assertTrue(!OfferViewers.has(npc.getUUID()), "viewers cleared");

            OfferService.AcceptOutcome second = OfferService.accept(b, npc.getId(), BREAD, 5);
            helper.assertTrue(second == OfferService.AcceptOutcome.TAKEN, "b gets taken: " + second);
            helper.assertTrue(Quests.get(b).active().isEmpty(), "b has no quest");
            helper.succeed();
        } finally {
            remove(helper, a, b);
        }
    }

    @GameTest(template = "empty")
    public static void acceptIsValidatedOnTheServer(GameTestHelper helper) {
        // Entities sit outside the 1x1 template's chunk; wait until that space ticks.
        TestArea.whenTicking(helper, 8, () -> acceptIsValidatedOnTheServerBody(helper));
    }

    private static void acceptIsValidatedOnTheServerBody(GameTestHelper helper) {
        HobbitEntity npc = giver(helper, 2, 2);
        ServerPlayer near = player(helper, 2, 3);
        ServerPlayer far = player(helper, 2, 20);
        try {
            OfferService.setOffer(npc, breadOffer(QuestGiverData.OFFER_TICKS));
            OfferService.AcceptOutcome tooFar = OfferService.accept(far, npc.getId(), BREAD, 5);
            helper.assertTrue(tooFar == OfferService.AcceptOutcome.TOO_FAR, "beyond 8 blocks: " + tooFar);
            helper.assertTrue(Quests.get(far).active().isEmpty(), "no quest for the far player");
            helper.assertTrue(OfferService.offerOf(npc).isPresent(), "offer untouched");

            OfferService.AcceptOutcome gone = OfferService.accept(near, 987_654, BREAD, 5);
            helper.assertTrue(gone == OfferService.AcceptOutcome.NO_GIVER, "unknown entity: " + gone);

            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 0, 3));
            zombie.setNoAi(true);
            npc.setTarget(zombie);
            OfferService.AcceptOutcome busy = OfferService.accept(near, npc.getId(), BREAD, 5);
            helper.assertTrue(busy == OfferService.AcceptOutcome.BUSY, "busy giver: " + busy);
            helper.assertTrue(OfferService.offerOf(npc).isPresent(), "a busy giver keeps its offer");
            npc.setTarget(null);

            // A private offer is for one player only.
            OfferService.setOffer(
                    npc,
                    new QuestGiverData.Offer(
                            BREAD, 5, 5.0f, 10, QuestGiverData.OFFER_TICKS, Optional.of(UUID.randomUUID())));
            OfferService.AcceptOutcome notYours = OfferService.accept(near, npc.getId(), BREAD, 5);
            helper.assertTrue(notYours == OfferService.AcceptOutcome.NOT_FOR_YOU, "private offer: " + notYours);
            helper.succeed();
        } finally {
            remove(helper, near, far);
        }
    }

    @GameTest(template = "empty")
    public static void fiveLiveQuestsGetTheTooManyLine(GameTestHelper helper) {
        HobbitEntity npc = giver(helper, 2, 2);
        ServerPlayer player = player(helper, 2, 3);
        List<String> said = new ArrayList<>();
        var previous = OfferDialogue.setObserver(line -> said.add(line.key()));
        try {
            for (int i = 0; i < 4; i++) {
                startBread(player, UUID.randomUUID(), 5);
            }
            OfferService.setOffer(npc, breadOffer(QuestGiverData.OFFER_TICKS));
            helper.assertTrue(QuestInteraction.handle(npc, player), "four quests: screen opens");
            helper.assertTrue(OfferViewers.isViewing(npc.getUUID(), player.getUUID()), "viewer registered");
            OfferViewers.removePlayer(player.getUUID());

            startBread(player, UUID.randomUUID(), 5);
            said.clear();
            helper.assertTrue(QuestInteraction.handle(npc, player), "five quests: the giver answers");
            helper.assertTrue(said.equals(List.of(OfferDialogue.TOO_MANY)), "too many line, got " + said);
            helper.assertTrue(!OfferViewers.has(npc.getUUID()), "no screen opened");
            helper.assertTrue(OfferService.offerOf(npc).isPresent(), "offer stays");
            helper.succeed();
        } finally {
            OfferDialogue.setObserver(previous);
            remove(helper, player);
        }
    }

    @GameTest(template = "empty")
    public static void ownQuestShowsProgressLineAndTracks(GameTestHelper helper) {
        HobbitEntity npc = giver(helper, 2, 2);
        ServerPlayer player = player(helper, 2, 3);
        List<String> said = new ArrayList<>();
        var previous = OfferDialogue.setObserver(line -> said.add(line.key()));
        try {
            startBread(player, npc.getUUID(), 5);
            startBread(player, UUID.randomUUID(), 5);
            UUID mine = Quests.get(player).active().get(0).uuid();
            helper.assertTrue(Quests.get(player).tracked().equals(Optional.of(Quests.get(player).active().get(1).uuid())),
                    "the latest quest is tracked");
            player.getInventory().add(new ItemStack(Items.BREAD, 2));
            helper.assertTrue(QuestInteraction.handle(npc, player), "own quest takes over");
            helper.assertTrue(Quests.get(player).find(mine).orElseThrow().progress() == 2, "two bread handed in");
            helper.assertTrue(Quests.get(player).tracked().equals(Optional.of(mine)), "it became the tracked quest");
            helper.assertTrue(
                    said.size() == 1 && said.get(0).startsWith("quest.lotrrevival.offer.progress."),
                    "progress line, got " + said);
            helper.succeed();
        } finally {
            OfferDialogue.setObserver(previous);
            remove(helper, player);
        }
    }

    // ------------------------------------------------------------------ coins and first completion

    @GameTest(template = "empty")
    public static void completionDropsCoinsAtGiverAndAwardsOnce(GameTestHelper helper) {
        HobbitEntity npc = giver(helper, 2, 2);
        ServerPlayer player = player(helper, 2, 3);
        List<ResourceLocation> toasts = new ArrayList<>();
        AchievementService.Sink previous = AchievementService.setSink(new AchievementService.Sink() {
            @Override
            public void toast(ServerPlayer p, ResourceLocation id) {
                toasts.add(id);
            }

            @Override
            public void broadcast(ServerPlayer p, Component line) {}
        });
        List<String> said = new ArrayList<>();
        var previousDialogue = OfferDialogue.setObserver(line -> said.add(line.key()));
        try {
            startBread(player, npc.getUUID(), 3);
            player.getInventory().add(new ItemStack(Items.BREAD, 3));
            helper.assertTrue(QuestInteraction.handle(npc, player), "hand-in takes over");
            helper.assertTrue(Quests.get(player).active().isEmpty(), "quest completed");
            helper.assertTrue(
                    said.size() == 1 && said.get(0).startsWith("quest.lotrrevival.offer.thanks."),
                    "thank-you line, got " + said);

            QuestRecord record = Quests.get(player).log().get(Quests.get(player).log().size() - 1);
            int coins = record.rewards().coins();
            helper.assertTrue(coins > 0, "coins rolled");
            int onGround = 0;
            for (ItemEntity item : helper.getLevel()
                    .getEntitiesOfClass(ItemEntity.class, npc.getBoundingBox().inflate(4.0))) {
                int value = Coins.value(item.getItem());
                if (value > 0) {
                    onGround += value;
                    helper.assertTrue(
                            Math.abs(item.getY() - (npc.getY() + 0.5)) < 0.01,
                            "coin dropped 0.5 blocks above the giver, y=" + item.getY());
                }
            }
            helper.assertTrue(onGround == coins, "coins on the ground " + onGround + " vs paid " + coins);
            helper.assertTrue(Coins.count(player) == 0, "nothing went into the inventory");

            AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(FIRST_ERRAND);
            helper.assertTrue(holder != null, "first-completion advancement exists");
            helper.assertTrue(player.getAdvancements().getOrStartProgress(holder).isDone(), "advancement done");
            helper.assertTrue(
                    toasts.stream().filter(FIRST_ERRAND::equals).count() == 1, "awarded once, toasts " + toasts);

            // A second completion neither re-awards nor changes the advancement.
            UUID otherGiver = UUID.randomUUID();
            startBread(player, otherGiver, 2);
            UUID second = Quests.get(player).active().get(0).uuid();
            helper.assertTrue(Quests.complete(player, second, true).isPresent(), "second quest completed");
            helper.assertTrue(
                    toasts.stream().filter(FIRST_ERRAND::equals).count() == 1, "still awarded once, toasts " + toasts);
            helper.assertTrue(!FirstCompletion.award(player, LOTRRevival.id("hobbits")), "nothing left to award");
            helper.succeed();
        } finally {
            OfferDialogue.setObserver(previousDialogue);
            AchievementService.setSink(previous);
            remove(helper, player);
        }
    }

    @GameTest(template = "empty")
    public static void coinsGoToThePlayerWhenTheGiverIsFarAway(GameTestHelper helper) {
        ServerPlayer player = player(helper, 2, 3);
        try {
            startBread(player, UUID.randomUUID(), 3);
            UUID id = Quests.get(player).active().get(0).uuid();
            QuestRecord record = Quests.complete(player, id, true).orElseThrow();
            helper.assertTrue(record.rewards().coins() > 0, "coins rolled");
            helper.assertTrue(
                    Coins.count(player) == record.rewards().coins(),
                    "coins " + Coins.count(player) + " went to the player, paid " + record.rewards().coins());
            helper.succeed();
        } finally {
            remove(helper, player);
        }
    }

    // ------------------------------------------------------------------ giver death and position

    @GameTest(template = "empty")
    public static void killingTheGiverFailsTheQuestAndClearsTheOffer(GameTestHelper helper) {
        HobbitEntity npc = giver(helper, 2, 2);
        ServerPlayer player = player(helper, 2, 3);
        try {
            startBread(player, npc.getUUID(), 5);
            UUID quest = Quests.get(player).active().get(0).uuid();
            OfferService.setOffer(npc, breadOffer(QuestGiverData.OFFER_TICKS));
            OfferViewers.add(npc.getUUID(), player.getUUID());

            npc.hurt(npc.damageSources().genericKill(), 1000.0f);

            helper.assertTrue(!npc.isAlive(), "the giver died");
            QuestInstance failed = Quests.get(player).find(quest).orElseThrow();
            helper.assertTrue(failed.failed(), "quest failed");
            helper.assertTrue(failed.failReason().equals(Optional.of(FailReason.GIVER_DIED)), "reason giver_died");
            helper.assertTrue(OfferService.offerOf(npc).isEmpty(), "offer cleared");
            helper.assertTrue(!OfferViewers.has(npc.getUUID()), "viewers cleared");
            helper.assertTrue(
                    DeadGivers.get(helper.getLevel().getServer()).contains(npc.getUUID()), "remembered as dead");
            helper.succeed();
        } finally {
            remove(helper, player);
        }
    }

    @GameTest(template = "empty")
    public static void giverPositionRefreshesOnlyAfterMovingFar(GameTestHelper helper) {
        HobbitEntity npc = giver(helper, 2, 2);
        ServerPlayer player = player(helper, 2, 3);
        try {
            startBread(player, npc.getUUID(), 5);
            UUID quest = Quests.get(player).active().get(0).uuid();
            var dimension = helper.getLevel().dimension();
            GlobalPos start = GlobalPos.of(dimension, new BlockPos(0, 64, 0));
            Quests.updateGiverPos(player, npc.getUUID(), start);

            helper.assertTrue(
                    !Quests.updateGiverPos(player, npc.getUUID(), GlobalPos.of(dimension, new BlockPos(10, 64, 0))),
                    "10 blocks is not far enough");
            helper.assertTrue(
                    Quests.get(player).find(quest).orElseThrow().giverPos().equals(Optional.of(start)),
                    "position unchanged");
            GlobalPos far = GlobalPos.of(dimension, new BlockPos(17, 64, 0));
            helper.assertTrue(Quests.updateGiverPos(player, npc.getUUID(), far), "17 blocks updates");
            helper.assertTrue(
                    Quests.get(player).find(quest).orElseThrow().giverPos().equals(Optional.of(far)),
                    "position updated");

            // The giver's own tick does it every 100 ticks.
            npc.tickCount = 100;
            OfferTicker.tick(helper.getLevel(), npc);
            helper.assertTrue(
                    Quests.get(player).find(quest).orElseThrow().giverPos().equals(Optional.of(
                            GlobalPos.of(dimension, npc.blockPosition()))),
                    "the tick refreshed the position");
            helper.succeed();
        } finally {
            remove(helper, player);
        }
    }

    // ------------------------------------------------------------------ command

    @GameTest(template = "empty")
    public static void offerCommandForcesAnOffer(GameTestHelper helper) {
        // Entities sit outside the 1x1 template's chunk; wait until that space ticks.
        TestArea.whenTicking(helper, 8, () -> offerCommandForcesAnOfferBody(helper));
    }

    private static void offerCommandForcesAnOfferBody(GameTestHelper helper) {
        HobbitEntity npc = giver(helper, 2, 2);
        var server = helper.getLevel().getServer();
        server.getCommands()
                .performPrefixedCommand(
                        server.createCommandSourceStack().withPermission(2), "lotrquest offer " + npc.getUUID());
        QuestGiverData.Offer offer =
                OfferService.offerOf(npc).orElseThrow(() -> new AssertionError("command made no offer"));
        helper.assertTrue(offer.template().getPath().startsWith("hobbits/"), "offer from the hobbit group");
        helper.assertTrue(offer.ticksLeft() == QuestGiverData.OFFER_TICKS, "fresh lifetime");
        helper.succeed();
    }
}
