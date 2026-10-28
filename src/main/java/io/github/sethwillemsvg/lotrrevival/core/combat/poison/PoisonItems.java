package io.github.sethwillemsvg.lotrrevival.core.combat.poison;

import com.mojang.serialization.Codec;
import io.github.sethwillemsvg.lotrrevival.core.combat.CombatItems;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Poison (specs/core/combat-items.md, "Poison"): the bottle of poison, the {@code lotrrevival:poisoned}
 * mark, the special recipe that applies it, and the hit listener ({@link PoisonHits}).
 */
public final class PoisonItems {
    private PoisonItems() {}

    /** Stack size 1, not drinkable (poisoned drinks are a brewing follow-up). Empties to a glass bottle. */
    public static final DeferredItem<Item> BOTTLE_OF_POISON = ModRegistries.ITEMS.registerItem(
            "bottle_of_poison",
            Item::new,
            new Item.Properties().stacksTo(1).craftRemainder(Items.GLASS_BOTTLE));

    /** Permanent mark on a poisoned weapon. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> POISONED =
            ModRegistries.DATA_COMPONENTS.registerComponentType(
                    "poisoned",
                    builder -> builder.persistent(Codec.unit(Unit.INSTANCE))
                            .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PoisonWeaponRecipe>> POISON_WEAPON_SERIALIZER =
            ModRegistries.RECIPE_SERIALIZERS.register(
                    "poison_weapon", () -> new SimpleCraftingRecipeSerializer<>(PoisonWeaponRecipe::new));

    /** Whether the stack carries the poisoned mark. */
    public static boolean isPoisoned(ItemStack stack) {
        return stack.has(POISONED.get());
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(PoisonItems::onBuildCreativeTabs);
        NeoForge.EVENT_BUS.register(PoisonHits.class);
        NeoForge.EVENT_BUS.register(PoisonTooltip.class);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CombatItems.MATERIALS_TAB_KEY) {
            event.accept(new ItemStack(BOTTLE_OF_POISON.get()));
        }
    }
}
