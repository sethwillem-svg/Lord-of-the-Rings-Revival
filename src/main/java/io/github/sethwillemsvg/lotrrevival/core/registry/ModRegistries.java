package io.github.sethwillemsvg.lotrrevival.core.registry;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The single home for every DeferredRegister in the mod. Owned by the Core pod.
 *
 * <p>Pods add entries in their own classes (for example {@code world.ModBiomeBlocks})
 * that reference these registers. They do not create new DeferredRegisters.
 */
public final class ModRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LOTRRevival.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LOTRRevival.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LOTRRevival.MOD_ID);

    private ModRegistries() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
