package io.github.sethwillemsvg.lotrrevival.core.item;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** Small shared Core items that no pod owns. */
public final class CoreItems {
    private CoreItems() {}

    /** Currency placeholder; member of {@code #lotrrevival:coins}. Traders and NPC loot use it. */
    public static final DeferredItem<Item> SILVER_COIN =
            ModRegistries.ITEMS.registerSimpleItem("silver_coin", new Item.Properties());

    public static void init(IEventBus modBus) {
        modBus.addListener(CoreItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(new ItemStack(SILVER_COIN.get()));
        }
    }
}
