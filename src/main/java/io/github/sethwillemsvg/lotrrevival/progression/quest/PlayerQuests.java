package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable value of the {@code lotrrevival:quests} attachment. Replace it with {@code setData} on every change;
 * go through {@link Quests} so the change is synced.
 *
 * @param active quests in acceptance order; failed ones stay until removed
 * @param tracked the tracked quest, if any
 * @param log completed, failed-and-removed and abandoned records, oldest first, at most {@link #LOG_CAP}
 * @param completedByFaction completed quests per giver faction id; never trimmed
 * @param bountiesCompleted reserved for the bounty system
 */
public record PlayerQuests(
        List<QuestInstance> active,
        Optional<UUID> tracked,
        List<QuestRecord> log,
        Map<ResourceLocation, Integer> completedByFaction,
        int completedTotal,
        int failedTotal,
        int bountiesCompleted) {
    public static final int LOG_CAP = 50;
    public static final int FAILED_CAP = 20;

    public static final PlayerQuests EMPTY =
            new PlayerQuests(List.of(), Optional.empty(), List.of(), Map.of(), 0, 0, 0);

    public static final Codec<PlayerQuests> CODEC = RecordCodecBuilder.create(i -> i.group(
                    QuestInstance.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(PlayerQuests::active),
                    UUIDUtil.CODEC.optionalFieldOf("tracked").forGetter(PlayerQuests::tracked),
                    QuestRecord.CODEC.listOf().optionalFieldOf("log", List.of()).forGetter(PlayerQuests::log),
                    Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                            .optionalFieldOf("completed_by_faction", Map.of())
                            .forGetter(PlayerQuests::completedByFaction),
                    Codec.INT.optionalFieldOf("completed_total", 0).forGetter(PlayerQuests::completedTotal),
                    Codec.INT.optionalFieldOf("failed_total", 0).forGetter(PlayerQuests::failedTotal),
                    Codec.INT.optionalFieldOf("bounties_completed", 0).forGetter(PlayerQuests::bountiesCompleted))
            .apply(i, PlayerQuests::new));

    /** Wire format: the NBT form of {@link #CODEC} (the state is small and bounded). */
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerQuests> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public PlayerQuests {
        active = List.copyOf(active);
        log = List.copyOf(log);
        completedByFaction = Map.copyOf(completedByFaction);
    }

    public Optional<QuestInstance> find(UUID id) {
        return active.stream().filter(q -> q.uuid().equals(id)).findFirst();
    }

    /** Quests that count toward the caps. */
    public long liveCount() {
        return active.stream().filter(QuestInstance::live).count();
    }

    public long liveCount(ResourceLocation giverFaction) {
        return active.stream()
                .filter(q -> q.live() && q.giverFaction().equals(giverFaction))
                .count();
    }

    public long failedCount() {
        return active.stream().filter(QuestInstance::failed).count();
    }

    public boolean hasLiveFrom(UUID giver) {
        return active.stream().anyMatch(q -> q.live() && q.giver().equals(giver));
    }

    /** Replaces the instance with the same uuid. */
    public PlayerQuests replace(QuestInstance instance) {
        List<QuestInstance> next = new ArrayList<>(active.size());
        for (QuestInstance q : active) {
            next.add(q.uuid().equals(instance.uuid()) ? instance : q);
        }
        return withActive(next);
    }

    public PlayerQuests withActiveMapped(UnaryOperator<QuestInstance> mapper) {
        return withActive(active.stream().map(mapper).toList());
    }

    public PlayerQuests withActive(List<QuestInstance> active) {
        return new PlayerQuests(active, tracked, log, completedByFaction, completedTotal, failedTotal, bountiesCompleted);
    }

    public PlayerQuests withAdded(QuestInstance instance) {
        List<QuestInstance> next = new ArrayList<>(active);
        next.add(instance);
        return withActive(next);
    }

    /** Removes the instance; clears the tracked quest if it was this one. */
    public PlayerQuests withoutActive(UUID id) {
        List<QuestInstance> next = new ArrayList<>(active);
        next.removeIf(q -> q.uuid().equals(id));
        Optional<UUID> nextTracked = tracked.filter(t -> !t.equals(id));
        return new PlayerQuests(next, nextTracked, log, completedByFaction, completedTotal, failedTotal, bountiesCompleted);
    }

    public PlayerQuests withTracked(Optional<UUID> tracked) {
        return new PlayerQuests(active, tracked, log, completedByFaction, completedTotal, failedTotal, bountiesCompleted);
    }

    /** Appends a record, dropping the oldest beyond {@link #LOG_CAP}. */
    public PlayerQuests withLogged(QuestRecord record) {
        List<QuestRecord> next = new ArrayList<>(log);
        next.add(record);
        while (next.size() > LOG_CAP) {
            next.remove(0);
        }
        return new PlayerQuests(active, tracked, next, completedByFaction, completedTotal, failedTotal, bountiesCompleted);
    }

    public PlayerQuests withoutLogged(int index) {
        List<QuestRecord> next = new ArrayList<>(log);
        next.remove(index);
        return new PlayerQuests(active, tracked, next, completedByFaction, completedTotal, failedTotal, bountiesCompleted);
    }

    public PlayerQuests withCompleted(ResourceLocation faction) {
        Map<ResourceLocation, Integer> next = new HashMap<>(completedByFaction);
        next.merge(faction, 1, Integer::sum);
        return new PlayerQuests(active, tracked, log, next, completedTotal + 1, failedTotal, bountiesCompleted);
    }

    public PlayerQuests withFailedCounted() {
        return new PlayerQuests(active, tracked, log, completedByFaction, completedTotal, failedTotal + 1, bountiesCompleted);
    }

    public PlayerQuests withoutQuestsOrLog() {
        return new PlayerQuests(List.of(), Optional.empty(), List.of(), completedByFaction, completedTotal, failedTotal, bountiesCompleted);
    }
}
