package io.github.sethwillemsvg.lotrrevival.core.brewing;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/** Wooden mug and the five pilot drinks. */
public final class BrewingItems {
    private BrewingItems() {}

    public static final DeferredItem<Item> WOODEN_MUG =
            ModRegistries.ITEMS.registerSimpleItem("wooden_mug", new Item.Properties());

    public static final DeferredItem<DrinkItem> ALE = drink("ale", 0.3, 3, 0.3f, List.of());
    public static final DeferredItem<DrinkItem> CIDER = drink("cider", 0.3, 4, 0.4f, List.of());
    public static final DeferredItem<DrinkItem> MEAD = drink("mead", 0.6, 4, 0.4f, List.of());
    public static final DeferredItem<DrinkItem> DWARVEN_TONIC = drink(
            "dwarven_tonic",
            0.2,
            4,
            0.4f,
            List.of(new DrinkItem.DrinkEffect(MobEffects.NIGHT_VISION, 4800, 0)));
    public static final DeferredItem<DrinkItem> MIRUVOR = drink(
            "miruvor",
            0.0,
            8,
            0.8f,
            List.of(
                    new DrinkItem.DrinkEffect(MobEffects.DAMAGE_BOOST, 800, 0),
                    new DrinkItem.DrinkEffect(MobEffects.MOVEMENT_SPEED, 800, 0)));

    /** All filled drinks, in creative-tab order. */
    public static final List<DeferredItem<DrinkItem>> DRINKS = List.of(ALE, CIDER, MEAD, DWARVEN_TONIC, MIRUVOR);

    private static DeferredItem<DrinkItem> drink(
            String name, double alcohol, int nutrition, float saturation, List<DrinkItem.DrinkEffect> effects) {
        Supplier<DrinkItem> factory =
                () -> new DrinkItem(new Item.Properties().stacksTo(1), alcohol, nutrition, saturation, effects);
        return ModRegistries.ITEMS.register(name, factory);
    }

    public static void init(IEventBus modBus) {
        BrewingComponents.init();
        BrewingBlocks.init();
        BrewingRecipes.init();
        modBus.addListener(BrewingItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(new ItemStack(BrewingBlocks.BARREL_ITEM.get()));
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(new ItemStack(WOODEN_MUG.get()));
            for (DeferredItem<DrinkItem> drink : DRINKS) {
                event.accept(new ItemStack(drink.get()));
            }
        }
    }
}
