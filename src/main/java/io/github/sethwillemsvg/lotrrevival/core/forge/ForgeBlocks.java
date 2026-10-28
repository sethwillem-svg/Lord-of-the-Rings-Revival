package io.github.sethwillemsvg.lotrrevival.core.forge;

import io.github.sethwillemsvg.lotrrevival.core.combat.CombatItems;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import java.util.List;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Registry handles for the forges: blocks, items, the shared block entity type and menu type. World structures place
 * {@link #ALLOY_FORGE} and {@link #ORC_FORGE}; both are in the block tag {@code lotrrevival:forges/any}.
 */
public final class ForgeBlocks {
    private ForgeBlocks() {}

    public static final DeferredBlock<ForgeBlock> ALLOY_FORGE = forge(ForgeVariant.ALLOY);
    public static final DeferredBlock<ForgeBlock> ORC_FORGE = forge(ForgeVariant.ORC);

    public static final DeferredItem<BlockItem> ALLOY_FORGE_ITEM = item(ForgeVariant.ALLOY, ALLOY_FORGE);
    public static final DeferredItem<BlockItem> ORC_FORGE_ITEM = item(ForgeVariant.ORC, ORC_FORGE);

    /** Every forge block, in creative-tab order. */
    public static final List<DeferredBlock<ForgeBlock>> ALL = List.of(ALLOY_FORGE, ORC_FORGE);
    public static final List<DeferredItem<BlockItem>> ALL_ITEMS = List.of(ALLOY_FORGE_ITEM, ORC_FORGE_ITEM);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForgeBlockEntity>> BLOCK_ENTITY =
            ModRegistries.BLOCK_ENTITY_TYPES.register(
                    "forge",
                    () -> BlockEntityType.Builder.of(
                                    ForgeBlockEntity::new, ALLOY_FORGE.get(), ORC_FORGE.get())
                            .build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<ForgeMenu>> MENU =
            ModRegistries.MENU_TYPES.register("forge", () -> IMenuTypeExtension.create(ForgeMenu::new));

    private static DeferredBlock<ForgeBlock> forge(ForgeVariant variant) {
        return ModRegistries.BLOCKS.register(
                variant.id(),
                () -> new ForgeBlock(
                        BlockBehaviour.Properties.ofFullCopy(Blocks.FURNACE)
                                .lightLevel(state -> state.getValue(ForgeBlock.LIT) ? ForgeBlock.LIT_LIGHT : 0),
                        variant));
    }

    private static DeferredItem<BlockItem> item(ForgeVariant variant, DeferredBlock<ForgeBlock> block) {
        return ModRegistries.ITEMS.register(variant.id(), () -> new BlockItem(block.get(), new Item.Properties()));
    }

    /** The blocks of all forges. */
    public static List<Block> blocks() {
        return ALL.stream().<Block>map(DeferredBlock::get).toList();
    }

    public static void init(IEventBus modBus) {
        ForgingRecipes.init();
        modBus.addListener(ForgeBlocks::onBuildCreativeTabs);
        modBus.addListener(ForgeBlocks::onRegisterCapabilities);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || event.getTabKey() == CombatItems.MATERIALS_TAB_KEY) {
            ALL_ITEMS.forEach(item -> event.accept(item.get()));
        }
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLOCK_ENTITY.get(), (forge, side) -> forge.handlerFor(side));
    }
}
