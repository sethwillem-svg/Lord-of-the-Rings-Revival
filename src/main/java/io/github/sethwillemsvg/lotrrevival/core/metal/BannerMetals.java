package io.github.sethwillemsvg.lotrrevival.core.metal;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Bronze and silver ingots and storage blocks, used as banner bases (Progression tags the blocks into
 * {@code #lotrrevival:banner_base/bronze} and {@code /silver}). Kept in its own holder; the metals ticket may
 * register the same ids, in which case Opus 1 dedupes.
 */
public final class BannerMetals {
    private BannerMetals() {}

    public static final DeferredItem<Item> BRONZE_INGOT =
            ModRegistries.ITEMS.registerSimpleItem("bronze_ingot", new Item.Properties());
    public static final DeferredItem<Item> SILVER_INGOT =
            ModRegistries.ITEMS.registerSimpleItem("silver_ingot", new Item.Properties());

    public static final DeferredBlock<Block> BRONZE_BLOCK = ModRegistries.BLOCKS.registerSimpleBlock(
            "bronze_block",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL));
    public static final DeferredBlock<Block> SILVER_BLOCK = ModRegistries.BLOCKS.registerSimpleBlock(
            "silver_block",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL));

    public static final DeferredItem<BlockItem> BRONZE_BLOCK_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem(BRONZE_BLOCK);
    public static final DeferredItem<BlockItem> SILVER_BLOCK_ITEM =
            ModRegistries.ITEMS.registerSimpleBlockItem(SILVER_BLOCK);

    public static void init(IEventBus modBus) {
        modBus.addListener(BannerMetals::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(new ItemStack(BRONZE_INGOT.get()));
            event.accept(new ItemStack(SILVER_INGOT.get()));
        } else if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(new ItemStack(BRONZE_BLOCK_ITEM.get()));
            event.accept(new ItemStack(SILVER_BLOCK_ITEM.get()));
        }
    }
}
