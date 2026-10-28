package io.github.sethwillemsvg.lotrrevival.progression.banner;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Registrations of the faction banner: two blocks, one block entity type, one item. */
public final class BannerBlocks {
    public static final DeferredBlock<FactionBannerBlock> STANDING = ModRegistries.BLOCKS.register(
            "faction_banner",
            () -> new FactionBannerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final DeferredBlock<FactionWallBannerBlock> WALL = ModRegistries.BLOCKS.register(
            "faction_wall_banner",
            () -> new FactionWallBannerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(1.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .noCollission()
                    .lootFrom(STANDING)));

    public static final DeferredItem<FactionBannerItem> ITEM = ModRegistries.ITEMS.register(
            "faction_banner",
            () -> new FactionBannerItem(STANDING.get(), WALL.get(), new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactionBannerBlockEntity>> BLOCK_ENTITY =
            ModRegistries.BLOCK_ENTITY_TYPES.register(
                    "faction_banner",
                    () -> BlockEntityType.Builder.of(FactionBannerBlockEntity::new, STANDING.get(), WALL.get())
                            .build(null));

    private BannerBlocks() {}

    static void init(IEventBus modBus) {
        BannerComponents.init();
        modBus.addListener(BannerBlocks::onBuildCreativeTabs);
    }

    /** One stack per design, in the functional blocks tab. */
    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            for (ResourceLocation design : BannerDesign.defaultIds()) {
                event.accept(stackOf(design));
            }
        }
    }

    /** A banner item of the given design. */
    public static ItemStack stackOf(ResourceLocation design) {
        ItemStack stack = new ItemStack(ITEM.get());
        stack.set(BannerComponents.DESIGN.get(), design);
        return stack;
    }
}
