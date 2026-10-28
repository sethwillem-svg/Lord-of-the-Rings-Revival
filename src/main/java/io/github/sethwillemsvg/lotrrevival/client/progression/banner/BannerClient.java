package io.github.sethwillemsvg.lotrrevival.client.progression.banner;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerBlocks;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerComponents;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerDesign;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Client registration of the faction banner: block entity renderer and item tint. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
public final class BannerClient {
    private BannerClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BannerBlocks.BLOCK_ENTITY.get(), FactionBannerRenderer::new);
    }

    /** The item icon is grey; tint it with the cloth colour of its design. */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> {
                    if (tintIndex != 0 || Minecraft.getInstance().level == null) {
                        return 0xFFFFFFFF;
                    }
                    ResourceLocation id = stack.get(BannerComponents.DESIGN.get());
                    if (id == null) {
                        return 0xFFFFFFFF;
                    }
                    BannerDesign design = Minecraft.getInstance()
                            .level
                            .registryAccess()
                            .registry(BannerDesign.REGISTRY)
                            .map(registry -> registry.get(id))
                            .orElse(null);
                    return design == null ? 0xFFFFFFFF : 0xFF000000 | design.color();
                },
                BannerBlocks.ITEM.get());
    }
}
