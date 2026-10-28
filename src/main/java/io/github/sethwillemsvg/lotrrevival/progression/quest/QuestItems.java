package io.github.sethwillemsvg.lotrrevival.progression.quest;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** Items of the quest system. */
public final class QuestItems {
    /** {@code lotrrevival:quest_book}: using it opens the quest log screen on the client. */
    public static final DeferredItem<Item> QUEST_BOOK = ModRegistries.ITEMS.registerItem(
            "quest_book", QuestBookItem::new, new Item.Properties().stacksTo(1));

    private QuestItems() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(QuestItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(new ItemStack(QUEST_BOOK.get()));
        }
    }
}
