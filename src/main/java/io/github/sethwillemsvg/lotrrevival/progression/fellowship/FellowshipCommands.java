package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.sethwillemsvg.lotrrevival.progression.title.TitleService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /fellowship ...}, {@code /fchat <name> <message>} and the op command {@code /lotrfellowship}. Fellowship
 * names may hold spaces, so they are quoted-string arguments; every action calls {@link Fellowships}.
 */
public final class FellowshipCommands {
    private static final DynamicCommandExceptionType NOT_IN = new DynamicCommandExceptionType(
            name -> Component.translatable("fellowship.lotrrevival.error.not_in_named", name));
    private static final DynamicCommandExceptionType NO_INVITE = new DynamicCommandExceptionType(
            name -> Component.translatable("fellowship.lotrrevival.error.no_invite", name));
    private static final DynamicCommandExceptionType UNKNOWN_PLAYER = new DynamicCommandExceptionType(
            name -> Component.translatable("fellowship.lotrrevival.error.unknown_player", name));
    private static final DynamicCommandExceptionType UNKNOWN_OPTION = new DynamicCommandExceptionType(
            name -> Component.translatable("fellowship.lotrrevival.error.unknown_option", name));
    private static final SimpleCommandExceptionType UNKNOWN_FELLOWSHIP =
            new SimpleCommandExceptionType(Component.translatable("fellowship.lotrrevival.error.unknown"));

