package io.github.sethwillemsvg.lotrrevival.core.building;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** The gold ring: the catalyst the world pod's ring portal looks for. Fire-resistant so it survives fire and lava. */
public final class GoldRingItems {
    private GoldRingItems() {}

    public static final DeferredItem<Item> GOLD_RING =
            ModRegistries.ITEMS.registerSimpleItem("gold_ring", new Item.Properties().stacksTo(16).fireResistant());

    public static void init(IEventBus modBus) {
        modBus.addListener(GoldRingItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(new ItemStack(GOLD_RING.get()));
        }
    }
}
