package io.github.sethwillemsvg.lotrrevival.core.material;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Ore and storage blocks of the new metals (spec: combat-items, "Metals, ores and smelting"). Ore placement in the
 * world belongs to the World pod; this holder only registers the blocks.
 */
public final class MaterialBlocks {
    private MaterialBlocks() {}

    public static final Map<Metal, DeferredBlock<Block>> ORE = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredBlock<Block>> DEEPSLATE_ORE = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredBlock<Block>> RAW_BLOCK = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredBlock<Block>> BLOCK = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredItem<BlockItem>> ORE_ITEM = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredItem<BlockItem>> DEEPSLATE_ORE_ITEM = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredItem<BlockItem>> RAW_BLOCK_ITEM = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredItem<BlockItem>> BLOCK_ITEM = new EnumMap<>(Metal.class);

    static {
        for (Metal metal : Metal.values()) {
            String id = metal.id();
            if (metal.hasOre()) {
                register(ORE, ORE_ITEM, metal, id + "_ore", oreProperties(metal, false));
                register(DEEPSLATE_ORE, DEEPSLATE_ORE_ITEM, metal, "deepslate_" + id + "_ore", oreProperties(metal, true));
                register(RAW_BLOCK, RAW_BLOCK_ITEM, metal, "raw_" + id + "_block",
                        BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_IRON_BLOCK));
            }
            if (metal.hasIngot()) {
                register(BLOCK, BLOCK_ITEM, metal, id + "_block", blockProperties(metal));
            }
        }
    }

    private static void register(
            Map<Metal, DeferredBlock<Block>> blocks,
            Map<Metal, DeferredItem<BlockItem>> items,
            Metal metal,
            String name,
            BlockBehaviour.Properties properties) {
        DeferredBlock<Block> block = ModRegistries.BLOCKS.registerSimpleBlock(name, properties);
        blocks.put(metal, block);
        items.put(metal, ModRegistries.ITEMS.registerSimpleBlockItem(name, block));
    }

    private static BlockBehaviour.Properties oreProperties(Metal metal, boolean deepslate) {
        return switch (metal) {
            case TIN -> BlockBehaviour.Properties.ofFullCopy(deepslate ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE);
            case MORGUL_IRON -> BlockBehaviour.Properties.ofFullCopy(deepslate ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE);
            case SILVER -> BlockBehaviour.Properties.ofFullCopy(deepslate ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE);
            // Ours: hardness 5, diamond tier.
            case MITHRIL -> BlockBehaviour.Properties.ofFullCopy(deepslate ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE)
                    .strength(5.0f, 6.0f);
            default -> throw new IllegalArgumentException(metal + " has no ore");
        };
    }

    private static BlockBehaviour.Properties blockProperties(Metal metal) {
        BlockBehaviour.Properties base = BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK);
        return metal == Metal.MITHRIL ? base.strength(5.0f, 6.0f) : base;
    }

    /** Every block in this holder (ores, deepslate ores, raw blocks, storage blocks). */
    public static java.util.List<DeferredBlock<Block>> all() {
        java.util.List<DeferredBlock<Block>> all = new java.util.ArrayList<>();
        all.addAll(ORE.values());
        all.addAll(DEEPSLATE_ORE.values());
        all.addAll(RAW_BLOCK.values());
        all.addAll(BLOCK.values());
        return all;
    }

    /** Forces class load so the registrations exist before the registries fire. */
    public static void init() {}
}
