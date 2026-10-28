package io.github.sethwillemsvg.lotrrevival.progression.quest;

import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentRules;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionSync;
import io.github.sethwillemsvg.lotrrevival.progression.quest.event.QuestAcceptedEvent;
import io.github.sethwillemsvg.lotrrevival.progression.quest.event.QuestCompletedEvent;
import io.github.sethwillemsvg.lotrrevival.progression.quest.event.QuestFailedEvent;
import io.github.sethwillemsvg.lotrrevival.progression.quest.event.QuestProgressEvent;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.ProgressionTriggers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Server API of the mini-quest system. Everything that changes a player's {@code quests} attachment goes through
 * here so the change is synced and the lifecycle events fire. Offers, giver interaction and the book screens are
 * later tickets; they call these methods.
 */
public final class Quests {
    /** Giver id used by {@code /lotrquest give}. */
    public static final UUID PLACEHOLDER_GIVER = new UUID(0L, 0L);

    /** Why {@link #canAccept} refused. */
    public enum AcceptResult {
        OK,
        DISABLED,
        UNKNOWN_TEMPLATE,
        ALREADY_HAS_QUEST_FROM_GIVER,
        FACTION_CAP,
        TOTAL_CAP,
        HOSTILE,
        ALIGNMENT_TOO_LOW
    }

    /** Result of a hand-in or turn-in attempt. */
    public record TurnIn(int taken, boolean completed) {
        public static final TurnIn NOTHING = new TurnIn(0, false);
    }

    /** Pays out coins. Core has no coin API yet, so the default does nothing. */
    @FunctionalInterface
    public interface CoinPayout {
        void pay(ServerPlayer player, int coins, UUID giver);
    }

    // TODO(core coin API): pay out coin items of the largest denominations, dropped at the giver. Until then the
    // coins are rolled and recorded in the QuestRecord only.
    private static volatile CoinPayout coinPayout = (player, coins, giver) -> {};

    private Quests() {}

    /** Replaces the payout (null pays nothing) and returns the previous one, so tests can restore it. */
    public static CoinPayout setCoinPayout(CoinPayout payout) {
        CoinPayout previous = coinPayout;
        coinPayout = payout == null ? (player, coins, giver) -> {} : payout;
        return previous;
    }

    // ------------------------------------------------------------------ reads

    public static PlayerQuests get(net.minecraft.world.entity.player.Player player) {
        return player.getData(ProgressionAttachments.QUESTS);
    }

    public static Optional<Holder.Reference<QuestTemplate>> template(RegistryAccess access, ResourceLocation id) {
        return access.registry(QuestTemplate.REGISTRY).flatMap(registry -> registry.getHolder(id));
    }

    public static Optional<QuestGroup> group(RegistryAccess access, ResourceLocation id) {
        return access.registry(QuestGroup.REGISTRY)
                .flatMap(registry -> registry.getHolder(id))
                .map(Holder::value);
    }

    // ------------------------------------------------------------------ accepting

    /**
     * Whether the player may take a quest from this giver: feature on, template known, no live quest from the giver,
     * below both caps (failed quests do not count), faction not hostile, alignment at the group minimum.
     */
    public static AcceptResult canAccept(
            ServerPlayer player, ResourceLocation templateId, UUID giver, ResourceLocation giverFaction) {
        if (!ProgressionConfig.get(ProgressionConfig.MINI_QUESTS_ENABLED)) {
            return AcceptResult.DISABLED;
        }
        RegistryAccess access = player.level().registryAccess();
        Optional<QuestGroup> group = template(access, templateId)
                .flatMap(template -> group(access, template.value().group().location()));
        if (group.isEmpty()) {
            return AcceptResult.UNKNOWN_TEMPLATE;
        }
        PlayerQuests quests = get(player);
        if (quests.hasLiveFrom(giver)) {
            return AcceptResult.ALREADY_HAS_QUEST_FROM_GIVER;
        }
        if (quests.liveCount(giverFaction) >= ProgressionConfig.get(ProgressionConfig.MAX_ACTIVE_QUESTS_PER_FACTION)) {
            return AcceptResult.FACTION_CAP;
        }
        if (quests.liveCount() >= ProgressionConfig.get(ProgressionConfig.MAX_ACTIVE_QUESTS_TOTAL)) {
            return AcceptResult.TOTAL_CAP;
        }
        ResourceKey<Faction> faction = group.get().faction();
        if (Alignment.rank(player, faction).id().equals(AlignmentRules.HOSTILE.id())) {
            return AcceptResult.HOSTILE;
        }
        if (Alignment.get(player, faction) < group.get().minAlignment()) {
            return AcceptResult.ALIGNMENT_TOO_LOW;
        }
        return AcceptResult.OK;
    }

