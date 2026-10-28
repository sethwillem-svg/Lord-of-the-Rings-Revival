package io.github.sethwillemsvg.lotrrevival.core.brewing;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Brewing barrel block, item, block entity type and menu type. */
public final class BrewingBlocks {
    private BrewingBlocks() {}

    public static final DeferredBlock<BrewingBarrelBlock> BARREL = ModRegistries.BLOCKS.register(
            "brewing_barrel",
            () -> new BrewingBarrelBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(3.0F, 5.0F)
                    .sound(SoundType.WOOD)));

    public static final DeferredItem<BrewingBarrelItem> BARREL_ITEM = ModRegistries.ITEMS.register(
            "brewing_barrel", () -> new BrewingBarrelItem(BARREL.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BrewingBarrelBlockEntity>> BARREL_BE =
            ModRegistries.BLOCK_ENTITY_TYPES.register(
                    "brewing_barrel",
                    () -> BlockEntityType.Builder.of(BrewingBarrelBlockEntity::new, BARREL.get())
                            .build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<BrewingBarrelMenu>> BARREL_MENU =
            ModRegistries.MENU_TYPES.register(
                    "brewing_barrel", () -> IMenuTypeExtension.create(BrewingBarrelMenu::new));

    static void init() {}
}
