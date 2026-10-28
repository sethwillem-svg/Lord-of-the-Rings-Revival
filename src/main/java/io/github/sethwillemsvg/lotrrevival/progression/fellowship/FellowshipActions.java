package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import io.github.sethwillemsvg.lotrrevival.progression.network.FellowshipActionPayload;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/** Dispatches a client {@code fellowship_action} to the same {@link Fellowships} methods the commands use. */
public final class FellowshipActions {
    private FellowshipActions() {}

    /** Runs the action for the player and sends the outcome to them. Malformed requests are refused with a line. */
    public static FellowshipResult perform(ServerPlayer player, FellowshipActionPayload payload) {
        FellowshipResult result = run(player, payload);
        player.sendSystemMessage(result.message());
        return result;
    }

    private static FellowshipResult run(ServerPlayer player, FellowshipActionPayload payload) {
        MinecraftServer server = player.getServer();
        String argument = payload.argument().trim();
        UUID id = payload.fellowship();
        return switch (payload.action()) {
            case CREATE -> Fellowships.create(player, argument, false);
            case INVITE -> withPlayer(server, argument, target -> Fellowships.invite(player, id, target));
            case ACCEPT -> Fellowships.accept(player, id);
            case REJECT -> Fellowships.reject(player, id);
            case LEAVE -> Fellowships.leave(player, id);
            case REMOVE -> withPlayer(server, argument, target -> Fellowships.remove(player, id, target));
            case PROMOTE -> withPlayer(server, argument, target -> Fellowships.promote(player, id, target));
            case DEMOTE -> withPlayer(server, argument, target -> Fellowships.demote(player, id, target));
            case TRANSFER -> withPlayer(server, argument, target -> Fellowships.transfer(player, id, target));
            case RENAME -> Fellowships.rename(player, id, argument);
            case DISBAND -> Fellowships.disband(player, id);
            case ICON -> Fellowships.setIcon(player, id, player.getItemInHand(InteractionHand.MAIN_HAND));
            case OPTION -> {
                Optional<FellowshipOption> option = FellowshipOption.byId(argument);
                yield option.isPresent()
                        ? Fellowships.setOption(player, id, option.get(), payload.value())
                        : FellowshipResult.fail("fellowship.lotrrevival.error.unknown_option", argument);
            }
        };
    }

    private static FellowshipResult withPlayer(
            MinecraftServer server, String name, java.util.function.Function<UUID, FellowshipResult> action) {
        Optional<UUID> target = Fellowships.resolvePlayer(server, name);
        if (target.isEmpty()) {
            return new FellowshipResult(false, Component.translatable("fellowship.lotrrevival.error.unknown_player", name));
        }
        return action.apply(target.get());
    }
}
