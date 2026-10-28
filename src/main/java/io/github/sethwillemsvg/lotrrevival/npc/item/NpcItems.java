package io.github.sethwillemsvg.lotrrevival.npc.item;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** NPC spawn eggs, registered through core's item register. */
public final class NpcItems {
    private NpcItems() {}

    public static final DeferredItem<DeferredSpawnEggItem> HOBBIT_SPAWN_EGG = ModRegistries.ITEMS.register(
            "hobbit_spawn_egg",
            () -> new DeferredSpawnEggItem(NpcEntityTypes.HOBBIT, 0x6B8E3A, 0xC9A27A, new Item.Properties()));

    public static void init(IEventBus modBus) {
        modBus.addListener(NpcItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(new ItemStack(HOBBIT_SPAWN_EGG.get()));
        }
    }
}
