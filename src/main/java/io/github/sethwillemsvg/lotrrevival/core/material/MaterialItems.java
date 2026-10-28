package io.github.sethwillemsvg.lotrrevival.core.material;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** Raw items, ingots and nuggets of the new metals, plus the creative-tab wiring for them and their blocks. */
public final class MaterialItems {
    private MaterialItems() {}

    public static final Map<Metal, DeferredItem<Item>> RAW = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredItem<Item>> INGOT = new EnumMap<>(Metal.class);
    public static final Map<Metal, DeferredItem<Item>> NUGGET = new EnumMap<>(Metal.class);

    static {
        for (Metal metal : Metal.values()) {
            String id = metal.id();
            if (metal.hasOre()) {
                RAW.put(metal, ModRegistries.ITEMS.registerSimpleItem("raw_" + id, new Item.Properties()));
            }
            if (metal.hasIngot()) {
                INGOT.put(metal, ModRegistries.ITEMS.registerSimpleItem(id + "_ingot", new Item.Properties()));
                NUGGET.put(metal, ModRegistries.ITEMS.registerSimpleItem(id + "_nugget", new Item.Properties()));
            }
        }
    }

    public static void init(IEventBus modBus) {
        MaterialBlocks.init();
        modBus.addListener(MaterialItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for (Metal metal : Metal.values()) {
                accept(event, RAW.get(metal));
                accept(event, INGOT.get(metal));
                accept(event, NUGGET.get(metal));
            }
        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            for (Metal metal : Metal.values()) {
                if (MaterialBlocks.ORE_ITEM.containsKey(metal)) {
                    event.accept(MaterialBlocks.ORE_ITEM.get(metal).get());
                    event.accept(MaterialBlocks.DEEPSLATE_ORE_ITEM.get(metal).get());
                    event.accept(MaterialBlocks.RAW_BLOCK_ITEM.get(metal).get());
                }
            }
        } else if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (Metal metal : Metal.values()) {
                if (MaterialBlocks.BLOCK_ITEM.containsKey(metal)) {
                    event.accept(MaterialBlocks.BLOCK_ITEM.get(metal).get());
                }
            }
        }
    }

    private static void accept(BuildCreativeModeTabContentsEvent event, DeferredItem<Item> item) {
        if (item != null) {
            event.accept(new ItemStack(item.get()));
        }
    }
}