    private static final SuggestionProvider<CommandSourceStack> MY_FELLOWSHIPS = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return builder.buildFuture();
        }
        List<String> names = new ArrayList<>();
        for (Fellowship fellowship : FellowshipData.get(ctx.getSource().getServer()).of(player.getUUID())) {
            names.add(quote(fellowship.name()));
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> MY_INVITES = (ctx, builder) -> {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            return builder.buildFuture();
        }
        List<String> names = new ArrayList<>();
        for (Fellowship fellowship : FellowshipData.get(ctx.getSource().getServer()).invitesFor(player.getUUID())) {
            names.add(quote(fellowship.name()));
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> PLAYER_NAMES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), builder);

    private static final SuggestionProvider<CommandSourceStack> OPTIONS = (ctx, builder) -> {
        List<String> ids = new ArrayList<>();
        for (FellowshipOption option : FellowshipOption.values()) {
            ids.add(option.id());
        }
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private FellowshipCommands() {}

    private static String quote(String name) {
        return name.indexOf(' ') >= 0 ? "\"" + name + "\"" : name;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fellowship");
        root.then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.string()).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return report(
                            ctx,
                            Fellowships.create(
                                    player, StringArgumentType.getString(ctx, "name"), ctx.getSource().hasPermission(2)));
                })));
        root.then(Commands.literal("invite")
                .then(fellowship().then(playerArg().executes(ctx -> withTarget(ctx, Fellowships::invite)))));
        root.then(Commands.literal("accept")
                .then(Commands.argument("fellowship", StringArgumentType.string())
                        .suggests(MY_INVITES)
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String name = StringArgumentType.getString(ctx, "fellowship");
                            Fellowship fellowship = Fellowships.findInvite(ctx.getSource().getServer(), player.getUUID(), name)
                                    .orElseThrow(() -> NO_INVITE.create(name));
                            return report(ctx, Fellowships.accept(player, fellowship.id()));
                        })));
        root.then(Commands.literal("reject")
                .then(Commands.argument("fellowship", StringArgumentType.string())
                        .suggests(MY_INVITES)
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String name = StringArgumentType.getString(ctx, "fellowship");
                            Fellowship fellowship = Fellowships.findInvite(ctx.getSource().getServer(), player.getUUID(), name)
                                    .orElseThrow(() -> NO_INVITE.create(name));
                            return report(ctx, Fellowships.reject(player, fellowship.id()));
                        })));
        root.then(Commands.literal("leave")
                .then(fellowship().executes(ctx -> report(
                        ctx, Fellowships.leave(ctx.getSource().getPlayerOrException(), mine(ctx).id())))));
        root.then(Commands.literal("remove")
                .then(fellowship().then(playerArg().executes(ctx -> withTarget(ctx, Fellowships::remove)))));
        root.then(Commands.literal("promote")
                .then(fellowship().then(playerArg().executes(ctx -> withTarget(ctx, Fellowships::promote)))));
        root.then(Commands.literal("demote")
                .then(fellowship().then(playerArg().executes(ctx -> withTarget(ctx, Fellowships::demote)))));
        root.then(Commands.literal("transfer")
                .then(fellowship().then(playerArg().executes(ctx -> withTarget(ctx, Fellowships::transfer)))));
        root.then(Commands.literal("rename")
                .then(fellowship()
                        .then(Commands.argument("newName", StringArgumentType.string())
                                .executes(ctx -> report(
                                        ctx,
                                        Fellowships.rename(
                                                ctx.getSource().getPlayerOrException(),
                                                mine(ctx).id(),
                                                StringArgumentType.getString(ctx, "newName")))))));
        root.then(Commands.literal("disband")
                .then(fellowship().executes(ctx -> report(
                        ctx, Fellowships.disband(ctx.getSource().getPlayerOrException(), mine(ctx).id())))));
        root.then(Commands.literal("icon")
                .then(fellowship().executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    return report(
                            ctx,
                            Fellowships.setIcon(
                                    player, mine(ctx).id(), player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)));
                })));
        root.then(Commands.literal("option")
                .then(fellowship()
                        .then(Commands.argument("option", StringArgumentType.word())
                                .suggests(OPTIONS)
                                .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "option");
                                    FellowshipOption option =
                                            FellowshipOption.byId(id).orElseThrow(() -> UNKNOWN_OPTION.create(id));
                                    return report(
                                            ctx,
                                            Fellowships.setOption(
                                                    ctx.getSource().getPlayerOrException(),
                                                    mine(ctx).id(),
                                                    option,
                                                    BoolArgumentType.getBool(ctx, "value")));
                                })))));
        root.then(Commands.literal("chat")
                .then(fellowship()
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(FellowshipCommands::chat))));
        root.then(Commands.literal("list").executes(FellowshipCommands::list));
        root.then(Commands.literal("info").then(fellowship().executes(FellowshipCommands::info)));
        dispatcher.register(root);

        dispatcher.register(Commands.literal("fchat")
                .then(fellowship()
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(FellowshipCommands::chat))));

        registerAdmin(dispatcher);
    }

    private static void registerAdmin(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> admin =
                Commands.literal("lotrfellowship").requires(source -> source.hasPermission(2));
        admin.then(Commands.literal("list").executes(ctx -> {
            FellowshipData data = FellowshipData.get(ctx.getSource().getServer());
            List<Fellowship> all = data.all();
            ctx.getSource().sendSuccess(() -> Component.translatable("fellowship.lotrrevival.admin.list.header", all.size()), false);
            for (Fellowship fellowship : all) {
                ctx.getSource().sendSuccess(
                        () -> Component.translatable(
                                "fellowship.lotrrevival.admin.list.entry",
                                fellowship.name(),
                                data.nameOf(fellowship.owner()),
                                fellowship.size(),
                                fellowship.id().toString()),
                        false);
            }
            return all.size();
        }));
        admin.then(Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.string())
                        .then(Commands.argument("owner", StringArgumentType.word())
                                .suggests(PLAYER_NAMES)
                                .executes(ctx -> {
                                    MinecraftServer server = ctx.getSource().getServer();
                                    Fellowship fellowship = adminFind(ctx);
                                    sendInfo(ctx.getSource(), server, fellowship);
                                    return 1;
                                }))));
        admin.then(Commands.literal("create")
                .then(Commands.argument("owner", StringArgumentType.word())
                        .suggests(PLAYER_NAMES)
                        .then(Commands.argument("name", StringArgumentType.string()).executes(ctx -> {
                            String ownerName = StringArgumentType.getString(ctx, "owner");
                            ServerPlayer owner = ctx.getSource().getServer().getPlayerList().getPlayerByName(ownerName);
                            if (owner == null) {
                                throw UNKNOWN_PLAYER.create(ownerName);
                            }
                            return report(ctx, Fellowships.create(owner, StringArgumentType.getString(ctx, "name"), true));
                        }))));
        admin.then(Commands.literal("disband")
                .then(Commands.argument("id", UuidArgument.uuid()).executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    UUID id = UuidArgument.getUuid(ctx, "id");
                    Fellowship fellowship = FellowshipData.get(server).find(id).orElseThrow(UNKNOWN_FELLOWSHIP::create);
                    String name = fellowship.name();
                    Fellowships.disbandNow(server, fellowship);
                    ctx.getSource().sendSuccess(() -> Component.translatable("fellowship.lotrrevival.disbanded", name), true);
                    return 1;
                })));
        dispatcher.register(admin);
    }

    // ------------------------------------------------------------------ helpers

    private static RequiredArgumentBuilder<CommandSourceStack, String> fellowship() {
        return Commands.argument("fellowship", StringArgumentType.string()).suggests(MY_FELLOWSHIPS);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> playerArg() {
        return Commands.argument("player", StringArgumentType.word()).suggests(PLAYER_NAMES);
    }

    /** The command player's fellowship named by the {@code fellowship} argument. */
    private static Fellowship mine(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "fellowship");
        return Fellowships.findOf(ctx.getSource().getServer(), player.getUUID(), name)
                .orElseThrow(() -> NOT_IN.create(name));
    }

    private static Fellowship adminFind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        MinecraftServer server = ctx.getSource().getServer();
        String ownerName = StringArgumentType.getString(ctx, "owner");
        UUID owner = Fellowships.resolvePlayer(server, ownerName).orElseThrow(() -> UNKNOWN_PLAYER.create(ownerName));
        return Fellowships.findOf(server, owner, StringArgumentType.getString(ctx, "name"))
                .orElseThrow(UNKNOWN_FELLOWSHIP::create);
    }

    private static int withTarget(
            CommandContext<CommandSourceStack> ctx, TargetAction action) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String targetName = StringArgumentType.getString(ctx, "player");
        UUID target = Fellowships.resolvePlayer(ctx.getSource().getServer(), targetName)
                .orElseThrow(() -> UNKNOWN_PLAYER.create(targetName));
        return report(ctx, action.apply(player, mine(ctx).id(), target));
    }

    @FunctionalInterface
    private interface TargetAction {
        FellowshipResult apply(ServerPlayer actor, UUID fellowship, UUID target);
    }

    private static int report(CommandContext<CommandSourceStack> ctx, FellowshipResult result) {
        if (result.ok()) {
            ctx.getSource().sendSuccess(result::message, false);
            return 1;
        }
        ctx.getSource().sendFailure(result.message());
        return 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        FellowshipData data = FellowshipData.get(ctx.getSource().getServer());
        List<Fellowship> mine = data.of(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.translatable("fellowship.lotrrevival.list.header", mine.size()), false);
        for (Fellowship fellowship : mine) {
            FellowshipRole role = fellowship.role(player.getUUID()).orElse(FellowshipRole.MEMBER);
            ctx.getSource().sendSuccess(
                    () -> Component.translatable(
                            "fellowship.lotrrevival.list.entry",
                            fellowship.name(),
                            Component.translatable("fellowship.lotrrevival.role." + role.name().toLowerCase(java.util.Locale.ROOT)),
                            fellowship.size()),
                    false);
        }
        for (Fellowship fellowship : data.invitesFor(player.getUUID())) {
            ctx.getSource().sendSuccess(
                    () -> Component.translatable(
                            "fellowship.lotrrevival.list.invite",
                            fellowship.name(),
                            data.nameOf(fellowship.invites().get(player.getUUID()))),
                    false);
        }
        return mine.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        sendInfo(ctx.getSource(), ctx.getSource().getServer(), mine(ctx));
        return 1;
    }

    private static void sendInfo(CommandSourceStack source, MinecraftServer server, Fellowship fellowship) {
        FellowshipData data = FellowshipData.get(server);
        source.sendSuccess(() -> Component.translatable("fellowship.lotrrevival.info.header", fellowship.name(), fellowship.size()), false);
        for (UUID id : fellowship.allMembers()) {
            FellowshipRole role = fellowship.role(id).orElse(FellowshipRole.MEMBER);
            boolean online = server.getPlayerList().getPlayer(id) != null;
            source.sendSuccess(
                    () -> Component.translatable(
                            "fellowship.lotrrevival.info.member",
                            data.nameOf(id),
                            Component.translatable("fellowship.lotrrevival.role." + role.name().toLowerCase(java.util.Locale.ROOT)),
                            Component.translatable(online ? "fellowship.lotrrevival.online" : "fellowship.lotrrevival.offline")),
                    false);
        }
        for (FellowshipOption option : FellowshipOption.values()) {
            source.sendSuccess(
                    () -> Component.translatable(
                            "fellowship.lotrrevival.info.option",
                            Component.translatable("fellowship.lotrrevival.option." + option.id()),
                            Component.translatable(fellowship.option(option) ? "fellowship.lotrrevival.on" : "fellowship.lotrrevival.off")),
                    false);
        }
    }

    private static int chat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Fellowship fellowship = mine(ctx);
        Component line = chatLine(player, fellowship, StringArgumentType.getString(ctx, "message"));
        Fellowships.chat(player, fellowship, line);
        return 1;
    }

    /** {@code [Name] [Title] <Sender> message}, all yellow, the bracketed name clickable to pre-fill a reply. */
    public static Component chatLine(ServerPlayer sender, Fellowship fellowship, String message) {
        MutableComponent prefix = Component.literal("[" + fellowship.name() + "]")
                .withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(
                                ClickEvent.Action.SUGGEST_COMMAND, "/fchat " + quote(fellowship.name()) + " ")));
        MutableComponent line = Component.empty().append(prefix).append(" ");
        TitleService.chatPrefix(sender).ifPresent(title -> line.append(title).append(" "));
        line.append(Component.literal("<").append(sender.getDisplayName()).append("> ").withStyle(ChatFormatting.YELLOW));
        line.append(Component.literal(message).withStyle(ChatFormatting.YELLOW));
        return line;
    }
}
