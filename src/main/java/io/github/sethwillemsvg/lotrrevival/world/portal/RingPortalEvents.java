package io.github.sethwillemsvg.lotrrevival.world.portal;

import com.mojang.brigadier.CommandDispatcher;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Game-bus listeners of the ring portal: the catalyst check, the guard cleanup and {@code /lotr portal}. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class RingPortalEvents {
    private RingPortalEvents() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        // Item first: this runs for every entity every tick.
        if (event.getEntity() instanceof ItemEntity item && !item.level().isClientSide) {
            RingPortals.tryOpen(item);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RingPortalTravel.guard().clear();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr")
                .then(Commands.literal("portal")
                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource())))
                        .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))));
    }

    private static int reset(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        RingPortals.setData(level, RingPortals.data(level).reset());
        source.sendSuccess(() -> Component.translatable("commands.lotrrevival.portal.reset"), true);
        return 1;
    }

    private static int info(CommandSourceStack source) {
        RingPortalData data = RingPortals.data(source.getServer().overworld());
        source.sendSuccess(
                () -> Component.translatable("commands.lotrrevival.portal.info.flag", String.valueOf(data.made())),
                false);
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.lotrrevival.portal.info.overworld", describe(data.overworldPortal())),
                false);
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.lotrrevival.portal.info.middle_earth", describe(data.middleEarthPortal())),
                false);
        return 1;
    }

    private static Component describe(Optional<BlockPos> pos) {
        return pos.map(p -> (Component) Component.literal(p.getX() + " " + p.getY() + " " + p.getZ()))
                .orElseGet(() -> Component.translatable("commands.lotrrevival.portal.info.none"));
    }
}
