package io.github.sethwillemsvg.lotrrevival.progression.achievement;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.event.AlignmentEvent;
import io.github.sethwillemsvg.lotrrevival.core.event.PledgeEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Game-bus listeners that keep the earned set and the achievement count current. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class AchievementEvents {
    private AchievementEvents() {}

    /** Fired by vanilla only for advancements with a display block; kept for completeness (idempotent). */
    @SubscribeEvent
    public static void onEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isOurs(event)) {
            AchievementService.onEarned(player, event.getAdvancement());
        }
    }

    /** The reliable signal for our displayless advancements: a grant that completes the advancement. */
    @SubscribeEvent
    public static void onProgress(AdvancementEvent.AdvancementProgressEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isOurs(event)) {
            return;
        }
        boolean done = event.getAdvancementProgress().isDone();
        if (event.getProgressType() == AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT) {
            if (done) {
                AchievementService.onEarned(player, event.getAdvancement());
            }
        } else if (!done) {
            AchievementService.onRevoked(player, event.getAdvancement().id());
        }
    }

    private static boolean isOurs(AdvancementEvent event) {
        return event.getAdvancement().id().getNamespace().equals(LOTRRevival.MOD_ID);
    }

    @SubscribeEvent
    public static void onAlignmentChanged(AlignmentEvent.Changed event) {
        AchievementService.markDirty(event.getPlayer());
    }

    /** Last, so a pledge another listener cancelled does not mark anything. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPledge(PledgeEvent.Pledge event) {
        if (!event.isCanceled()) {
            AchievementService.markDirty(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPledgeBreak(PledgeEvent.Break event) {
        AchievementService.markDirty(event.getPlayer());
    }

    /** Datapack reload (and join): entries and advancements may have changed. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(AchievementService::rescan);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AchievementService.forget(player);
        }
    }
}
