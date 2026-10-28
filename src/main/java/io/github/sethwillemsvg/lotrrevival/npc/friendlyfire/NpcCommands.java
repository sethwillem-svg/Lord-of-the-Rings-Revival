package io.github.sethwillemsvg.lotrrevival.npc.friendlyfire;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /lotr npc friendlyfire <on|off>}, merged into core's {@code lotr} root by Brigadier. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class NpcCommands {
    private NpcCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(Commands.literal("lotr")
                        .then(Commands.literal("npc")
                                .then(Commands.literal("friendlyfire")
                                        .then(Commands.literal("on").executes(ctx -> setFriendlyFire(ctx, true)))
                                        .then(Commands.literal("off").executes(ctx -> setFriendlyFire(ctx, false))))));
    }

    private static int setFriendlyFire(CommandContext<CommandSourceStack> ctx, boolean allow)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        NpcPlayerPrefs.set(player, NpcPlayerPrefs.get(player).withAllowFriendlyFire(allow));
        ctx.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                allow
                                        ? "commands.lotrrevival.npc.friendlyfire.on"
                                        : "commands.lotrrevival.npc.friendlyfire.off"),
                        false);
        return 1;
    }
}
