package io.github.sethwillemsvg.lotrrevival.progression.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.npc.entity.HobbitEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementService;
import io.github.sethwillemsvg.lotrrevival.progression.bounty.Bounties;
import io.github.sethwillemsvg.lotrrevival.progression.bounty.BountyData;
import io.github.sethwillemsvg.lotrrevival.progression.bounty.BountyEvents;
import io.github.sethwillemsvg.lotrrevival.progression.bounty.BountyOffers;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGiverData;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestRecord;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestStatus;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferDialogue;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferKind;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferService;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferViewers;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.QuestGivers;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.QuestInteraction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Bounty offers: generation, accept rules, fixed payout, the target notice and the hint. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BountyOfferTest {
    private static final ResourceLocation TEMPLATE = LOTRRevival.id("hobbits/bounty");
    private static final ResourceLocation HOBBIT = Factions.HOBBIT.location();

    private BountyOfferTest() {}

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

    private static Optional<QuestGiverData.Offer> generate(
            GameTestHelper helper, HobbitEntity npc, RandomSource random) {
        QuestGivers.GroupMatch group = QuestGivers.eligible(npc).orElseThrow(() -> new AssertionError("no group"));
        OfferKind kind = new BountyOffers.Kind();
        return kind.generate(
                new OfferKind.Context(helper.getLevel(), npc, group, random),
                TEMPLATE,
                Quests.template(helper.getLevel().registryAccess(), TEMPLATE).orElseThrow().value());
    }

    private static void markTarget(MinecraftServer server, UUID target, int kills) {
        BountyData.get(server).clearKills(target, null);
        BountyData.get(server).addKills(HOBBIT, target, Bounties.now(server), kills);
    }

    private static void cleanup(GameTestHelper helper, MinecraftServer server, ServerPlayer target, ServerPlayer... others) {
        BountyData data = BountyData.get(server);
        data.clearKills(target.getUUID(), null);
        data.takeNotices(target.getUUID());
        remove(helper, target);
        remove(helper, others);
    }

    @GameTest(template = "empty")
    public static void noOfferWithoutAValidTargetAndFixedValuesWithOne(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer target = player(helper, 4, 4);
        HobbitEntity npc = giver(helper, 2, 2);
        try {
            markTarget(server, target.getUUID(), 24);
            helper.assertTrue(generate(helper, npc, RandomSource.create(1L)).isEmpty(), "no offer at 24 kills");
            markTarget(server, target.getUUID(), 25);
            QuestGiverData.Offer offer = generate(helper, npc, RandomSource.create(2L))
                    .orElseThrow(() -> new AssertionError("offer expected at 25 kills"));
            helper.assertTrue(offer.bountyTarget().equals(Optional.of(target.getUUID())), "names the target");
            helper.assertTrue(offer.bountyTargetName().isPresent(), "has the target name");
            helper.assertTrue(offer.count() == 25, "count is the live records: " + offer.count());
            helper.assertTrue(offer.alignmentBase() == 25.0f, "alignment base v = 25: " + offer.alignmentBase());
            helper.assertTrue(
                    offer.coinBase() >= 188 && offer.coinBase() <= 313, "coins 25 x 10 +-25%: " + offer.coinBase());
            helper.assertTrue(offer.forPlayer().isEmpty(), "public offer");
            markTarget(server, target.getUUID(), 60);
            QuestGiverData.Offer big = generate(helper, npc, RandomSource.create(3L)).orElseThrow();
            helper.assertTrue(big.alignmentBase() == 50.0f, "v capped at 50");
        } finally {
            cleanup(helper, server, target);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void acceptNeedsPlus100NotTheTargetAndNoSecondBountyOnTheTarget(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer target = player(helper, 12, 12);
        ServerPlayer hunter = player(helper, 3, 2);
        HobbitEntity npc = giver(helper, 2, 2);
        HobbitEntity other = giver(helper, 2, 5);
        AlignmentSource source = AlignmentSource.command();
        try {
            markTarget(server, target.getUUID(), 30);
            QuestGiverData.Offer offer = generate(helper, npc, RandomSource.create(4L)).orElseThrow();
            OfferService.setOffer(npc, offer);

            Alignment.set(hunter, Factions.HOBBIT, 99.0f, source);
            helper.assertTrue(
                    OfferService.accept(hunter, npc.getId(), TEMPLATE, offer.count())
                            == OfferService.AcceptOutcome.REFUSED,
                    "refused at +99");
            helper.assertTrue(Quests.get(hunter).active().isEmpty(), "no quest at +99");
            helper.assertTrue(OfferService.offerOf(npc).isPresent(), "offer stays after a refusal");

            Alignment.set(target, Factions.HOBBIT, 500.0f, source);
            target.setPos(npc.position());
            helper.assertTrue(
                    OfferService.accept(target, npc.getId(), TEMPLATE, offer.count())
                            == OfferService.AcceptOutcome.REFUSED,
                    "the target cannot accept their own bounty");

            Alignment.set(hunter, Factions.HOBBIT, 100.0f, source);
            helper.assertTrue(
                    OfferService.accept(hunter, npc.getId(), TEMPLATE, offer.count())
                            == OfferService.AcceptOutcome.ACCEPTED,
                    "accepted at +100");
            QuestInstance quest = Quests.get(hunter).active().get(0);
            helper.assertTrue(
                    quest.objectiveData().bountyTarget().equals(Optional.of(target.getUUID())), "target kept");
            helper.assertTrue(quest.count() == 1, "a bounty hunts one player");
            helper.assertTrue(quest.alignmentBase() == offer.alignmentBase(), "fixed alignment base");
            helper.assertTrue(quest.coinBase() == offer.coinBase(), "fixed coin base");
            helper.assertTrue(quest.state() == QuestStatus.ACTIVE, "active");
            helper.assertTrue(BountyData.get(server).hasNotices(target.getUUID()), "target notice queued");

            // A second bounty on the same target is refused, from another giver.
            QuestGiverData.Offer second = generate(helper, other, RandomSource.create(5L)).orElseThrow();
            OfferService.setOffer(other, second);
            helper.assertTrue(
                    OfferService.accept(hunter, other.getId(), TEMPLATE, second.count())
                            == OfferService.AcceptOutcome.REFUSED,
                    "no other live bounty on the same target");
        } finally {
            cleanup(helper, server, target, hunter);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void acceptedBountyPaysTheFixedCoinsAndNoticeArrivesOnLogin(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer target = player(helper, 12, 12);
        ServerPlayer hunter = player(helper, 3, 2);
        HobbitEntity npc = giver(helper, 2, 2);
        List<Integer> paid = new ArrayList<>();
        Quests.CoinPayout previous = Quests.setCoinPayout((p, coins, g) -> paid.add(coins));
        try {
            markTarget(server, target.getUUID(), 40);
            QuestGiverData.Offer offer = generate(helper, npc, RandomSource.create(6L)).orElseThrow();
            OfferService.setOffer(npc, offer);
            Alignment.set(hunter, Factions.HOBBIT, 150.0f, AlignmentSource.command());
            helper.assertTrue(
                    OfferService.accept(hunter, npc.getId(), TEMPLATE, offer.count())
                            == OfferService.AcceptOutcome.ACCEPTED,
                    "accepted");

            helper.assertTrue(BountyData.get(server).hasNotices(target.getUUID()), "notice queued for the target");
            BountyEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(target));
            helper.assertFalse(BountyData.get(server).hasNotices(target.getUUID()), "notice delivered on login");

            QuestInstance quest = Quests.get(hunter).active().get(0);
            helper.assertTrue(Quests.markObjectiveDone(hunter, quest.uuid()), "target killed");
            QuestRecord record =
                    Quests.complete(hunter, quest.uuid(), false, RandomSource.create(99L)).orElseThrow();
            helper.assertTrue(record.rewards().coins() == offer.coinBase(), "coins exactly " + offer.coinBase());
            helper.assertTrue(paid.equals(List.of(offer.coinBase())), "paid out once, no jackpot: " + paid);
            helper.assertTrue(record.rewards().alignment() == offer.alignmentBase(), "alignment exactly v");
        } finally {
            Quests.setCoinPayout(previous);
            cleanup(helper, server, target, hunter);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hintFiresAboutOneInThreeAndNamesTheLastBiome(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer target = player(helper, 12, 12);
        ServerPlayer hunter = player(helper, 3, 2);
        HobbitEntity npc = giver(helper, 2, 2);
        HobbitEntity bystander = giver(helper, 5, 5);
        List<OfferDialogue.Said> said = new ArrayList<>();
        Consumer<OfferDialogue.Said> previous = OfferDialogue.setObserver(said::add);
        try {
            helper.assertTrue(!QuestInteraction.handle(bystander, hunter), "no hint without a bounty");
            markTarget(server, target.getUUID(), 30);
            QuestGiverData.Offer offer = generate(helper, npc, RandomSource.create(7L)).orElseThrow();
            OfferService.setOffer(npc, offer);
            Alignment.set(hunter, Factions.HOBBIT, 150.0f, AlignmentSource.command());
            OfferService.accept(hunter, npc.getId(), TEMPLATE, offer.count());
            said.clear();

            BountyData.get(server).setLastBiome(target.getUUID(), ResourceLocation.withDefaultNamespace("plains"));
            int hints = 0;
            int talks = 600;
            for (int i = 0; i < talks; i++) {
                if (QuestInteraction.handle(bystander, hunter)) {
                    hints++;
                }
            }
            helper.assertTrue(
                    hints > talks / 3 - 80 && hints < talks / 3 + 80, "about 1 in 3: " + hints + "/" + talks);
            helper.assertTrue(said.size() == hints, "each hint is spoken once");
            helper.assertTrue(said.get(0).key().equals("bounty.lotrrevival.hint"), "hint line " + said.get(0).key());
        } finally {
            OfferDialogue.setObserver(previous);
            cleanup(helper, server, target, hunter);
        }
        helper.succeed();
    }
}
