package io.github.sethwillemsvg.lotrrevival.npc.hire.horn;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** The command horn and command sword, registered through core's item register from the npc pod. */
public final class HornItems {
    public static final DeferredItem<CommandHornItem> COMMAND_HORN =
            ModRegistries.ITEMS.register("command_horn", () -> new CommandHornItem(new Item.Properties()));
    public static final DeferredItem<CommandSwordItem> COMMAND_SWORD =
            ModRegistries.ITEMS.register("command_sword", () -> new CommandSwordItem(new Item.Properties()));

    private HornItems() {}

    /** Called from {@code NpcItems.init}. */
    public static void init(IEventBus modBus) {
        HornComponents.init();
        modBus.addListener(HornItems::onBuildCreativeTabs);
        modBus.addListener(HornPayloads::register);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(new ItemStack(COMMAND_HORN.get()));
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(new ItemStack(COMMAND_SWORD.get()));
        }
    }
}
