package io.github.sethwillemsvg.lotrrevival.progression.title;

import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionSync;
import io.github.sethwillemsvg.lotrrevival.progression.network.TitlesDeltaPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.TitlesFullPayload;
import io.github.sethwillemsvg.lotrrevival.progression.trigger.ProgressionTriggers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of titles: selection, the re-check that clears a title the player no longer qualifies for, the
 * {@code earn_title} trigger, the title cache and the sync to the client.
 *
 * <p>Things that can change usability only mark the player dirty; the re-check runs in the end-of-tick flush
 * (see {@link ProgressionSync}), on login and on respawn.
 */
public final class TitleService {
    /** Where notices and the {@code earn_title} trigger go; replaceable so GameTests can observe them. */
    public interface Sink {
        /** The selected title was cleared; {@code notice} names it. */
        void lost(ServerPlayer player, Component notice);

        /** A title became usable for the first time; {@code usableCount} counts the distinct usable titles. */
        void earned(ServerPlayer player, ResourceLocation title, int usableCount);
    }

    private static final Sink DEFAULT_SINK = new Sink() {
        @Override
        public void lost(ServerPlayer player, Component notice) {
            if (!player.isFakePlayer() && player.connection != null) {
                player.sendSystemMessage(notice);
            }
        }

        @Override
        public void earned(ServerPlayer player, ResourceLocation title, int usableCount) {
            ProgressionTriggers.EARN_TITLE.get().trigger(player, title, usableCount);
        }
    };

    private static volatile Sink sink = DEFAULT_SINK;

    /** What the client was last told, per player instance (a respawned player starts from a full sync). */
    private record Snapshot(
            Optional<ResourceLocation> selected, ChatFormatting color, boolean feminine, Set<ResourceLocation> granted) {
        static Snapshot of(TitleData data) {
            return new Snapshot(data.selected(), data.color(), data.feminineRanks(), data.granted());
        }
    }

    private static final Map<ServerPlayer, Snapshot> SYNCED = Collections.synchronizedMap(new WeakHashMap<>());

    /** Selection requests per player: window start tick and count in the window. */
    private static final Map<UUID, long[]> RATE = new HashMap<>();

    /** Core's convention for client-to-server payloads: 20 per second per player. */
    static final int MAX_SELECTS_PER_SECOND = 20;

    private TitleService() {}

    /** Called once from {@code ProgressionMod.init}. */
    public static void init() {
        ProgressionSync.registerDeltaSender(ProgressionSync.Type.TITLES, TitleService::flush);
        ProgressionSync.registerFullSync(TitleService::sendFull);
    }

    /** Replaces the output sink and returns the previous one (GameTests restore it in a finally). */
    public static Sink setSink(Sink replacement) {
        Sink previous = sink;
        sink = replacement == null ? DEFAULT_SINK : replacement;
        return previous;
    }

    public static TitleData data(ServerPlayer player) {
        return player.getData(ProgressionAttachments.TITLES);
    }

    /** Forgets per-player transient state (logout, tests). */
    public static void forget(ServerPlayer player) {
        SYNCED.remove(player);
        synchronized (RATE) {
            RATE.remove(player.getUUID());
        }
    }

    // ------------------------------------------------------------------ changes

    /** Writes the data, mirrors the choice into the title cache and queues a delta. */
    private static void store(ServerPlayer player, TitleData data, boolean queueSync) {
        player.setData(ProgressionAttachments.TITLES, data);
        MinecraftServer server = player.getServer();
        if (server != null) {
            TitleCache.get(server).put(player.getUUID(), data.selected(), data.color(), data.feminineRanks());
        }
        if (queueSync) {
            ProgressionSync.markDirty(player, ProgressionSync.Type.TITLES);
        }
    }

    /** Something titles depend on changed; the re-check and the delta happen at the end of the tick. */
    public static void markDirty(ServerPlayer player) {
        ProgressionSync.markDirty(player, ProgressionSync.Type.TITLES);
    }

    /**
     * Handles a {@code select_title} request. Returns false (and changes nothing) when titles are disabled, the
     * player is over the rate limit, the colour is not one of the 16 or the title is unknown or not usable.
     * An empty title clears the selection.
     */
    public static boolean select(ServerPlayer player, Optional<ResourceLocation> title, String colorName, boolean feminine) {
        if (!Titles.enabled(player) || !allowRequest(player)) {
            return false;
        }
        ChatFormatting color = TitleData.parseColor(colorName);
        if (color == null) {
            return false;
        }
        if (title.isPresent() && !Titles.isUsable(player, title.get())) {
            return false;
        }
        TitleData data = data(player);
        TitleData next = data.withChoice(title, color, feminine);
        if (!next.equals(data)) {
            store(player, next, true);
        }
        return true;
    }

    private static boolean allowRequest(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        synchronized (RATE) {
            long[] window = RATE.computeIfAbsent(player.getUUID(), id -> new long[] {now, 0});
            if (now - window[0] >= 20) {
                window[0] = now;
                window[1] = 0;
            }
            return ++window[1] <= MAX_SELECTS_PER_SECOND;
        }
    }

    /** Adds a granted (special) title. Returns false when the player already has it. */
    public static boolean grant(ServerPlayer player, ResourceLocation id) {
        TitleData data = data(player);
        if (data.granted().contains(id)) {
            return false;
        }
        Set<ResourceLocation> granted = new LinkedHashSet<>(data.granted());
        granted.add(id);
        store(player, data.withGranted(granted), true);
        return true;
    }

