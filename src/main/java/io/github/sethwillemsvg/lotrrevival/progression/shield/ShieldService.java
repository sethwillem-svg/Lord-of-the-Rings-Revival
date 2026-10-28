package io.github.sethwillemsvg.lotrrevival.progression.shield;

import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementService;
import io.github.sethwillemsvg.lotrrevival.progression.network.ProgressionSync;
import io.github.sethwillemsvg.lotrrevival.progression.network.ShieldSyncPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of shields: selection (validated at select time only), the attachment and the sync to the
 * player and everyone tracking them.
 */
public final class ShieldService {
    /** Core's convention for client-to-server payloads: 20 per second per player. */
    static final int MAX_SELECTS_PER_SECOND = 20;

    private static final ToIntFunction<ServerPlayer> DEFAULT_COUNT = AchievementService::count;
    private static volatile ToIntFunction<ServerPlayer> countSource = DEFAULT_COUNT;
    private static volatile BiConsumer<ServerPlayer, ShieldSyncPayload> syncObserver;

    /** Selection requests per player: window start tick and count in the window. */
    private static final Map<UUID, long[]> RATE = new HashMap<>();

    private ShieldService() {}

    /** Called once from {@code ProgressionMod.init}. */
    public static void init() {
        ProgressionSync.registerFullSync(ShieldService::syncToAll);
    }

    /** Test seam: replaces where the achievement count comes from; null restores the default. */
    public static void setCountSource(ToIntFunction<ServerPlayer> source) {
        countSource = source == null ? DEFAULT_COUNT : source;
    }

    /** Test seam: sees every payload the service sends (before the network check); null removes it. */
    public static void setSyncObserver(BiConsumer<ServerPlayer, ShieldSyncPayload> observer) {
        syncObserver = observer;
    }

    public static Optional<ResourceLocation> selected(ServerPlayer player) {
        return player.getData(ProgressionAttachments.COSMETICS).shield();
    }

    /**
     * Handles a {@code select_shield} request. Returns false and changes nothing when the player is over the
     * rate limit, the shield is unknown or its unlock is not met by the current achievement count. An empty id
     * removes the shield. Validity is not re-checked later.
     */
    public static boolean select(ServerPlayer player, Optional<ResourceLocation> shield) {
        if (!allowRequest(player)) {
            return false;
        }
        if (shield.isPresent()) {
            ShieldDefinition definition = ShieldDefinitions.find(player.level().registryAccess(), shield.get())
                    .orElse(null);
            if (definition == null || !definition.unlock().isMet(countSource.applyAsInt(player))) {
                return false;
            }
        }
        if (!shield.equals(selected(player))) {
            player.setData(ProgressionAttachments.COSMETICS, new Cosmetics(shield));
            syncToAll(player);
        }
        return true;
    }

    /** Sends the player's shield to the player and everyone tracking them (change, login, respawn, dimension). */
    public static void syncToAll(ServerPlayer player) {
        ShieldSyncPayload payload = new ShieldSyncPayload(player.getUUID(), selected(player));
        observe(player, payload);
        if (canReceive(player)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
        }
    }

    /** Tells {@code tracker} about {@code target}'s shield when it has one (start tracking). */
    public static void syncTo(ServerPlayer tracker, ServerPlayer target) {
        Optional<ResourceLocation> shield = selected(target);
        if (shield.isEmpty()) {
            return;
        }
        ShieldSyncPayload payload = new ShieldSyncPayload(target.getUUID(), shield);
        if (canReceive(tracker)) {
            PacketDistributor.sendToPlayer(tracker, payload);
        }
    }

    public static void forget(ServerPlayer player) {
        synchronized (RATE) {
            RATE.remove(player.getUUID());
        }
    }

    private static void observe(ServerPlayer player, ShieldSyncPayload payload) {
        BiConsumer<ServerPlayer, ShieldSyncPayload> observer = syncObserver;
        if (observer != null) {
            observer.accept(player, payload);
        }
    }

    /** False for players without a negotiated connection (fake players, test connections). */
    private static boolean canReceive(ServerPlayer player) {
        return !player.isFakePlayer()
                && player.connection != null
                && player.connection.hasChannel(ShieldSyncPayload.TYPE);
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
}
