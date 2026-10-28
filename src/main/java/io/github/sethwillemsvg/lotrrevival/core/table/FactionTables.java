package io.github.sethwillemsvg.lotrrevival.core.table;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** The five faction crafting tables (blocks, items, shared menu type) and the faction recipe registrations. */
public final class FactionTables {
    private FactionTables() {}

    /** One table: registry name, faction, and its block and item. */
    public record Entry(
            String name, ResourceKey<Faction> faction, DeferredBlock<FactionTableBlock> block, DeferredItem<BlockItem> item) {}

    public static final Entry HOBBIT = table("hobbit_crafting_table", Factions.HOBBIT);
    public static final Entry BREE = table("bree_crafting_table", Factions.BREE);
    public static final Entry RANGER = table("ranger_crafting_table", Factions.DUNEDAIN_NORTH);
    public static final Entry GONDOR = table("gondor_crafting_table", Factions.GONDOR);
    public static final Entry MORDOR = table("mordor_crafting_table", Factions.MORDOR);

    /** All tables, in creative-tab order. */
    public static final List<Entry> ALL = List.of(HOBBIT, BREE, RANGER, GONDOR, MORDOR);

    public static final DeferredHolder<MenuType<?>, MenuType<FactionTableMenu>> MENU =
            ModRegistries.MENU_TYPES.register("faction_table", () -> IMenuTypeExtension.create(FactionTableMenu::new));

    private static Entry table(String name, ResourceKey<Faction> faction) {
        DeferredBlock<FactionTableBlock> block = ModRegistries.BLOCKS.register(
                name,
                () -> new FactionTableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE), faction));
        DeferredItem<BlockItem> item = ModRegistries.ITEMS.register(
                name, () -> new BlockItem(block.get(), new Item.Properties()));
        return new Entry(name, faction, block, item);
    }

    public static void init(IEventBus modBus) {
        FactionTableRecipes.init();
        modBus.addListener(FactionTables::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            for (Entry entry : ALL) {
                event.accept(new ItemStack(entry.item().get()));
            }
        }
    }

    /** The blocks of all tables. */
    public static List<Block> blocks() {
        return ALL.stream().map(entry -> (Block) entry.block().get()).toList();
    }
}