    /** {@link #canAccept} and, when it passes, {@link #start}. */
    public static AcceptResult accept(
            ServerPlayer player,
            ResourceLocation templateId,
            int count,
            UUID giver,
            Component giverName,
            ResourceLocation giverFaction,
            Optional<GlobalPos> giverPos) {
        AcceptResult result = canAccept(player, templateId, giver, giverFaction);
        if (result != AcceptResult.OK) {
            return result;
        }
        return start(player, templateId, count, giver, giverName, giverFaction, giverPos).isPresent()
                ? AcceptResult.OK
                : AcceptResult.UNKNOWN_TEMPLATE;
    }

    /**
     * Starts a quest without checking caps or alignment (callers check {@link #canAccept}; ops bypass it). Fixes the
     * base values from {@code count}, tracks the new quest and posts {@link QuestAcceptedEvent}.
     *
     * @return the new instance, empty if the template does not exist
     */
    public static Optional<QuestInstance> start(
            ServerPlayer player,
            ResourceLocation templateId,
            int count,
            UUID giver,
            Component giverName,
            ResourceLocation giverFaction,
            Optional<GlobalPos> giverPos) {
        return start(player, templateId, count, giver, giverName, giverFaction, giverPos, ObjectiveData.EMPTY);
    }

    /** {@link #start} with objective data (the bounty target); the base values come from the template. */
    public static Optional<QuestInstance> start(
            ServerPlayer player,
            ResourceLocation templateId,
            int count,
            UUID giver,
            Component giverName,
            ResourceLocation giverFaction,
            Optional<GlobalPos> giverPos,
            ObjectiveData objectiveData) {
        return start(
                player, templateId, count, giver, giverName, giverFaction, giverPos, objectiveData, Optional.empty());
    }

    /**
     * {@link #start} with objective data and, optionally, base values fixed by the caller (bounties fix theirs when
     * the offer appears). {@code fixedBases} holds {alignment base, coin base}.
     */
    public static Optional<QuestInstance> start(
            ServerPlayer player,
            ResourceLocation templateId,
            int count,
            UUID giver,
            Component giverName,
            ResourceLocation giverFaction,
            Optional<GlobalPos> giverPos,
            ObjectiveData objectiveData,
            Optional<float[]> fixedBases) {
        RegistryAccess access = player.level().registryAccess();
        Optional<Holder.Reference<QuestTemplate>> holder = template(access, templateId);
        if (holder.isEmpty()) {
            return Optional.empty();
        }
        QuestTemplate template = holder.get().value();
        int n = Math.max(1, count);
        float alignmentBase = fixedBases.map(b -> b[0]).orElseGet(() -> template.alignmentBase(n));
        int coinBase = fixedBases.map(b -> Math.round(b[1])).orElseGet(() -> template.coinBase(n));
        Optional<ResourceLocation> biome =
                player.level().getBiome(player.blockPosition()).unwrapKey().map(ResourceKey::location);
        QuestInstance instance = new QuestInstance(
                UUID.randomUUID(),
                templateId,
                template.group().location(),
                giver,
                giverName,
                giverFaction,
                giverPos,
                player.level().getGameTime(),
                biome,
                n,
                alignmentBase,
                coinBase,
                0,
                QuestStatus.ACTIVE,
                Optional.empty(),
                objectiveData);
        store(player, get(player).withAdded(instance).withTracked(Optional.of(instance.uuid())));
        updateHolder(player.getServer(), giver, player.getUUID(), true);
        NeoForge.EVENT_BUS.post(new QuestAcceptedEvent(player, instance));
        return Optional.of(instance);
    }

    // ------------------------------------------------------------------ progress

