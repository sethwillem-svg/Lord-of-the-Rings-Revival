package io.github.sethwillemsvg.lotrrevival.core.building;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The Arnor stone brick set: plain, mossy and cracked bricks, each with slab and stairs. All have the strength and
 * sound of vanilla stone bricks. Kept in its own holder so other core item sets merge cleanly.
 */
public final class ArnorBlocks {
    private ArnorBlocks() {}

    /** One variant of the set: the full block plus its slab and stairs, and their block items. */
    public record Variant(
            String name,
            DeferredBlock<Block> block,
            DeferredBlock<SlabBlock> slab,
            DeferredBlock<StairBlock> stairs,
            DeferredItem<BlockItem> blockItem,
            DeferredItem<BlockItem> slabItem,
            DeferredItem<BlockItem> stairsItem) {}

    public static final Variant ARNOR_BRICK = variant("arnor_brick");
    public static final Variant MOSSY_ARNOR_BRICK = variant("mossy_arnor_brick");
    public static final Variant CRACKED_ARNOR_BRICK = variant("cracked_arnor_brick");

    /** All three variants, in creative-tab order. */
    public static final List<Variant> VARIANTS = List.of(ARNOR_BRICK, MOSSY_ARNOR_BRICK, CRACKED_ARNOR_BRICK);

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS);
    }

    private static Variant variant(String name) {
        DeferredBlock<Block> block = ModRegistries.BLOCKS.register(name, () -> new Block(props()));
        DeferredBlock<SlabBlock> slab = ModRegistries.BLOCKS.register(name + "_slab", () -> new SlabBlock(props()));
        DeferredBlock<StairBlock> stairs = ModRegistries.BLOCKS.register(
                name + "_stairs", () -> new StairBlock(block.get().defaultBlockState(), props()));
        return new Variant(
                name,
                block,
                slab,
                stairs,
                ModRegistries.ITEMS.registerSimpleBlockItem(block),
                ModRegistries.ITEMS.registerSimpleBlockItem(slab),
                ModRegistries.ITEMS.registerSimpleBlockItem(stairs));
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ArnorBlocks::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (Variant v : VARIANTS) {
                event.accept(new ItemStack((Item) v.blockItem().get()));
                event.accept(new ItemStack((Item) v.slabItem().get()));
                event.accept(new ItemStack((Item) v.stairsItem().get()));
            }
        }
    }
}
