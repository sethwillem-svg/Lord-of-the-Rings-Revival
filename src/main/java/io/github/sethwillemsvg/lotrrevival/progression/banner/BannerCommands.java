package io.github.sethwillemsvg.lotrrevival.progression.banner;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * {@code /lotrbanner} (permission level 2): {@code range <pos> <1..64|reset>}, {@code whitelistsize <pos> <1..4000>},
 * {@code info <pos>}.
 */
public final class BannerCommands {
    private static final SimpleCommandExceptionType NO_BANNER =
            new SimpleCommandExceptionType(Component.translatable("commands.lotrrevival.banner.none"));

    private BannerCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal("lotrbanner").requires(source -> source.hasPermission(2));
        root.then(Commands.literal("range")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.literal("reset").executes(ctx -> setRange(ctx, 0)))
                        .then(Commands.argument("range", IntegerArgumentType.integer(1, BannerBases.MAX_RANGE))
                                .executes(ctx -> setRange(ctx, IntegerArgumentType.getInteger(ctx, "range"))))));
        root.then(Commands.literal("whitelistsize")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument(
                                        "size", IntegerArgumentType.integer(1, BannerSettings.MAX_WHITELIST_SIZE))
                                .executes(BannerCommands::setWhitelistSize))));
        root.then(Commands.literal("info")
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(BannerCommands::info)));
        dispatcher.register(root);
    }

    private static FactionBannerBlockEntity bannerAt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        if (ctx.getSource().getLevel().getBlockEntity(pos) instanceof FactionBannerBlockEntity banner) {
            return banner;
        }
        throw NO_BANNER.create();
    }

    private static int setRange(CommandContext<CommandSourceStack> ctx, int range) throws CommandSyntaxException {
        FactionBannerBlockEntity banner = bannerAt(ctx);
        banner.setSettings(banner.settings().withCustomRange(range));
        if (range == 0) {
            ctx.getSource().sendSuccess(
                    () -> Component.translatable("commands.lotrrevival.banner.range.reset", banner.effectiveRange()),
                    true);
        } else {
            ctx.getSource().sendSuccess(
                    () -> Component.translatable("commands.lotrrevival.banner.range.set", range), true);
        }
        return banner.effectiveRange();
    }

    private static int setWhitelistSize(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        FactionBannerBlockEntity banner = bannerAt(ctx);
        int size = IntegerArgumentType.getInteger(ctx, "size");
        banner.setSettings(banner.settings().withWhitelistSize(size));
        ctx.getSource().sendSuccess(
                () -> Component.translatable("commands.lotrrevival.banner.whitelistsize", banner.settings().whitelistSize()),
                true);
        return banner.settings().whitelistSize();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        FactionBannerBlockEntity banner = bannerAt(ctx);
        BannerSettings settings = banner.settings();
        int range = banner.effectiveRange();
        ctx.getSource().sendSuccess(
                () -> Component.translatable(
                        "commands.lotrrevival.banner.info",
                        banner.design().toString(),
                        range,
                        settings.customRange(),
                        settings.owner().isPresent() ? settings.ownerName() : "-",
                        settings.mode().getSerializedName(),
                        settings.threshold(),
                        settings.entries().size() + 1,
                        settings.whitelistSize(),
                        settings.selfProtection(),
                        settings.structure()),
                false);
        return range;
    }
}
