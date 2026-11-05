package io.github.sethwillemsvg.lotrrevival.nemesis;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.sevenstars.middleearth.resources.datas.common.NpcRank;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** {@code /lotr_revival nemesis ...} (op level 2): list, info, spawn, renown, kill, grudge, ambush. */
public final class NemesisCommands {
    private NemesisCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lotr_revival").requires(s -> s.hasPermission(2)).then(Commands.literal("nemesis")
                .then(Commands.literal("list").executes(c -> list(c, null))
                        .then(Commands.argument("faction", ResourceLocationArgument.id()).executes(c -> list(c, ResourceLocationArgument.getId(c, "faction")))))
                .then(Commands.literal("info").then(Commands.argument("id", StringArgumentType.word()).executes(NemesisCommands::info)))
                .then(Commands.literal("spawn").then(Commands.argument("faction", ResourceLocationArgument.id()).executes(c -> spawn(c, Rank.MARKED))
                        .then(Commands.argument("rank", StringArgumentType.word()).executes(c -> spawn(c, Rank.byName(StringArgumentType.getString(c, "rank")))))))
                .then(Commands.literal("renown").then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(NemesisCommands::renown))))
                .then(Commands.literal("kill").then(Commands.argument("id", StringArgumentType.word()).executes(NemesisCommands::kill)))
                .then(Commands.literal("grudge").then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("amount", IntegerArgumentType.integer())
                                .executes(NemesisCommands::grudge)))))
                .then(Commands.literal("ambush").then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("player", EntityArgument.player()).executes(NemesisCommands::ambush))))
                .then(Commands.literal("politics").executes(c -> {
                    Politics.round(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("politics round run"), true);
                    return 1;
                }))));
    }

    /** Finds a record by full UUID or by the short id (prefix). */
    public static @Nullable NemesisRecord byId(CommandSourceStack src, String id) {
        NemesisSavedData data = NemesisSavedData.get(src.getServer());
        for (NemesisRecord r : data.records.values()) if (r.id.toString().startsWith(id.toLowerCase())) return r;
        return null;
    }

    private static int list(CommandContext<CommandSourceStack> c, @Nullable ResourceLocation faction) {
        NemesisSavedData data = NemesisSavedData.get(c.getSource().getServer());
        int n = 0;
        for (NemesisRecord r : data.records.values()) {
            if (faction != null && !r.faction.equals(faction)) continue;
            n++;
            String line = r.shortId() + " " + r.displayName() + " [" + r.faction + "] " + r.rank + (r.challenger ? "*" : "") + " renown=" + r.renown
                    + " " + r.status;
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        int total = n;
        c.getSource().sendSuccess(() -> Component.literal(total + " record(s)"), false);
        return n;
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        NemesisRecord r = byId(c.getSource(), StringArgumentType.getString(c, "id"));
        if (r == null) return notFound(c);
        String text = r.id + " \"" + r.displayName() + "\" faction=" + r.faction + " type=" + r.npcType + " race=" + r.race + " rank=" + r.rank
                + " challenger=" + r.challenger + " renown=" + r.renown + " status=" + r.status + " strengths=" + r.strengths + " weakness=" + r.weakness
                + " scars=" + r.scars + " cheat_deaths=" + r.cheatDeaths + " manifestation=" + r.manifestation + " grudges=" + r.grudges.size()
                + " last=" + r.lastPos.toShortString() + " in " + r.lastDimension;
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, Rank rank) {
        ResourceLocation faction = ResourceLocationArgument.getId(c, "faction");
        var level = c.getSource().getLevel();
        var f = Sides.faction(level, faction);
        if (!MeRanks.nemesisFaction(f)) {
            c.getSource().sendFailure(Component.literal("not a nemesis faction: " + faction));
            return 0;
        }
        List<ResourceLocation> pool = MeRanks.pool(f, rank.ordinal() >= Rank.COMMANDER.ordinal() ? NpcRank.LEADER : NpcRank.SOLDIER);
        if (pool.isEmpty()) pool = MeRanks.pool(f, NpcRank.LEADER);
        ResourceLocation type = NemesisRandom.pick(pool);
        NemesisRecord r = NemesisFactory.create(level, faction, type, NemesisFactory.raceOfType(level, type));
        r.renown = rank.threshold();
        Renown.reseat(c.getSource().getServer(), faction, "command spawn");
        var body = Manifestation.spawn(level, r, BlockPos.containing(c.getSource().getPosition()), "command");
        LotrLog.dbg("nemesis", "command spawn: id={} faction={} rank={} body={}", r.shortId(), faction, r.rank, body == null ? "-" : LotrLog.id(body.getUUID()));
        c.getSource().sendSuccess(() -> Component.literal("spawned " + r.shortId() + " " + r.displayName() + " (" + r.rank + ")"), true);
        return 1;
    }

    private static int renown(CommandContext<CommandSourceStack> c) {
        NemesisRecord r = byId(c.getSource(), StringArgumentType.getString(c, "id"));
        if (r == null) return notFound(c);
        Renown.add(c.getSource().getServer(), r, IntegerArgumentType.getInteger(c, "amount"), "command");
        c.getSource().sendSuccess(() -> Component.literal(r.shortId() + " renown=" + r.renown + " rank=" + r.rank), true);
        return 1;
    }

    private static int kill(CommandContext<CommandSourceStack> c) {
        NemesisRecord r = byId(c.getSource(), StringArgumentType.getString(c, "id"));
        if (r == null) return notFound(c);
        DeathRules.die(c.getSource().getServer(), r, 5, "operator command", c.getSource().getEntity());
        c.getSource().sendSuccess(() -> Component.literal(r.shortId() + " is dead"), true);
        return 1;
    }

    private static int grudge(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        NemesisRecord r = byId(c.getSource(), StringArgumentType.getString(c, "id"));
        if (r == null) return notFound(c);
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        int now = r.addGrudge(p.getUUID(), IntegerArgumentType.getInteger(c, "amount"), p.level().getGameTime());
        NemesisSavedData.get(c.getSource().getServer()).setDirty();
        c.getSource().sendSuccess(() -> Component.literal(r.shortId() + " grudge on " + p.getScoreboardName() + " = " + now), true);
        return 1;
    }

    private static int ambush(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        NemesisRecord r = byId(c.getSource(), StringArgumentType.getString(c, "id"));
        if (r == null) return notFound(c);
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        var body = Ambush.start(p, r);
        c.getSource().sendSuccess(() -> Component.literal(body == null ? "no ambush (see log)" : "ambush started"), true);
        return body == null ? 0 : 1;
    }

    private static int notFound(CommandContext<CommandSourceStack> c) {
        c.getSource().sendFailure(Component.literal("no nemesis with that id"));
        return 0;
    }
}
