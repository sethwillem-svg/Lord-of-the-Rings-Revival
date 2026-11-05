package io.github.sethwillemsvg.lotrrevival.events;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** {@code /lotr_revival event list | start <type> [player] | end <id> | governor} (op level 2). */
public final class EventCommands {
    private EventCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lotr_revival").requires(s -> s.hasPermission(2)).then(Commands.literal("event")
                .then(Commands.literal("list").executes(EventCommands::list))
                .then(Commands.literal("start").then(Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(WorldEventManager.TYPES.keySet().stream().map(ResourceLocation::getPath), b))
                        .executes(c -> start(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> start(c, EntityArgument.getPlayer(c, "player"))))))
                .then(Commands.literal("end").then(Commands.argument("id", StringArgumentType.word()).executes(EventCommands::end)))
                .then(Commands.literal("governor").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("governor " + LoadGovernor.state() + " avg " + String.format("%.1f", LoadGovernor.lastMs())
                            + " ms, chunks " + EventChunkTickets.used() + "/" + EventsConfig.i(EventsConfig.CHUNK_BUDGET)), false);
                    return 1;
                }))));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        int n = 0;
        for (WorldEvent e : WorldEventManager.active()) {
            n++;
            String line = e.shortId() + " " + e.type.getPath() + " at " + e.centre.toShortString() + " lead=" + e.leadFactions + "(" + e.count(EventParticipant.LEAD)
                    + ") opp=" + e.oppFactions + "(" + e.count(EventParticipant.OPPOSITION) + ") owner=" + e.owner.toString().substring(0, 8)
                    + " players=" + e.players.size();
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        int total = n;
        c.getSource().sendSuccess(() -> Component.literal(total + " active event(s)"), false);
        return n;
    }

    private static int start(CommandContext<CommandSourceStack> c, ServerPlayer p) throws CommandSyntaxException {
        String t = StringArgumentType.getString(c, "type");
        ResourceLocation id = t.contains(":") ? ResourceLocation.tryParse(t) : LotrRevival.id(t);
        if (!WorldEventManager.TYPES.containsKey(id)) {
            c.getSource().sendFailure(Component.literal("unknown event type " + t));
            return 0;
        }
        WorldEvent ev = EventScheduler.stage(p, id);
        c.getSource().sendSuccess(() -> Component.literal(ev == null ? "no event staged (see the events log)"
                : "started " + ev.shortId() + " at " + ev.centre.toShortString()), true);
        return ev == null ? 0 : 1;
    }

    private static int end(CommandContext<CommandSourceStack> c) {
        WorldEvent ev = WorldEventManager.byShortId(StringArgumentType.getString(c, "id"));
        if (ev == null) {
            c.getSource().sendFailure(Component.literal("no active event with that id"));
            return 0;
        }
        WorldEventManager.end(c.getSource().getServer(), ev, null, true, "operator command");
        c.getSource().sendSuccess(() -> Component.literal("ended " + ev.shortId() + ": " + ev.outcome), true);
        return 1;
    }
}