    /** Raises progress by {@code amount} (capped at the count); a full kill-type quest becomes ready to turn in. */
    private static QuestInstance advance(ServerPlayer player, QuestInstance instance, int amount) {
        int before = instance.progress();
        int after = Math.min(instance.count(), before + amount);
        if (after <= before) {
            return instance;
        }
        QuestStatus status = after >= instance.count() ? QuestStatus.READY_TO_TURN_IN : instance.state();
        QuestInstance next = instance.withProgress(after, status);
        store(player, get(player).replace(next));
        NeoForge.EVENT_BUS.post(new QuestProgressEvent(player, next, before));
        return next;
    }

    private static Optional<QuestObjective> objective(RegistryAccess access, QuestInstance instance) {
        return template(access, instance.template()).map(holder -> holder.value().objective());
    }

    /**
     * A player (or a unit credited to them) killed a faction NPC: counts for every active {@code kill_faction}
     * quest that matches the victim's faction.
     */
    public static void onFactionKill(ServerPlayer player, ResourceKey<Faction> victimFaction) {
        RegistryAccess access = player.level().registryAccess();
        for (QuestInstance instance : get(player).active()) {
            if (instance.state() != QuestStatus.ACTIVE) {
                continue;
            }
            Optional<QuestObjective> objective = objective(access, instance);
            if (objective.isPresent()
                    && objective.get() instanceof QuestObjective.KillFaction kill
                    && kill.matches(access, victimFaction)) {
                advance(player, instance, 1);
            }
        }
    }

    /** A player (or a unit credited to them) killed an entity: counts for matching {@code kill_entity} quests. */
    public static void onEntityKill(ServerPlayer player, EntityType<?> victim) {
        RegistryAccess access = player.level().registryAccess();
        for (QuestInstance instance : get(player).active()) {
            if (instance.state() != QuestStatus.ACTIVE) {
                continue;
            }
            Optional<QuestObjective> objective = objective(access, instance);
            if (objective.isPresent()
                    && objective.get() instanceof QuestObjective.KillEntity kill
                    && kill.matches(victim)) {
                advance(player, instance, 1);
            }
        }
    }

    /**
     * Collect hand-in: takes matching items, the held slot first and then the inventory in order, up to what is
     * still owed. Partial hand-ins are kept. Completes the quest when the count is reached.
     */
    public static TurnIn handIn(ServerPlayer player, UUID questId) {
        return handIn(player, questId, player.getRandom());
    }

    public static TurnIn handIn(ServerPlayer player, UUID questId, RandomSource random) {
        Optional<QuestInstance> found = get(player).find(questId);
        if (found.isEmpty() || found.get().state() != QuestStatus.ACTIVE) {
            return TurnIn.NOTHING;
        }
        QuestInstance instance = found.get();
        Optional<QuestObjective> objective = objective(player.level().registryAccess(), instance);
        if (objective.isEmpty() || !(objective.get() instanceof QuestObjective.Collect collect)) {
            return TurnIn.NOTHING;
        }
        int owed = instance.count() - instance.progress();
        Inventory inventory = player.getInventory();
        int taken = 0;
        int selected = inventory.selected;
        taken += take(inventory.items.get(selected), collect, owed - taken);
        for (int slot = 0; slot < inventory.items.size() && taken < owed; slot++) {
            if (slot != selected) {
                taken += take(inventory.items.get(slot), collect, owed - taken);
            }
        }
        for (int slot = 0; slot < inventory.offhand.size() && taken < owed; slot++) {
            taken += take(inventory.offhand.get(slot), collect, owed - taken);
        }
        if (taken == 0) {
            return TurnIn.NOTHING;
        }
        inventory.setChanged();
        QuestInstance next = advance(player, instance, taken);
        boolean completed = next.state() == QuestStatus.READY_TO_TURN_IN
                && complete(player, questId, false, random).isPresent();
        return new TurnIn(taken, completed);
    }

    private static int take(ItemStack stack, QuestObjective.Collect objective, int max) {
        if (max <= 0 || !objective.matches(stack)) {
            return 0;
        }
        int taken = Math.min(stack.getCount(), max);
        stack.shrink(taken);
        return taken;
    }

