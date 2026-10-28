package io.github.sethwillemsvg.lotrrevival.core.brewing;

import com.mojang.serialization.Codec;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Data components used by the brewing system. */
public final class BrewingComponents {
    private BrewingComponents() {}

    /** Strength tier of a filled drink, int 0-4 (see {@link DrinkTier}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> DRINK_STRENGTH =
            ModRegistries.DATA_COMPONENTS.registerComponentType(
                    "drink_strength",
                    builder -> builder.persistent(Codec.intRange(DrinkTier.MIN_INDEX, DrinkTier.MAX_INDEX))
                            .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Tier stored on a stack, or {@link DrinkTier#DEFAULT} when the stack has no component. */
    public static DrinkTier tierOf(ItemStack stack) {
        Integer index = stack.get(DRINK_STRENGTH.get());
        return index == null ? DrinkTier.DEFAULT : DrinkTier.byIndex(index);
    }

    /** Sets the strength tier on a stack. */
    public static void setTier(ItemStack stack, DrinkTier tier) {
        stack.set(DRINK_STRENGTH.get(), tier.index());
    }

    static void init() {}
}
