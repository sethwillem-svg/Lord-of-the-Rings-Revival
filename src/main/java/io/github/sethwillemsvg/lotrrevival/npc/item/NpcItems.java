package io.github.sethwillemsvg.lotrrevival.npc.item;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** NPC spawn eggs, registered through core's item register. */
public final class NpcItems {
    private NpcItems() {}

    public static final DeferredItem<DeferredSpawnEggItem> HOBBIT_SPAWN_EGG = ModRegistries.ITEMS.register(
            "hobbit_spawn_egg",
            () -> new DeferredSpawnEggItem(NpcEntityTypes.HOBBIT, 0x6B8E3A, 0xC9A27A, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> HOBBIT_BOUNDER_SPAWN_EGG = egg(
            "hobbit_bounder_spawn_egg", NpcEntityTypes.HOBBIT_BOUNDER, 0x5B7F3A, 0xC9A27A);
    public static final DeferredItem<DeferredSpawnEggItem> HOBBIT_SHIRRIFF_SPAWN_EGG = egg(
            "hobbit_shirriff_spawn_egg", NpcEntityTypes.HOBBIT_SHIRRIFF, 0x8A2F2F, 0xC9A27A);
    public static final DeferredItem<DeferredSpawnEggItem> BREE_HOBBIT_SPAWN_EGG = egg(
            "bree_hobbit_spawn_egg", NpcEntityTypes.BREE_HOBBIT, 0x7A5A3A, 0xC9A27A);
    public static final DeferredItem<DeferredSpawnEggItem> BREE_MAN_SPAWN_EGG = egg(
            "bree_man_spawn_egg", NpcEntityTypes.BREE_MAN, 0x7A5A3A, 0xD1A98A);
    public static final DeferredItem<DeferredSpawnEggItem> BREE_GUARD_SPAWN_EGG = egg(
            "bree_guard_spawn_egg", NpcEntityTypes.BREE_GUARD, 0x3E5C8A, 0xD1A98A);

    private static DeferredItem<DeferredSpawnEggItem> egg(
            String id,
            DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type,
            int background,
            int highlight) {
        return ModRegistries.ITEMS.register(
                id, () -> new DeferredSpawnEggItem(type, background, highlight, new Item.Properties()));
    }

    /** All npc spawn eggs. */
    public static List<DeferredItem<DeferredSpawnEggItem>> spawnEggs() {
        return List.of(
                HOBBIT_SPAWN_EGG,
                HOBBIT_BOUNDER_SPAWN_EGG,
                HOBBIT_SHIRRIFF_SPAWN_EGG,
                BREE_HOBBIT_SPAWN_EGG,
                BREE_MAN_SPAWN_EGG,
                BREE_GUARD_SPAWN_EGG);
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(NpcItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            spawnEggs().forEach(egg -> event.accept(new ItemStack(egg.get())));
        }
    }
}