    /**
     * Right-click on the giver: collect quests take items, other quests complete when ready.
     */
    public static TurnIn turnIn(ServerPlayer player, UUID questId, RandomSource random) {
        Optional<QuestInstance> found = get(player).find(questId);
        if (found.isEmpty()) {
            return TurnIn.NOTHING;
        }
        QuestInstance instance = found.get();
        if (instance.state() == QuestStatus.READY_TO_TURN_IN) {
            return new TurnIn(0, complete(player, questId, false, random).isPresent());
        }
        if (instance.state() == QuestStatus.ACTIVE) {
            return handIn(player, questId, random);
        }
        return TurnIn.NOTHING;
    }

    /** Marks a live, active quest's objective done (bounty: the target was killed); it is then ready to turn in. */
    public static boolean markObjectiveDone(ServerPlayer player, UUID questId) {
        Optional<QuestInstance> found = get(player).find(questId);
        if (found.isEmpty() || found.get().state() != QuestStatus.ACTIVE) {
            return false;
        }
        QuestInstance instance = found.get();
        return advance(player, instance, Math.max(1, instance.count() - instance.progress())) != instance;
    }

    /** Counts one completed bounty and returns the new tally. */
    public static int incrementBountiesCompleted(ServerPlayer player) {
        int next = get(player).bountiesCompleted() + 1;
        store(player, get(player).withBountiesCompleted(next));
        return next;
    }

    // ------------------------------------------------------------------ completion

    public static Optional<QuestRecord> complete(ServerPlayer player, UUID questId, boolean force) {
        return complete(player, questId, force, player.getRandom());
    }

    /**
     * Completes a quest: rolls the rewards, posts {@link QuestCompletedEvent}, applies alignment through the Core
     * API, records the coins (payout is a hook), files the record, bumps the counters and fires the
     * {@code complete_mini_quest} trigger.
     *
     * @param force complete even if the objective is not met (ops); failed quests never complete
     * @return the log record, empty if the quest does not exist, is failed, is not ready or became invalid
     */
    public static Optional<QuestRecord> complete(
            ServerPlayer player, UUID questId, boolean force, RandomSource random) {
        Optional<QuestInstance> found = get(player).find(questId);
        if (found.isEmpty() || found.get().failed()) {
            return Optional.empty();
        }
        QuestInstance instance = found.get();
        if (!force && instance.state() != QuestStatus.READY_TO_TURN_IN) {
            return Optional.empty();
        }
        RegistryAccess access = player.level().registryAccess();
        Optional<Holder.Reference<QuestTemplate>> template = template(access, instance.template());
        Optional<QuestGroup> group =
                template.flatMap(holder -> group(access, holder.value().group().location()));
        if (template.isEmpty() || group.isEmpty()) {
            fail(player, questId, FailReason.INVALID);
            return Optional.empty();
        }

        // Bounty quests (they name a target) pay exactly the values fixed at offer time, without variance.
        RewardBuilder rewards = instance.objectiveData().bountyTarget().isPresent()
                ? new RewardBuilder(instance.alignmentBase(), instance.coinBase())
                : new RewardBuilder(
                        QuestRewards.rollAlignment(instance.alignmentBase(), random),
                        QuestRewards.rollCoins(instance.coinBase(), random).coins());
        for (QuestReward extra : template.get().value().extraRewards()) {
            extra.grant(rewards);
        }
        NeoForge.EVENT_BUS.post(new QuestCompletedEvent(player, instance, rewards));

        ResourceKey<Faction> rewardFaction = group.get().effectiveRewardFaction();
        boolean skipAlignment = group.get().skipAlignmentIfEnemy() && Alignment.get(player, rewardFaction) < 0.0f;
        float alignment = skipAlignment ? 0.0f : rewards.alignment();
        QuestRewardSummary summary =
                new QuestRewardSummary(alignment, rewards.coins(), rewards.items(), rewards.hired());
        QuestRecord record = new QuestRecord(
                instance.template(),
                instance.giverName(),
                instance.giverFaction(),
                QuestOutcome.COMPLETED,
                Optional.empty(),
                player.level().getGameTime(),
                summary);
        PlayerQuests next = get(player)
                .withoutActive(questId)
                .withLogged(record)
                .withCompleted(instance.giverFaction());
        store(player, next);
        updateHolder(player.getServer(), instance.giver(), player.getUUID(), false);

        if (alignment != 0.0f) {
            Alignment.add(player, rewardFaction, alignment, AlignmentSource.quest(instance.template()));
        }
        if (rewards.coins() > 0) {
            coinPayout.pay(player, rewards.coins(), instance.giver());
        }
        for (ItemStack stack : rewards.items()) {
            player.getInventory().placeItemBackInInventory(stack.copy());
        }

        List<ResourceLocation> tags = template.get().tags().map(TagKey::location).toList();
        ProgressionTriggers.COMPLETE_MINI_QUEST
                .get()
                .trigger(
                        player,
                        ResourceKey.create(ModDataRegistries.FACTION, instance.giverFaction()),
                        instance.group(),
                        instance.template(),
                        tags,
                        next.completedTotal());
        return Optional.of(record);
    }

