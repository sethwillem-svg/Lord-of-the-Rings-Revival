package io.github.sethwillemsvg.lotrrevival.npc.hire.ai;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** The teleport check of following units. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class UnitAiEvents {
    private UnitAiEvents() {}

    /** Every {@value UnitAi#TELEPORT_INTERVAL} ticks: a following unit that cannot walk to a far owner teleports. */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof NpcEntity npc)
                || npc.level().isClientSide
                || npc.tickCount % UnitAi.TELEPORT_INTERVAL != UnitAi.TELEPORT_INTERVAL / 2
                || !npc.isHired()) {
            return;
        }
        ServerPlayer owner = UnitAi.presentOwner(npc);
        if (owner != null && UnitAi.shouldTeleport(npc, owner)) {
            UnitAi.teleportNear(npc, owner);
        }
    }
}
