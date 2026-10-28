package io.github.sethwillemsvg.lotrrevival.progression.banner;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Data components of the banner item. */
public final class BannerComponents {
    /** {@code lotrrevival:banner_design}: the id of a {@code banner_design} entry. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> DESIGN =
            ModRegistries.DATA_COMPONENTS.registerComponentType(
                    "banner_design",
                    builder -> builder.persistent(ResourceLocation.CODEC)
                            .networkSynchronized(ResourceLocation.STREAM_CODEC));

    /** {@code lotrrevival:banner_protection}: the settings carried when an owner picks a banner up. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BannerSettings>> PROTECTION =
            ModRegistries.DATA_COMPONENTS.registerComponentType(
                    "banner_protection",
                    builder -> builder.persistent(BannerSettings.CODEC)
                            .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(BannerSettings.CODEC)));

    private BannerComponents() {}

    /** The design on a stack, or {@code fallback} when it has none. */
    public static ResourceLocation designOf(ItemStack stack, ResourceLocation fallback) {
        ResourceLocation design = stack.get(DESIGN.get());
        return design == null ? fallback : design;
    }

    /** Touch from the banner system init so the class loads before registration. */
    static void init() {}
}
