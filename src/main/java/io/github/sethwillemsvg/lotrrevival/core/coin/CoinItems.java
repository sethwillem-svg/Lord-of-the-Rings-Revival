package io.github.sethwillemsvg.lotrrevival.core.coin;

import io.github.sethwillemsvg.lotrrevival.core.item.CoreItems;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The higher silver coin denominations. The 1-piece coin stays in {@link CoreItems#SILVER_COIN}; this holder
 * keeps the new registrations separate so parallel item work merges cleanly. Values and the tag live in
 * {@link Coins}.
 */
public final class CoinItems {
    private CoinItems() {}

    /** Worth 10 silver coins. Member of {@code #lotrrevival:coins}. */
    public static final DeferredItem<Item> SILVER_COIN_10 =
            ModRegistries.ITEMS.registerSimpleItem("silver_coin_10", new Item.Properties());

    /** Worth 100 silver coins. Member of {@code #lotrrevival:coins}. */
    public static final DeferredItem<Item> SILVER_COIN_100 =
            ModRegistries.ITEMS.registerSimpleItem("silver_coin_100", new Item.Properties());

    public static void init(IEventBus modBus) {
        modBus.addListener(CoinItems::onBuildCreativeTabs);
        // The tooltip event only ever fires on the client; registering it on the common bus is harmless elsewhere.
        NeoForge.EVENT_BUS.addListener(CoinItems::onTooltip);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(new ItemStack(SILVER_COIN_10.get()));
            event.accept(new ItemStack(SILVER_COIN_100.get()));
        }
    }

    private static void onTooltip(ItemTooltipEvent event) {
        int unit = Coins.unitValue(event.getItemStack().getItem());
        if (unit > 0) {
            event.getToolTip().add(Component.translatable("tooltip.lotrrevival.coin_value", unit)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
