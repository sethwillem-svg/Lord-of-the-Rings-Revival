package io.github.sethwillemsvg.lotrrevival.client.npc;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client registration for npc entities. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID, value = Dist.CLIENT)
public final class NpcClient {
    private NpcClient() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NpcEntityTypes.HOBBIT.get(), context -> new NpcHumanoidRenderer<>(context, "hobbit"));
    }
}
