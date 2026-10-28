package io.github.sethwillemsvg.lotrrevival.progression.bounty;

import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Bounty API: who is a valid target, what a bounty is worth and target notices. Kill records live in
 * {@link BountyData}; the bounty offers that use this API are a later ticket.
 */
public final class Bounties {
    /** Live records with one faction that make a player a valid target. */
    public static final int TARGET_THRESHOLD = 25;

    private Bounties() {}

    /** Overworld game time, the clock of every bounty timer. */
    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Adds one kill record for the player with the faction, at the current time. */
    public static void recordKill(MinecraftServer server, UUID player, ResourceKey<Faction> faction) {
        BountyData.get(server).addKills(faction.location(), player, now(server), 1);
    }

    /** Kill records with the faction that are not older than 48 hours. */
    public static int liveRecords(MinecraftServer server, UUID player, ResourceKey<Faction> faction) {
        return BountyData.get(server).liveKills(faction.location(), player, now(server));
    }

    /**
     * 25 or more live records with the faction, and the last claim for the faction at least 12 hours ago. Works for
     * online and offline players. False when bounties are disabled.
     */
    public static boolean isValidTarget(MinecraftServer server, UUID player, ResourceKey<Faction> faction) {
        if (!ProgressionConfig.get(ProgressionConfig.BOUNTIES_ENABLED)) {
            return false;
        }
        BountyData data = BountyData.get(server);
        long now = now(server);
        if (data.liveKills(faction.location(), player, now) < TARGET_THRESHOLD) {
            return false;
        }
        return data.lastClaimed(faction.location(), player)
                .map(claimed -> now - claimed >= BountyData.IMMUNITY)
                .orElse(true);
    }

    /** Every valid target for the faction, online or offline. */
    public static List<UUID> validTargets(MinecraftServer server, ResourceKey<Faction> faction) {
        List<UUID> out = new ArrayList<>();
        for (UUID player : BountyData.get(server).recordedPlayers(faction.location())) {
            if (isValidTarget(server, player, faction)) {
                out.add(player);
            }
        }
        return out;
    }

    /** Bounty value v, the alignment base: clamp(live records, 1, 50). */
    public static int bountyValue(int liveRecords) {
        return Mth.clamp(liveRecords, 1, 50);
    }

    /** Bounty coins: clamp(round(records x 10 x uniform(0.75, 1.25)), 1, 1000). */
    public static int rollCoins(int liveRecords, RandomSource random) {
        float coins = liveRecords * 10.0f * Mth.randomBetween(random, 0.75f, 1.25f);
        return Mth.clamp(Math.round(coins), 1, 1000);
    }

    /**
     * Tells the target a faction has put a price on their head: on their next tick if online, otherwise on their
     * next login. The hunter is not named.
     */
    public static void queueNotice(MinecraftServer server, UUID target, ResourceKey<Faction> faction) {
        BountyData.get(server).addNotice(target, faction.location());
    }

    /** Delivers and clears the queued notices of an online target (yellow chat line each). */
    static void deliverNotices(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        BountyData data = BountyData.get(server);
        if (!data.hasNotices(player.getUUID())) {
            return;
        }
        for (var faction : data.takeNotices(player.getUUID())) {
            player.sendSystemMessage(Component.translatable(
                            "bounty.lotrrevival.notice",
                            Factions.displayName(ResourceKey.create(
                                    ModDataRegistries.FACTION, faction)))
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
