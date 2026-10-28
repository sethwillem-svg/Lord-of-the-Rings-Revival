package io.github.sethwillemsvg.lotrrevival.world;

import com.mojang.brigadier.CommandDispatcher;
import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Placeholder portal: {@code /lotrrevival middleearth} (permission level 2) teleports to the Middle-earth
 * landing point, or back to the overworld spawn when run inside Middle-earth. Replaced by the portal spec.
 */
public final class MiddleEarthCommand {
    private MiddleEarthCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotrrevival")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("middleearth").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    return toggle(player) ? 1 : 0;
                })));
    }

    private static boolean toggle(ServerPlayer player) {
        var server = player.server;
        if (player.level().dimension().equals(ModDimensions.MIDDLE_EARTH)) {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            var spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0);
            return true;
        }
        ServerLevel middleEarth = server.getLevel(ModDimensions.MIDDLE_EARTH);
        if (middleEarth == null) {
            player.sendSystemMessage(Component.literal("The Middle-earth dimension is not loaded."));
            return false;
        }
        var landing = MiddleEarthLanding.load(server.getResourceManager());
        // Generates the chunk if needed so the player is placed on the highest solid block.
        int y = WorldHeights.surface(middleEarth, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, landing.x(), landing.z());
        Vec3 pos = new Vec3(landing.x() + 0.5, y, landing.z() + 0.5);
        player.teleportTo(middleEarth, pos.x, pos.y, pos.z, player.getYRot(), 0);
        return true;
    }
}