    // ------------------------------------------------------------------ failing and ending

    /**
     * Marks a live quest failed. It stays in {@code active} until removed and stops counting toward the caps. At
     * most {@link PlayerQuests#FAILED_CAP} failed entries stay; the oldest move to the log.
     *
     * @return whether a live quest was failed
     */
    public static boolean fail(ServerPlayer player, UUID questId, FailReason reason) {
        PlayerQuests quests = get(player);
        Optional<QuestInstance> found = quests.find(questId);
        if (found.isEmpty() || found.get().failed()) {
            return false;
        }
        QuestInstance failed = found.get().failed(reason);
        long now = player.level().getGameTime();
        quests = quests.replace(failed).withFailedCounted();
        while (quests.failedCount() > PlayerQuests.FAILED_CAP) {
            QuestInstance oldest = quests.active().stream()
                    .filter(QuestInstance::failed)
                    .findFirst()
                    .orElseThrow();
            quests = quests.withoutActive(oldest.uuid())
                    .withLogged(QuestRecord.ended(oldest, QuestOutcome.FAILED, now));
        }
        store(player, quests);
        updateHolder(player.getServer(), failed.giver(), player.getUUID(), false);
        NeoForge.EVENT_BUS.post(new QuestFailedEvent(player, failed, reason));
        player.sendSystemMessage(Component.translatable(
                "quest.lotrrevival.failed", failed.giverName(), Component.translatable(reason.langKey())));
        return true;
    }

