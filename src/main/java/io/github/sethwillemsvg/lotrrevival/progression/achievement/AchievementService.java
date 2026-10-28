package io.github.sethwillemsvg.lotrrevival.progression.achievement;

import io.github.sethwillemsvg.lotrrevival.core.api.AchievementCounters;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import io.github.sethwillemsvg.lotrrevival.progression.api.AchievementCountChangedEvent;
import io.github.sethwillemsvg.lotrrevival.progression.api.ProgressionApi;
import io.github.sethwillemsvg.lotrrevival.progression.network.AchievementSync;
import io.github.sethwillemsvg.lotrrevival.progression.network.AchievementToastPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.AchievementsDeltaPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionSync;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Server side of achievements: the earned set per player, the cached {@code achievementCount}, the award flow
 * (toast and chat line) and the sync to the client.
 *
 * <p>The earned set follows advancement grants and revokes incrementally and is rebuilt from the player's
 * advancements on login, respawn and datapack reload. Vanilla only fires the advancement earn event for
 * advancements that have a display block, which ours never have, so grants are detected on the progress event.
 */
public final class AchievementService {
    /** Where the award flow delivers its output; replaceable so GameTests can observe it. */
    public interface Sink {
        void toast(ServerPlayer player, ResourceLocation id);

        void broadcast(ServerPlayer player, Component line);
    }

