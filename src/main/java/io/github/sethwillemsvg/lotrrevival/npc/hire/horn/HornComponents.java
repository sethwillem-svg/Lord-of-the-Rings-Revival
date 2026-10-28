package io.github.sethwillemsvg.lotrrevival.npc.hire.horn;

import com.mojang.serialization.Codec;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Data components of the command horn and the command sword. */
public final class HornComponents {
    /** {@code lotrrevival:horn_mode}: the mode of a command horn. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HornMode>> HORN_MODE =
            ModRegistries.DATA_COMPONENTS.registerComponentType(
                    "horn_mode", builder -> builder.persistent(HornMode.CODEC).networkSynchronized(HornMode.STREAM_CODEC));

    /** {@code lotrrevival:squadron}: the squadron a horn or sword commands (empty or absent: no squadron). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SQUADRON =
            ModRegistries.DATA_COMPONENTS.registerComponentType(
                    "squadron",
                    builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private HornComponents() {}

    public static HornMode modeOf(ItemStack stack) {
        HornMode mode = stack.get(HORN_MODE.get());
        return mode == null ? HornMode.BLANK : mode;
    }

    /** The stack's squadron, trimmed and capped; empty when it has none. */
    public static String squadronOf(ItemStack stack) {
        String squadron = stack.get(SQUADRON.get());
        return squadron == null ? "" : Squadrons.clean(squadron);
    }

    /** Touch so the class loads before registration. */
    static void init() {}
}
