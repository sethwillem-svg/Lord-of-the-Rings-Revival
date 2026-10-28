package io.github.sethwillemsvg.lotrrevival.progression.bounty;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** Items of the bounty system. */
public final class BountyItems {
    /** {@code lotrrevival:bounty_trophy}: cosmetic reward for every fifth completed bounty (art in a later ticket). */
    public static final DeferredItem<Item> BOUNTY_TROPHY =
            ModRegistries.ITEMS.registerSimpleItem("bounty_trophy", new Item.Properties().stacksTo(64));

    private BountyItems() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(BountyItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(new ItemStack(BOUNTY_TROPHY.get()));
        }
    }
}
