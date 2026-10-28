package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sync scheduler. Systems mark a (player, type) dirty; one delta per dirty type is flushed at the end
 * of the server tick. Full-sync hooks run on login, respawn and dimension change, after Core's
 * alignment sync (this class listens at {@link EventPriority#LOW}).
 */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class ProgressionSync {
    public enum Type {
        ACHIEVEMENTS,
        TITLES,
        QUESTS
    }

    private static final Map<UUID, EnumSet<Type>> DIRTY = new HashMap<>();
    private static final Map<Type, Consumer<ServerPlayer>> DELTA_SENDERS = new EnumMap<>(Type.class);
    private static final List<Consumer<ServerPlayer>> FULL_SYNC_HOOKS = new ArrayList<>();

    private ProgressionSync() {}

    /** Registers the function that sends the delta payload for a type (once per type, by its owner). */
    public static void registerDeltaSender(Type type, Consumer<ServerPlayer> sender) {
        DELTA_SENDERS.put(type, sender);
    }

    public static void unregisterDeltaSender(Type type) {
        DELTA_SENDERS.remove(type);
    }

    /** Registers a hook that sends a full sync; it runs on login, respawn and dimension change. */
    public static void registerFullSync(Consumer<ServerPlayer> hook) {
        FULL_SYNC_HOOKS.add(hook);
    }

    public static void markDirty(ServerPlayer player, Type type) {
        DIRTY.computeIfAbsent(player.getUUID(), id -> EnumSet.noneOf(Type.class)).add(type);
    }

    public static boolean isDirty(ServerPlayer player, Type type) {
        EnumSet<Type> set = DIRTY.get(player.getUUID());
        return set != null && set.contains(type);
    }

    /** Sends deltas for every dirty type of the given players and clears their flags. */
    public static void flush(Iterable<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            EnumSet<Type> set = DIRTY.remove(player.getUUID());
            if (set == null) {
                continue;
            }
            for (Type type : set) {
                Consumer<ServerPlayer> sender = DELTA_SENDERS.get(type);
                if (sender != null) {
                    sender.accept(player);
                }
            }
        }
    }

    /** Runs every full-sync hook for the player; pending deltas are dropped because the full state covers them. */
    public static void fullSync(ServerPlayer player) {
        DIRTY.remove(player.getUUID());
        for (Consumer<ServerPlayer> hook : FULL_SYNC_HOOKS) {
            hook.accept(player);
        }
    }

    /**
     * Sends a payload unless the player is a fake player or the connection lacks the channel
     * (fake and mock players in tests have no real connection).
     */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.isFakePlayer() || player.connection == null || !player.connection.hasChannel(payload.type())) {
            return;
        }
        PacketDistributor.sendToPlayer(player, payload);
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (!DIRTY.isEmpty()) {
            flush(event.getServer().getPlayerList().getPlayers());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            fullSync(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            fullSync(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            fullSync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DIRTY.remove(event.getEntity().getUUID());
    }
}