    private static final Sink DEFAULT_SINK = new Sink() {
        @Override
        public void toast(ServerPlayer player, ResourceLocation id) {
            ProgressionSync.send(player, new AchievementToastPayload(id));
        }

        @Override
        public void broadcast(ServerPlayer player, Component line) {
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(line, false);
            }
        }
    };

    private static volatile Sink sink = DEFAULT_SINK;

    /** Per player instance: a respawned player is a new instance and starts from its advancements again. */
    private static final Map<ServerPlayer, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private AchievementService() {}

    private static final class State {
        /** Earned ids that have a display entry. */
        final Set<ResourceLocation> earned = new LinkedHashSet<>();
        /** What the client was last told. */
        Set<ResourceLocation> synced = new HashSet<>();
        /** Count as of the last flush. */
        int count;
        /** Count last announced through {@link AchievementCountChangedEvent}. */
        int published;
        boolean dirty;
    }

    /** Called once from {@code ProgressionMod.init}. */
    public static void init() {
        AchievementCounters.register(ProgressionApi::achievementCount);
        ProgressionSync.registerDeltaSender(ProgressionSync.Type.ACHIEVEMENTS, AchievementService::flush);
        ProgressionSync.registerFullSync(AchievementService::sendFull);
    }

    /** Replaces the output sink and returns the previous one (GameTests restore it in a finally). */
    public static Sink setSink(Sink replacement) {
        Sink previous = sink;
        sink = replacement == null ? DEFAULT_SINK : replacement;
        return previous;
    }

    // ------------------------------------------------------------------ state

    private static State state(ServerPlayer player) {
        synchronized (STATES) {
            State state = STATES.get(player);
            if (state == null) {
                state = create(player);
                STATES.put(player, state);
            }
            return state;
        }
    }

    private static State create(ServerPlayer player) {
        State state = new State();
        state.earned.addAll(scanEarned(player));
        state.count = computeCount(player, state.earned);
        state.published = state.count;
        state.synced = new HashSet<>(state.earned);
        return state;
    }

    /** Ids with a display entry whose backing advancement is done. */
    private static Set<ResourceLocation> scanEarned(ServerPlayer player) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        MinecraftServer server = player.getServer();
        if (server == null) {
            return out;
        }
        Registry<AchievementEntry> registry =
                server.registryAccess().registry(AchievementEntry.REGISTRY).orElse(null);
        if (registry == null) {
            return out;
        }
        for (ResourceLocation id : registry.keySet()) {
            AdvancementHolder holder = server.getAdvancements().get(id);
            if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
                out.add(id);
            }
        }
        return out;
    }

    private static int computeCount(ServerPlayer player, Set<ResourceLocation> earned) {
        RegistryAccess access = player.level().registryAccess();
        int count = 0;
        for (ResourceLocation id : earned) {
            AchievementEntry entry = AchievementEntries.find(access, id).orElse(null);
            if (entry != null && entry.counted() && AchievementEligibility.isEligible(player, entry)) {
                count++;
            }
        }
        return count;
    }

    /** Earned, eligible and counted. Cached until something it depends on changes, then recomputed on read. */
    public static int count(ServerPlayer player) {
        State state = state(player);
        synchronized (state) {
            return state.dirty ? computeCount(player, state.earned) : state.count;
        }
    }

    /** Earned ids that have an entry, including ones that are not eligible right now. */
    public static Set<ResourceLocation> earned(ServerPlayer player) {
        State state = state(player);
        synchronized (state) {
            return Set.copyOf(state.earned);
        }
    }

    /** Forgets a player's state (logout, tests). */
    public static void forget(ServerPlayer player) {
        STATES.remove(player);
    }

    // ------------------------------------------------------------------ events

    /** Something the count depends on changed: alignment, pledge. Recomputed at the end of the tick. */
    public static void markDirty(ServerPlayer player) {
        State state = state(player);
        synchronized (state) {
            state.dirty = true;
        }
        ProgressionSync.markDirty(player, ProgressionSync.Type.ACHIEVEMENTS);
    }

    /** The backing advancement of {@code holder} is done. Idempotent; runs the award flow once per earn. */
    public static void onEarned(ServerPlayer player, AdvancementHolder holder) {
        ResourceLocation id = holder.id();
        AchievementEntry entry =
                AchievementEntries.find(player.level().registryAccess(), id).orElse(null);
        if (entry == null) {
            return;
        }
        State state;
        synchronized (STATES) {
            state = STATES.get(player);
            if (state == null) {
                // The advancement is already done, so a fresh scan would contain it and hide this earn.
                state = create(player);
                state.earned.remove(id);
                state.synced.remove(id);
                STATES.put(player, state);
            }
        }
        boolean added;
        synchronized (state) {
            added = state.earned.add(id);
            state.dirty = true;
        }
        ProgressionSync.markDirty(player, ProgressionSync.Type.ACHIEVEMENTS);
        if (added && AchievementEligibility.isEligible(player, entry)) {
            sink.toast(player, id);
            if (!isBroadcastDenied(id)) {
                sink.broadcast(player, awardLine(player, entry));
            }
        }
    }

    /** The backing advancement of {@code id} is no longer done. */
    public static void onRevoked(ServerPlayer player, ResourceLocation id) {
        State state = state(player);
        boolean removed;
        synchronized (state) {
            removed = state.earned.remove(id);
            if (removed) {
                state.dirty = true;
            }
        }
        if (removed) {
            ProgressionSync.markDirty(player, ProgressionSync.Type.ACHIEVEMENTS);
        }
    }

    /** Datapack reload: ids may have appeared or vanished, so rebuild from the advancements. */
    public static void rescan(ServerPlayer player) {
        State state = state(player);
        Set<ResourceLocation> fresh = scanEarned(player);
        synchronized (state) {
            state.earned.clear();
            state.earned.addAll(fresh);
        }
        markDirty(player);
    }

    // ------------------------------------------------------------------ award flow

    public static boolean isBroadcastDenied(ResourceLocation id) {
        for (String entry : ProgressionConfig.get(ProgressionConfig.ACHIEVEMENT_BROADCAST_DENYLIST)) {
            if (id.equals(ResourceLocation.tryParse(entry))) {
                return true;
            }
        }
        return false;
    }

    /** {@code <player> has earned the achievement [Title]}, the bracket hovering the description. */
    public static Component awardLine(ServerPlayer player, AchievementEntry entry) {
        MutableComponent bracket = Component.literal("[").append(entry.title()).append("]");
        bracket.withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, entry.description())));
        return Component.translatable("chat.lotrrevival.achievement.earned", player.getDisplayName(), bracket);
    }

    // ------------------------------------------------------------------ sync

    /** End-of-tick flush for a dirty player: recompute the count, post the change event, send the delta. */
    static void flush(ServerPlayer player) {
        State state = state(player);
        int oldCount;
        int newCount;
        List<ResourceLocation> added = new ArrayList<>();
        List<ResourceLocation> removed = new ArrayList<>();
        synchronized (state) {
            state.count = computeCount(player, state.earned);
            state.dirty = false;
            oldCount = state.published;
            newCount = state.count;
            state.published = newCount;
            for (ResourceLocation id : state.earned) {
                if (!state.synced.contains(id)) {
                    added.add(id);
                }
            }
            for (ResourceLocation id : state.synced) {
                if (!state.earned.contains(id)) {
                    removed.add(id);
                }
            }
            state.synced = new HashSet<>(state.earned);
        }
        if (added.size() + removed.size() > AchievementSync.MAX_DELTA_IDS) {
            sendFullPayloads(player, state);
        } else if (!added.isEmpty() || !removed.isEmpty()) {
            ProgressionSync.send(player, new AchievementsDeltaPayload(added, removed));
        }
        if (oldCount != newCount) {
            NeoForge.EVENT_BUS.post(new AchievementCountChangedEvent(player, oldCount, newCount));
        }
    }

    /** Full-sync hook (login, respawn, dimension change): rescan, settle the count and send every part. */
    static void sendFull(ServerPlayer player) {
        State state = state(player);
        Set<ResourceLocation> fresh = scanEarned(player);
        int oldCount;
        int newCount;
        synchronized (state) {
            state.earned.clear();
            state.earned.addAll(fresh);
            state.count = computeCount(player, state.earned);
            state.dirty = false;
            oldCount = state.published;
            newCount = state.count;
            state.published = newCount;
        }
        sendFullPayloads(player, state);
        if (oldCount != newCount) {
            NeoForge.EVENT_BUS.post(new AchievementCountChangedEvent(player, oldCount, newCount));
        }
    }

    private static void sendFullPayloads(ServerPlayer player, State state) {
        List<ResourceLocation> ids;
        synchronized (state) {
            state.synced = new HashSet<>(state.earned);
            ids = new ArrayList<>(state.earned);
        }
        AchievementSync.split(ids).forEach(payload -> ProgressionSync.send(player, payload));
    }
}