    /** Removes a granted title. A selected title that stops being usable is cleared by the next re-check. */
    public static boolean revoke(ServerPlayer player, ResourceLocation id) {
        TitleData data = data(player);
        if (!data.granted().contains(id)) {
            return false;
        }
        Set<ResourceLocation> granted = new LinkedHashSet<>(data.granted());
        granted.remove(id);
        store(player, data.withGranted(granted), true);
        return true;
    }

    // ------------------------------------------------------------------ re-check

    /**
     * Clears the selection when it is no longer usable or gone (with a notice naming it), adds newly usable
     * titles to {@code ever_usable} and fires {@code earn_title} once for each. Does nothing while titles are
     * disabled, so the selection survives on disk.
     */
    public static void recheck(ServerPlayer player) {
        if (!Titles.enabled(player)) {
            return;
        }
        RegistryAccess access = player.level().registryAccess();
        TitleData data = data(player);
        Set<ResourceLocation> usable = new LinkedHashSet<>();
        TitleDefinitions.all(access).forEach((id, definition) -> {
            if (Titles.isUsable(player, id, definition)) {
                usable.add(id);
            }
        });

        List<ResourceLocation> fresh = new ArrayList<>();
        for (ResourceLocation id : usable) {
            if (!data.everUsable().contains(id)) {
                fresh.add(id);
            }
        }
        fresh.sort(null);

        TitleData next = data;
        if (!fresh.isEmpty()) {
            Set<ResourceLocation> ever = new LinkedHashSet<>(data.everUsable());
            ever.addAll(fresh);
            next = next.withEverUsable(ever);
        }
        Component lost = null;
        if (data.selected().isPresent() && !usable.contains(data.selected().get())) {
            ResourceLocation id = data.selected().get();
            Component name = TitleDefinitions.find(access, id)
                    .map(definition -> definition.displayText(data.feminineRanks()))
                    .orElseGet(() -> Component.literal(id.toString()));
            lost = Component.translatable("title.lotrrevival.lost", name);
            next = next.withSelected(Optional.empty());
        }
        if (!next.equals(data)) {
            store(player, next, false);
        }
        if (lost != null) {
            sink.lost(player, lost);
        }
        for (ResourceLocation id : fresh) {
            sink.earned(player, id, usable.size());
        }
    }

    // ------------------------------------------------------------------ chat

    /** {@code [Title]} in the player's colour, or empty when titles are off or nothing usable is selected. */
    public static Optional<Component> chatPrefix(ServerPlayer player) {
        if (!Titles.enabled(player)) {
            return Optional.empty();
        }
        TitleData data = data(player);
        if (data.selected().isEmpty() || !Titles.isUsable(player, data.selected().get())) {
            return Optional.empty();
        }
        return prefix(player.level().registryAccess(), data.selected().get(), data.color(), data.feminineRanks());
    }

    /** The bracketed, coloured title for any stored choice (also for the title cache); empty if the id is gone. */
    public static Optional<Component> prefix(RegistryAccess access, ResourceLocation id, ChatFormatting color, boolean feminine) {
        return TitleDefinitions.find(access, id).map(definition -> {
            MutableComponent text = Component.literal("[").append(definition.displayText(feminine)).append("]");
            return text.withStyle(color);
        });
    }

    /** The chat line {@code [Title] <Name> message}, or empty when the player has no title to show. */
    public static Optional<Component> chatLine(ServerPlayer player, Component message) {
        return chatPrefix(player).map(prefix -> Component.empty()
                .append(prefix)
                .append(" ")
                .append(Component.translatable("chat.type.text", player.getDisplayName(), message)));
    }

    // ------------------------------------------------------------------ sync

    /** End-of-tick flush for a dirty player: re-check, then send what changed. */
    static void flush(ServerPlayer player) {
        recheck(player);
        TitleData data = data(player);
        Snapshot now = Snapshot.of(data);
        Snapshot before = SYNCED.get(player);
        if (before == null) {
            sendFull(player);
            return;
        }
        if (before.equals(now)) {
            return;
        }
        List<ResourceLocation> added = new ArrayList<>();
        List<ResourceLocation> removed = new ArrayList<>();
        for (ResourceLocation id : now.granted()) {
            if (!before.granted().contains(id)) {
                added.add(id);
            }
        }
        for (ResourceLocation id : before.granted()) {
            if (!now.granted().contains(id)) {
                removed.add(id);
            }
        }
        SYNCED.put(player, now);
        ProgressionSync.send(
                player,
                new TitlesDeltaPayload(data.selected(), data.color().getName(), data.feminineRanks(), added, removed));
    }

    /** Full-sync hook (login, respawn, dimension change): re-check first so the client never sees a stale title. */
    static void sendFull(ServerPlayer player) {
        recheck(player);
        TitleData data = data(player);
        SYNCED.put(player, Snapshot.of(data));
        ProgressionSync.send(
                player,
                new TitlesFullPayload(
                        Titles.enabled(player),
                        Titles.strict(player),
                        data.selected(),
                        data.color().getName(),
                        data.feminineRanks(),
                        List.copyOf(data.granted())));
    }
}
