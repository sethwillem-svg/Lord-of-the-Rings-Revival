package io.github.sethwillemsvg.lotrrevival.nodemap.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapModule;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapSettings;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.PathResult;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeAccess;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapAttachment;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapData;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelCost;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lotr_revival node ...} (spec Commands). Node refs are written as resource locations: {@code lotr_revival:bree}
 * (or bare {@code bree}) for a fixed node, {@code camp:3} for camp 3.
 */
public final class NodeCommands {
    private NodeCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal(LotrRevival.MOD_ID).then(Commands.literal("node")
                .then(Commands.literal("list")
                        .executes(c -> list(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(c -> list(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("discover").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("node", ResourceLocationArgument.id()).suggests((c, b) -> suggestNodes(b))
                                        .executes(c -> discover(c, true)))))
                .then(Commands.literal("forget").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("node", ResourceLocationArgument.id()).suggests((c, b) -> suggestNodes(b))
                                        .executes(c -> discover(c, false)))))
                .then(Commands.literal("cooldown").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0)).executes(NodeCommands::cooldown))))
                .then(Commands.literal("travel")
                        .then(Commands.argument("node", ResourceLocationArgument.id()).suggests((c, b) -> suggestNodes(b))
                                .executes(c -> {
                                    NodeMapModule.TRAVEL.request(c.getSource().getPlayerOrException(), ref(c, "node"));
                                    return 1;
                                })))
                .then(Commands.literal("camp")
                        .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(c -> NodeMapModule.CAMPS.create(c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "name")).ok() ? 1 : 0)))
                        .then(Commands.literal("rename").then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> NodeMapModule.CAMPS.rename(c.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(c, "id"), StringArgumentType.getString(c, "name")).ok() ? 1 : 0))))
                        .then(Commands.literal("delete").then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(c -> NodeMapModule.CAMPS.delete(c.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(c, "id")).ok() ? 1 : 0))))
                .then(Commands.literal("path").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("from", ResourceLocationArgument.id()).suggests((c, b) -> suggestNodes(b))
                                .then(Commands.argument("to", ResourceLocationArgument.id()).suggests((c, b) -> suggestNodes(b))
                                        .executes(c -> path(c, c.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(c -> path(c, EntityArgument.getPlayer(c, "player")))))))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestNodes(
            com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        List<String> ids = new ArrayList<>();
        for (GraphNode n : NodeMapModule.graph().nodes()) ids.add(n.ref().fixed().toString());
        return SharedSuggestionProvider.suggest(ids, b);
    }

    /** {@code camp:N} is a camp; a bare path (minecraft namespace) means lotr_revival; anything else a fixed node. */
    static NodeRef ref(CommandContext<CommandSourceStack> c, String arg) {
        ResourceLocation id = ResourceLocationArgument.getId(c, arg);
        if (id.getNamespace().equals("camp")) {
            try {
                return NodeRef.camp(Integer.parseInt(id.getPath()));
            } catch (NumberFormatException e) {
                return NodeRef.camp(-1);
            }
        }
        if (id.getNamespace().equals("minecraft")) id = LotrRevival.id(id.getPath());
        return NodeRef.fixed(id);
    }

    private static int list(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        NodeAccess acc = new NodeAccess(NodeMapModule.LIVE, p);
        List<String> known = new ArrayList<>(), barred = new ArrayList<>(), rumoured = new ArrayList<>();
        for (GraphNode n : acc.graph.nodes()) {
            switch (acc.state(n)) {
                case KNOWN -> known.add(n.ref().shortName());
                case BARRED -> barred.add(n.ref().shortName());
                case RUMOURED -> rumoured.add(n.ref().shortName());
                default -> { }
            }
        }
        NodeMapData d = acc.data;
        List<String> camps = d.camps().stream().map(k -> k.id() + ":" + k.name() + "@" + k.pos().toShortString()).toList();
        c.getSource().sendSuccess(() -> Component.literal(p.getScoreboardName() + ": known " + known + "; rumoured " + rumoured
                + "; barred " + barred + "; camps " + camps + "; cooldown " + TravelCost.clock(d.cooldown()) + "; trips " + d.trips()), false);
        return known.size();
    }

    private static int discover(CommandContext<CommandSourceStack> c, boolean add) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        ResourceLocation id = ResourceLocationArgument.getId(c, "node");
        boolean all = id.getPath().equals("all");
        int n = 0;
        for (ServerPlayer p : players) {
            NodeMapData d = NodeMapAttachment.get(p);
            if (all) {
                if (add) for (GraphNode g : NodeMapModule.graph().nodes()) n += d.addKnown(g.ref().fixed()) ? 1 : 0;
                else {
                    n += d.known().size();
                    d.forgetAll();
                }
            } else {
                NodeRef r = ref(c, "node");
                if (r.isCamp()) continue;
                n += (add ? d.addKnown(r.fixed()) : d.forget(r.fixed())) ? 1 : 0;
            }
            NodeMapModule.LIVE.onChange.accept(p);
        }
        int changed = n;
        c.getSource().sendSuccess(() -> Component.literal((add ? "Discovered " : "Forgot ") + changed + " node entries for "
                + players.size() + " player(s)"), true);
        return changed;
    }

    private static int cooldown(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int t = IntegerArgumentType.getInteger(c, "ticks");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");
        for (ServerPlayer p : players) {
            NodeMapAttachment.get(p).setCooldown(t);
            NodeMapModule.LIVE.onChange.accept(p);
        }
        c.getSource().sendSuccess(() -> Component.literal("Travel cooldown set to " + t + " ticks for " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int path(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        NodeAccess acc = new NodeAccess(NodeMapModule.LIVE, p);
        PathResult r = acc.path(ref(c, "from"), ref(c, "to"));
        NodeMapSettings s = NodeMapModule.LIVE.settings.get();
        String extra = r.found() ? " cooldown=" + s.cost().cooldown(r.length()) + " ticks (" + TravelCost.clock(s.cost().cooldown(r.length()))
                + ") exhaustion=" + s.cost().exhaustion(r.length()) : "";
        c.getSource().sendSuccess(() -> Component.literal(r.describe() + extra), false);
        return r.found() ? 1 : 0;
    }
}
