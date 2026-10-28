package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code /lotr waypoint custom create|rename|delete}, permission 0, the same rules as the map page. Attached to the
 * {@code waypoint} node by {@link WaypointCommands}.
 */
public final class CustomWaypointCommands {
    private CustomWaypointCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("custom")
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(CustomWaypointCommands::create)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(CustomWaypointCommands::rename))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .executes(CustomWaypointCommands::delete)));
    }

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return report(
                ctx.getSource(),
                CustomWaypoints.create(
                        ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name")));
    }

    private static int rename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return report(
                ctx.getSource(),
                CustomWaypoints.rename(
                        ctx.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(ctx, "id"),
                        StringArgumentType.getString(ctx, "name")));
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return report(
                ctx.getSource(),
                CustomWaypoints.delete(
                        ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "id")));
    }

    /** Shows the result; returns the waypoint id on success (what the command result carries) and 0 on a refusal. */
    private static int report(CommandSourceStack source, CustomWaypoints.Result result) {
        if (result.ok()) {
            source.sendSuccess(result::message, false);
            return result.id().orElse(0);
        }
        source.sendFailure(result.message());
        return 0;
    }

    /** English strings for custom waypoints; called from {@code WaypointSystem.contributeLang}. */
    public static void contributeLang() {
        ModLangProvider.contribute(lang -> {
            lang.add("message.lotrrevival.custom.created", "Marked \"%s\" on your map.");
            lang.add("message.lotrrevival.custom.renamed", "Renamed \"%1$s\" to \"%2$s\".");
            lang.add("message.lotrrevival.custom.deleted", "Removed \"%s\" from your map.");
            lang.add("message.lotrrevival.custom.wrong_dimension", "You can only mark waypoints in Middle-earth.");
            lang.add("message.lotrrevival.custom.bad_name", "A waypoint name must be 1 to %s characters.");
            lang.add("message.lotrrevival.custom.too_low", "You cannot mark a waypoint below y=%s.");
            lang.add(
                    "message.lotrrevival.custom.limit",
                    "You can only hold %s custom waypoints. Earn more achievements to hold more.");
            lang.add("message.lotrrevival.custom.unknown", "You have no custom waypoint number %s.");
        });
    }
}
