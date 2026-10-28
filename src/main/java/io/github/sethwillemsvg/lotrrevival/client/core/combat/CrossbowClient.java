package io.github.sethwillemsvg.lotrrevival.client.core.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.CrossbowGear;
import io.github.sethwillemsvg.lotrrevival.core.combat.item.GearCrossbow;
import io.github.sethwillemsvg.lotrrevival.core.combat.ranged.BoltEntities;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client registration for crossbows and bolts: the bolt renderer and the item model predicates the
 * crossbow models need ({@code pull}, {@code pulling}, {@code charged}, {@code firework}), which
 * vanilla registers only for its own crossbow. {@code pull} uses our 50-tick charge time.
 */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
public final class CrossbowClient {
    private CrossbowClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BoltEntities.BOLT.get(), BoltRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            registerPredicates(CrossbowGear.BRONZE_CROSSBOW.get());
            registerPredicates(CrossbowGear.IRON_CROSSBOW.get());
        });
    }

    private static void registerPredicates(Item item) {
        ItemProperties.register(item, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) -> {
            if (entity == null || !(stack.getItem() instanceof GearCrossbow crossbow) || CrossbowItem.isCharged(stack)) {
                return 0.0F;
            }
            return (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks())
                    / (float) crossbow.chargeDuration(stack, entity);
        });
        ItemProperties.register(item, ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !CrossbowItem.isCharged(stack)
                        ? 1.0F
                        : 0.0F);
        ItemProperties.register(item, ResourceLocation.withDefaultNamespace("charged"), (stack, level, entity, seed) ->
                CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
        ItemProperties.register(item, ResourceLocation.withDefaultNamespace("firework"), (stack, level, entity, seed) -> {
            ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
            return charged != null && charged.contains(Items.FIREWORK_ROCKET) ? 1.0F : 0.0F;
        });
    }
}