    /** {@link #failQuestsFromGiver(MinecraftServer, UUID)} on the running server. */
    public static int failQuestsFromGiver(UUID giver) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? 0 : failQuestsFromGiver(server, giver);
    }

    /**
     * The giver died: fails every online holder's live quest from it with {@code giver_died} and remembers the
     * giver in {@link DeadGivers} so offline holders fail on login.
     *
     * @return how many quests failed now
     */
    public static int failQuestsFromGiver(MinecraftServer server, UUID giver) {
        DeadGivers.get(server).add(giver, server.overworld().getGameTime());
        int failed = 0;
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            for (QuestInstance instance : get(player).active()) {
                if (instance.live() && instance.giver().equals(giver) && fail(player, instance.uuid(), FailReason.GIVER_DIED)) {
                    failed++;
                }
            }
        }
        return failed;
    }

    /** Login check: fails live quests whose giver is in the dead-giver list. */
    public static int checkDeadGivers(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0;
        }
        DeadGivers dead = DeadGivers.get(server);
        dead.prune(server.overworld().getGameTime());
        int failed = 0;
        for (QuestInstance instance : get(player).active()) {
            if (instance.live() && dead.contains(instance.giver()) && fail(player, instance.uuid(), FailReason.GIVER_DIED)) {
                failed++;
            }
        }
        return failed;
    }

    /**
     * Fails live quests whose template, group or faction no longer exists (datapack change) with {@code invalid}.
     * No penalty. Never throws on unknown ids.
     */
    public static int validate(ServerPlayer player) {
        RegistryAccess access = player.level().registryAccess();
        Optional<Registry<Faction>> factions = access.registry(ModDataRegistries.FACTION);
        int failed = 0;
        for (QuestInstance instance : get(player).active()) {
            if (!instance.live()) {
                continue;
            }
            Optional<QuestGroup> group = template(access, instance.template())
                    .flatMap(template -> group(access, template.value().group().location()));
            boolean valid = group.isPresent()
                    && factions.isPresent()
                    && factions.get().containsKey(group.get().faction());
            if (!valid && fail(player, instance.uuid(), FailReason.INVALID)) {
                failed++;
            }
        }
        return failed;
    }

    /** Abandons a live quest: free, no penalty, recorded as abandoned. */
    public static boolean abandon(ServerPlayer player, UUID questId) {
        Optional<QuestInstance> found = get(player).find(questId);
        if (found.isEmpty() || found.get().failed()) {
            return false;
        }
        QuestInstance instance = found.get();
        store(
                player,
                get(player)
                        .withoutActive(questId)
                        .withLogged(QuestRecord.ended(instance, QuestOutcome.ABANDONED, player.level().getGameTime())));
        updateHolder(player.getServer(), instance.giver(), player.getUUID(), false);
        return true;
    }

    /** Removes a failed quest from the active list into the log. */
    public static boolean remove(ServerPlayer player, UUID questId) {
        Optional<QuestInstance> found = get(player).find(questId);
        if (found.isEmpty() || !found.get().failed()) {
            return false;
        }
        store(
                player,
                get(player)
                        .withoutActive(questId)
                        .withLogged(QuestRecord.ended(found.get(), QuestOutcome.FAILED, player.level().getGameTime())));
        return true;
    }

    /** Deletes one log record by index (oldest first). */
    public static boolean deleteRecord(ServerPlayer player, int index) {
        PlayerQuests quests = get(player);
        if (index < 0 || index >= quests.log().size()) {
            return false;
        }
        store(player, quests.withoutLogged(index));
        return true;
    }

    /** Tracks one quest (empty untracks). Only quests in the active list can be tracked. */
    public static boolean track(ServerPlayer player, Optional<UUID> questId) {
        PlayerQuests quests = get(player);
        if (questId.isPresent() && quests.find(questId.get()).isEmpty()) {
            return false;
        }
        store(player, quests.withTracked(questId));
        return true;
    }

    /** Op reset: drops all active quests, the tracked quest and the log. Counters stay. */
    public static void clear(ServerPlayer player) {
        for (QuestInstance instance : get(player).active()) {
            updateHolder(player.getServer(), instance.giver(), player.getUUID(), false);
        }
        store(player, get(player).withoutQuestsOrLog());
    }

    /** A giver counts as moved when it is further than this (blocks) from the stored position. */
    public static final double GIVER_MOVE_THRESHOLD = 16.0;

    /**
     * Refreshes {@code giver_pos} of the player's live quests from {@code giver} when the giver moved more than
     * {@link #GIVER_MOVE_THRESHOLD} blocks (or changed dimension, or had no stored position).
     *
     * @return whether anything changed
     */
    public static boolean updateGiverPos(ServerPlayer player, UUID giver, GlobalPos pos) {
        PlayerQuests quests = get(player);
        boolean changed = false;
        for (QuestInstance instance : quests.active()) {
            if (!instance.live() || !instance.giver().equals(giver)) {
                continue;
            }
            Optional<GlobalPos> stored = instance.giverPos();
            boolean moved = stored.isEmpty()
                    || !stored.get().dimension().equals(pos.dimension())
                    || stored.get().pos().distSqr(pos.pos()) > GIVER_MOVE_THRESHOLD * GIVER_MOVE_THRESHOLD;
            if (moved) {
                quests = quests.replace(instance.withGiverPos(Optional.of(pos)));
                changed = true;
            }
        }
        if (changed) {
            store(player, quests);
        }
        return changed;
    }

    // ------------------------------------------------------------------ plumbing

    /** Writes the attachment and marks the {@code quests} sync dirty. */
    static void store(ServerPlayer player, PlayerQuests quests) {
        player.setData(ProgressionAttachments.QUESTS, quests);
        ProgressionSync.markDirty(player, ProgressionSync.Type.QUESTS);
    }

    /** Keeps the giver entity's {@code holders} set current, if the giver is loaded. */
    private static void updateHolder(@Nullable MinecraftServer server, UUID giver, UUID player, boolean present) {
        if (server == null || PLACEHOLDER_GIVER.equals(giver)) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(giver);
            if (entity != null) {
                QuestGiverData data = entity.getData(ProgressionAttachments.QUEST_GIVER);
                if (data.holders().contains(player) != present) {
                    entity.setData(ProgressionAttachments.QUEST_GIVER, data.withHolder(player, present));
                }
                return;
            }
        }
    }
}
