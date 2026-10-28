package io.github.sethwillemsvg.lotrrevival.core.registry;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/** Declares the mod's datapack registries (keys and codecs). Owned by the Core pod. */
public final class ModDataRegistries {
    /** {@code lotrrevival:faction}, files at {@code data/<ns>/lotrrevival/faction/<id>.json}. */
    public static final ResourceKey<Registry<Faction>> FACTION =
            ResourceKey.createRegistryKey(LOTRRevival.id("faction"));

    private ModDataRegistries() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModDataRegistries::onNewRegistry);
    }

    private static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        // The same codec is used as the network codec so clients receive factions.
        event.dataPackRegistry(FACTION, Faction.CODEC, Faction.CODEC);
    }
}
