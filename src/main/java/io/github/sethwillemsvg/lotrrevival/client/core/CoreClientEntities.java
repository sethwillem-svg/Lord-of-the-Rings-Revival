package io.github.sethwillemsvg.lotrrevival.client.core;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrowingEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client registration of the core entity renderers (thrown spears and axes). */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
public final class CoreClientEntities {
    private CoreClientEntities() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ThrowingEntities.THROWN_SPEAR.get(), context -> new ThrownGearRenderer(context, false));
        event.registerEntityRenderer(ThrowingEntities.THROWN_AXE.get(), context -> new ThrownGearRenderer(context, true));
    }
}
