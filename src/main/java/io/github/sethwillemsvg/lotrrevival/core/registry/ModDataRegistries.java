package io.github.sethwillemsvg.lotrrevival.core.registry;

import com.mojang.serialization.Codec;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Declares the mod's datapack registries (keys and codecs). Owned by the Core pod.
 *
 * <p>Registries whose value types live in another pod have their keys reserved here (so every pod
 * can reference them) and are bound to a codec by the owning pod through
 * {@link #registerSynced(ResourceKey, Codec)}, called from that pod's {@code init}.
 */
public final class ModDataRegistries {
    /** {@code lotrrevival:faction}, files at {@code data/<ns>/lotrrevival/faction/<id>.json}. */
    public static final ResourceKey<Registry<Faction>> FACTION =
            ResourceKey.createRegistryKey(LOTRRevival.id("faction"));

    /** Hook registrations, drained when {@link DataPackRegistryEvent.NewRegistry} fires. */
    private static final List<Pending<?>> PENDING = new ArrayList<>();

    private static boolean frozen;

    private ModDataRegistries() {}

    // Progression-owned registries. The value type is chosen by the caller, who also supplies the codec.

    /** {@code lotrrevival:achievement}, files at {@code data/<ns>/lotrrevival/achievement/<tab>/<name>.json}. */
    public static <T> ResourceKey<Registry<T>> achievement() {
        return ResourceKey.createRegistryKey(LOTRRevival.id("achievement"));
    }

    /** {@code lotrrevival:title}. */
    public static <T> ResourceKey<Registry<T>> title() {
        return ResourceKey.createRegistryKey(LOTRRevival.id("title"));
    }

    /** {@code lotrrevival:quest_group}. */
    public static <T> ResourceKey<Registry<T>> questGroup() {
        return ResourceKey.createRegistryKey(LOTRRevival.id("quest_group"));
    }

    /** {@code lotrrevival:mini_quest}. */
    public static <T> ResourceKey<Registry<T>> miniQuest() {
        return ResourceKey.createRegistryKey(LOTRRevival.id("mini_quest"));
    }

    // World-owned registries (waypoints and roads), bound by the World pod through registerSynced.

    /** {@code lotrrevival:waypoint}. */
    public static <T> ResourceKey<Registry<T>> waypoint() {
        return ResourceKey.createRegistryKey(LOTRRevival.id("waypoint"));
    }

    /** {@code lotrrevival:waypoint_region}. */
    public static <T> ResourceKey<Registry<T>> waypointRegion() {
        return ResourceKey.createRegistryKey(LOTRRevival.id("waypoint_region"));
    }

    /** {@code lotrrevival:road}. */
    public static <T> ResourceKey<Registry<T>> road() {
        return ResourceKey.createRegistryKey(LOTRRevival.id("road"));
    }

    /**
     * Registers a datapack registry that is synced to clients (the codec doubles as network codec).
     * Pods call this from their {@code init}, before {@code DataPackRegistryEvent.NewRegistry} fires.
     *
     * @throws IllegalStateException if called too late or twice for the same key
     */
    public static synchronized <T> void registerSynced(ResourceKey<Registry<T>> key, Codec<T> codec) {
        if (frozen) {
            throw new IllegalStateException("Datapack registries are frozen; register " + key.location() + " in init");
        }
        for (Pending<?> pending : PENDING) {
            if (pending.key().equals(key)) {
                throw new IllegalStateException("Datapack registry already registered: " + key.location());
            }
        }
        PENDING.add(new Pending<>(key, codec));
    }

    /** Whether {@code key} was bound to a codec through {@link #registerSynced}. */
    public static synchronized boolean isRegistered(ResourceKey<? extends Registry<?>> key) {
        return PENDING.stream().anyMatch(pending -> pending.key().equals(key));
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModDataRegistries::onNewRegistry);
    }

    private static synchronized void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        // The same codec is used as the network codec so clients receive factions.
        event.dataPackRegistry(FACTION, Faction.CODEC, Faction.CODEC);
        for (Pending<?> pending : PENDING) {
            pending.register(event);
        }
        frozen = true;
    }

    private record Pending<T>(ResourceKey<Registry<T>> key, Codec<T> codec) {
        void register(DataPackRegistryEvent.NewRegistry event) {
            event.dataPackRegistry(key, codec, codec);
        }
    }
}
