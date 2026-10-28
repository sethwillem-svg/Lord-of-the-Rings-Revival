package io.github.sethwillemsvg.lotrrevival.client.core.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearCatalog;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearEntry;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearKind;
import io.github.sethwillemsvg.lotrrevival.core.combat.item.FactionBow;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.PebbleTypes;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client registration for the ranged weapons: the pebble renderer and the bow pull model predicates. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
public final class RangedClient {
    private RangedClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PebbleTypes.PEBBLE.get(), ThrownItemRenderer::new);
    }

    /** Vanilla only registers {@code pull} and {@code pulling} for its own bow, so every bow in the catalog needs them. */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (GearEntry entry : GearCatalog.entries()) {
                if (entry.kind() == GearKind.BOW) {
                    registerBow(entry.item().get());
                }
            }
        });
    }

    private static void registerBow(Item bow) {
        ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) -> {
            if (entity == null || entity.getUseItem() != stack) {
                return 0.0F;
            }
            int used = stack.getUseDuration(entity) - entity.getUseItemRemainingTicks();
            return bow instanceof FactionBow faction
                    ? faction.drawFraction(used)
                    : (float) used / BowItem.MAX_DRAW_DURATION;
        });
        ItemProperties.register(
                bow,
                ResourceLocation.withDefaultNamespace("pulling"),
                (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
    }
}
