package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import io.github.sethwillemsvg.lotrrevival.core.api.WaypointView;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** {@code /lotr waypoint list|unlock|lock|clock}. Travel and custom waypoint commands arrive with later tickets. */
public final class WaypointCommands {
    private static final DynamicCommandExceptionType UNKNOWN_REGION = new DynamicCommandExceptionType(
            id -> Component.translatable("commands.lotrrevival.waypoint.region.unknown", id));

    private static final DynamicCommandExceptionType BAD_REF = new DynamicCommandExceptionType(
            text -> Component.translatable("commands.lotrrevival.waypoint.travel.bad_ref", text));

    private WaypointCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr")
                .then(Commands.literal("waypoint")
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .executes(ctx -> list(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(regionChange("unlock", true))
                        .then(regionChange("lock", false))
                        .then(Commands.literal("clock")
                                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument(
                                                        "ticks", IntegerArgumentType.integer(0, TravelData.MAX_CLOCK))
                                                .executes(WaypointCommands::clock))))
                        .then(CustomWaypointCommands.node())
                        .then(Commands.literal("travel")
                                .then(Commands.argument("ref", StringArgumentType.greedyString())
                                        .suggests(WaypointCommands::suggestRefs)
                                        .executes(WaypointCommands::travel)))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRefs(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        List<String> refs = new ArrayList<>();
        ctx.getSource()
                .registryAccess()
                .registryOrThrow(ModDataRegistries.<Waypoint>waypoint())
                .keySet()
                .forEach(id -> refs.add(new WaypointRef.Fixed(id).wire()));
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            TravelData.get(player).custom().forEach(custom -> refs.add(new WaypointRef.Custom(custom.id()).wire()));
        }
        return SharedSuggestionProvider.suggest(refs, builder);
    }

    /** {@code /lotr waypoint travel <ref>}: same rules as the map page request. A bare id means a mod waypoint. */
    private static int travel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String text = StringArgumentType.getString(ctx, "ref").trim();
        WaypointRef ref = WaypointRef.tryParse(text).orElse(null);
        if (ref == null) {
            ResourceLocation id = Waypoint.modId(text);
            ref = id == null ? null : new WaypointRef.Fixed(id);
        }
        if (ref == null) {
            throw BAD_REF.create(text);
        }
        // Refusals are shown to the player by the travel code; the command only reports whether a countdown began.
        return io.github.sethwillemsvg.lotrrevival.world.travel.FastTravel.request(player, ref).started() ? 1 : 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> regionChange(String name, boolean unlock) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("region", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    List<String> options = new ArrayList<>(List.of("all"));
                                    ctx.getSource()
                                            .registryAccess()
                                            .registryOrThrow(ModDataRegistries.<WaypointRegion>waypointRegion())
                                            .keySet()
                                            .forEach(id -> options.add(id.getPath()));
                                    return SharedSuggestionProvider.suggest(options, builder);
                                })
                                .executes(ctx -> changeRegions(ctx, unlock))));
    }

    private static int changeRegions(CommandContext<CommandSourceStack> ctx, boolean unlock)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        String text = StringArgumentType.getString(ctx, "region");
        Registry<WaypointRegion> registry =
                source.registryAccess().registryOrThrow(ModDataRegistries.<WaypointRegion>waypointRegion());
        Set<ResourceLocation> targets = new HashSet<>();
        if (text.equals("all")) {
            targets.addAll(registry.keySet());
        } else {
            ResourceLocation id = Waypoint.modId(text);
            if (id == null || !registry.containsKey(id)) {
                throw UNKNOWN_REGION.create(text);
            }
            targets.add(id);
        }
        for (ServerPlayer player : players) {
            TravelData data = TravelData.get(player);
            Set<ResourceLocation> regions = new HashSet<>(data.regions());
            if (unlock) {
                regions.addAll(targets);
            } else {
                regions.removeAll(targets);
            }
            TravelData.set(player, data.withRegions(regions));
            io.github.sethwillemsvg.lotrrevival.world.network.TravelSync.send(player);
        }
        String key = unlock ? "commands.lotrrevival.waypoint.unlock" : "commands.lotrrevival.waypoint.lock";
        source.sendSuccess(
                () -> Component.translatable(key, targets.size(), players.size()), true);
        return players.size();
    }

    private static int clock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        for (ServerPlayer player : players) {
            TravelData.set(player, TravelData.get(player).withClock(ticks));
            io.github.sethwillemsvg.lotrrevival.world.network.TravelSync.send(player);
        }
        ctx.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                "commands.lotrrevival.waypoint.clock", players.size(), format(ticks)),
                        true);
        return players.size();
    }

    private static int list(CommandSourceStack source, ServerPlayer player) {
        TravelData data = TravelData.get(player);
        var access = source.registryAccess();
        Registry<Waypoint> waypoints = access.registryOrThrow(ModDataRegistries.<Waypoint>waypoint());

        source.sendSuccess(
                () -> Component.translatable(
                        "commands.lotrrevival.waypoint.list.header",
                        player.getDisplayName(),
                        format(data.clock()),
                        data.regions().size()),
                false);
        List<ResourceLocation> ids = new ArrayList<>(waypoints.keySet());
        ids.sort(null);
        int shown = 0;
        for (ResourceLocation id : ids) {
            Waypoint waypoint = waypoints.get(id);
            TravelRules.Status status = TravelRules.status(waypoint, player);
            if (!status.listed()) {
                continue;
            }
            double distance = Math.hypot(player.getX() - waypoint.blockX(), player.getZ() - waypoint.blockZ());
            int required = TravelRules.requiredTicks(data.usesOf(id), distance);
            Component state = Component.translatable("waypoint.state.lotrrevival." + stateName(status.state()));
            if (status.barredBy().isPresent()) {
                state = Component.translatable(
                        "message.lotrrevival.waypoint_barred", Factions.displayName(status.barredBy().get()));
            }
            Component line = Component.translatable(
                    "commands.lotrrevival.waypoint.list.entry", waypoint.name(), id.getPath(), state, format(required));
            source.sendSuccess(() -> line, false);
            shown++;
        }
        for (TravelData.Custom custom : data.custom()) {
            Component line = Component.translatable(
                    "commands.lotrrevival.waypoint.list.custom",
                    custom.id(),
                    custom.name(),
                    custom.pos().toShortString(),
                    custom.uses());
            source.sendSuccess(() -> line, false);
            shown++;
        }
        return shown;
    }

    private static String stateName(WaypointView.State state) {
        return switch (state) {
            case LOCKED -> "locked";
            case USABLE -> "usable";
            case BARRED -> "barred";
        };
    }

    /** {@code mm:ss} for a number of ticks (minutes may exceed 59). */
    public static String format(int ticks) {
        int seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
