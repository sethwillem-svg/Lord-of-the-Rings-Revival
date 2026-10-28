package io.github.sethwillemsvg.lotrrevival.progression.title;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.event.AlignmentEvent;
import io.github.sethwillemsvg.lotrrevival.core.event.PledgeEvent;
import io.github.sethwillemsvg.lotrrevival.progression.api.AchievementCountChangedEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Game-bus listeners that keep the selected title valid and add the chat prefix. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class TitleEvents {
    private TitleEvents() {}

    /** Before the full sync (which runs at low priority) so the client never sees a title that is already gone. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TitleService.recheck(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TitleService.recheck(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TitleService.forget(player);
        }
    }

    @SubscribeEvent
    public static void onAlignmentChanged(AlignmentEvent.Changed event) {
        TitleService.markDirty(event.getPlayer());
    }

    /** Last, so a pledge another listener cancelled does not mark anything. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPledge(PledgeEvent.Pledge event) {
        if (!event.isCanceled()) {
            TitleService.markDirty(event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPledgeBreak(PledgeEvent.Break event) {
        TitleService.markDirty(event.getPlayer());
    }

    @SubscribeEvent
    public static void onAchievementCount(AchievementCountChangedEvent event) {
        TitleService.markDirty(event.getPlayer());
    }

    /** Grants and revokes of our advancements: an achievement title may depend on them. */
    @SubscribeEvent
    public static void onAdvancementProgress(AdvancementEvent.AdvancementProgressEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getAdvancement().id().getNamespace().equals(LOTRRevival.MOD_ID)) {
            TitleService.markDirty(player);
        }
    }

    /** Datapack reload (and join): definitions may have changed or vanished. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(TitleService::markDirty);
    }

    /**
     * Shows {@code [Title] <Name> message}. Chat formatting only lets a mod change the message part, so a
     * titled message is cancelled and broadcast as one line; messages from players without a title stay
     * vanilla.
     */
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        Component line = TitleService.chatLine(player, event.getMessage()).orElse(null);
        MinecraftServer server = player.getServer();
        if (line == null || server == null) {
            return;
        }
        event.setCanceled(true);
        server.getPlayerList().broadcastSystemMessage(line, false);
    }
}
