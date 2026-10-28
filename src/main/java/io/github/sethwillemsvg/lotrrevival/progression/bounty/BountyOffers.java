package io.github.sethwillemsvg.lotrrevival.progression.bounty;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestGiverData;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestObjective;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestTemplate;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferDialogue;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferKind;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferKinds;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.OfferService;
import io.github.sethwillemsvg.lotrrevival.progression.quest.offer.QuestInteraction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Bounty quest offers: the offer kind (weight 4), the extra accept rules and the hint in the talk chain. The
 * target, the kill records and the resolution live in the rest of this package.
 */
public final class BountyOffers {
    /** Relative weight of bounty offers at the roll, among the kinds the group has templates for. */
    public static final int WEIGHT = 4;

    /** Alignment the hunter needs with the giver's faction. */
    public static final float MIN_ALIGNMENT = 100.0f;

    /** The hint fires on 1 in this many right-clicks. */
    public static final int HINT_ODDS = 3;

    private BountyOffers() {}

    /** Registers the kind, the accept rules and the hint step (called once from {@link BountySystem#init}). */
    public static void init() {
        OfferKinds.register(new Kind());
        OfferService.addAcceptCheck(BountyOffers::mayAccept);
        QuestInteraction.setBountyHintStep(BountyOffers::hint);
    }

    // ------------------------------------------------------------------ the offer kind

    /** The bounty offer kind. */
    public static final class Kind implements OfferKind {
        @Override
        public String id() {
            return "bounty";
        }

        @Override
        public int weight() {
            return WEIGHT;
        }

        @Override
        public boolean matches(QuestObjective objective) {
            return objective instanceof QuestObjective.Bounty;
        }

        @Override
        public Optional<QuestGiverData.Offer> generate(
                Context context, ResourceLocation templateId, QuestTemplate template) {
            MinecraftServer server = context.level().getServer();
            if (server == null || !ProgressionConfig.get(ProgressionConfig.BOUNTIES_ENABLED)) {
                return Optional.empty();
            }
            ResourceKey<Faction> faction = context.group().group().faction();
            List<UUID> targets = Bounties.validTargets(server, faction);
            if (targets.isEmpty()) {
                return Optional.empty();
            }
            UUID target = targets.get(context.random().nextInt(targets.size()));
            Optional<String> name = nameOf(server, target);
            if (name.isEmpty()) {
                return Optional.empty();
            }
            int records = Bounties.liveRecords(server, target, faction);
            return Optional.of(new QuestGiverData.Offer(
                    templateId,
                    records,
                    Bounties.bountyValue(records),
                    Bounties.rollCoins(records, context.random()),
                    QuestGiverData.OFFER_TICKS,
                    Optional.empty(),
                    Optional.of(target),
                    name));
        }
    }

    /** The name of a player, online or not; empty when the server has never seen them. */
    static Optional<String> nameOf(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            return Optional.of(online.getGameProfile().getName());
        }
        return server.getProfileCache().get(player).map(GameProfile::getName);
    }

    // ------------------------------------------------------------------ accept rules

    /**
     * On top of the normal accept rules: +100 alignment with the giver's faction, not the target, and no other live
     * bounty on the same target. Offers without a bounty target pass.
     */
    static boolean mayAccept(ServerPlayer player, NpcEntity giver, QuestGiverData.Offer offer) {
        if (offer.bountyTarget().isEmpty()) {
            return true;
        }
        UUID target = offer.bountyTarget().get();
        if (target.equals(player.getUUID())) {
            return false;
        }
        if (Alignment.get(player, giver.faction()) < MIN_ALIGNMENT) {
            return false;
        }
        return liveBountyOn(player, target).isEmpty();
    }

    /** The player's live (not failed) bounty quest on {@code target}, if any. */
    static Optional<QuestInstance> liveBountyOn(ServerPlayer player, UUID target) {
        for (QuestInstance quest : Quests.get(player).active()) {
            if (quest.live() && quest.objectiveData().bountyTarget().filter(target::equals).isPresent()) {
                return Optional.of(quest);
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ hint

    /**
     * Step 4 of the talk chain: on 1 in {@value #HINT_ODDS} right-clicks on an NPC of a faction the player holds a
     * live bounty for, the NPC names the target's last known biome. Returns true when it spoke.
     */
    static boolean hint(NpcEntity npc, ServerPlayer player) {
        MinecraftServer server = npc.getServer();
        if (server == null || !ProgressionConfig.get(ProgressionConfig.BOUNTIES_ENABLED)) {
            return false;
        }
        Optional<QuestInstance> held = Quests.get(player).active().stream()
                .filter(q -> q.live()
                        && q.objectiveData().bountyTarget().isPresent()
                        && q.giverFaction().equals(npc.faction().location()))
                .findFirst();
        if (held.isEmpty() || npc.getRandom().nextInt(HINT_ODDS) != 0) {
            return false;
        }
        QuestInstance quest = held.get();
        UUID target = quest.objectiveData().bountyTarget().get();
        String name = quest.objectiveData().bountyTargetName().orElse("?");
        Optional<ResourceLocation> biome = BountyData.get(server).lastBiome(target);
        if (biome.isPresent()) {
            OfferDialogue.say(
                    npc,
                    player,
                    "bounty.lotrrevival.hint",
                    name,
                    Component.translatable(Util.makeDescriptionId("biome", biome.get())));
        } else {
            OfferDialogue.say(npc, player, "bounty.lotrrevival.hint.unknown", name);
        }
        return true;
    }
}
