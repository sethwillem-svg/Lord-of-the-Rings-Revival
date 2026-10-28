package io.github.sethwillemsvg.lotrrevival.npc.hire;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Game-bus listeners of the hiring system. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class HireEvents {
    private HireEvents() {}

    /** Desertion check every {@link HiredUnits#DESERTION_INTERVAL} ticks for hired units. */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof NpcEntity npc
                && !npc.level().isClientSide
                && npc.isHired()
                && npc.tickCount % HiredUnits.DESERTION_INTERVAL == 0) {
            HiredUnits.checkDesertion(npc);
        }
    }
}
