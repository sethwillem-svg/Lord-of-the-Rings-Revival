package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.interact.NpcInteractEvent;
import io.github.sethwillemsvg.lotrrevival.progression.quest.event.QuestCompletedEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Game-bus listeners of the quest offer system. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class OfferEvents {
    private OfferEvents() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof NpcEntity npc && npc.level() instanceof ServerLevel level) {
            OfferTicker.tick(level, npc);
        }
    }

    /**
     * Talk interaction. Only {@code TALK} is ours; trading and hiring keep their own flow (a trader's
     * sneak-right-click arrives here as TALK). Cancels the event when the chain took over.
     */
    @SubscribeEvent
    public static void onInteract(NpcInteractEvent event) {
        if (event.getKind() == NpcInteractEvent.Kind.TALK && QuestInteraction.handle(event.getNpc(), event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /** A giver died: its holders' quests fail and its offer goes away. Skipped when something cancelled the death. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof NpcEntity npc && !npc.level().isClientSide) {
            OfferService.onGiverDeath(npc);
        } else if (event.getEntity() instanceof ServerPlayer player) {
            OfferViewers.removePlayer(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof NpcEntity npc && event.getEntity() instanceof ServerPlayer player) {
            OfferMarkers.sendTo(player, npc);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OfferViewers.removePlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        OfferViewers.removePlayer(event.getEntity().getUUID());
    }

    /** Lowest, so rewards other listeners add are settled; the award itself does not depend on them. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onQuestCompleted(QuestCompletedEvent event) {
        FirstCompletion.award(event.getPlayer(), event.getQuest().group());
    }
}
