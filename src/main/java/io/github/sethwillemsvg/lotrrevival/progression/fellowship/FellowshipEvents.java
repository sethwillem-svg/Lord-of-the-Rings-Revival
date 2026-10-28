package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus listeners of the fellowship system: names, online markers, pending-invite reminder, positions, commands. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class FellowshipEvents {
    private FellowshipEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        FellowshipData data = FellowshipData.get(server);
        data.rememberName(player.getUUID(), player.getGameProfile().getName());
        FellowshipSync.markCoMembersDirty(server, player.getUUID());
        int pending = data.invitesFor(player.getUUID()).size();
        if (pending > 0) {
            player.sendSystemMessage(Component.translatable("fellowship.lotrrevival.notice.pending_invites", pending));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }
        FellowshipSync.markCoMembersDirty(player.getServer(), player.getUUID());
        FellowshipSync.forget(player.getUUID());
        FellowshipPositions.forget(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        FellowshipPositions.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FellowshipCommands.register(event.getDispatcher());
    }
}
