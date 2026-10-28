package io.github.sethwillemsvg.lotrrevival.progression.bounty;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.progression.quest.FailReason;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestInstance;
import io.github.sethwillemsvg.lotrrevival.progression.quest.QuestStatus;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What happens when a player kills a player who is a bounty target or a bounty hunter. Only the two direct cases
 * count: a hunter kills the target they hold a bounty on, or a target kills a hunter hunting them.
 */
public final class BountyResolution {
    /** Alignment value that bounty losses never push a player below. */
    public static final float LOSS_FLOOR = 100.0f;

    /** Alignment cause of every change made here. */
    public static final AlignmentSource SOURCE = AlignmentSource.of(LOTRRevival.id("bounty"));

    private BountyResolution() {}

    /** The holder's bounty quests on {@code target} that are still being hunted (not done, not failed). */
    static List<QuestInstance> openBounties(ServerPlayer holder, UUID target) {
        List<QuestInstance> out = new ArrayList<>();
        for (QuestInstance quest : Quests.get(holder).active()) {
            if (quest.state() == QuestStatus.ACTIVE && quest.objectiveData().bountyTarget().filter(target::equals).isPresent()) {
                out.add(quest);
            }
        }
        return out;
    }

    /** Resolves a player kill in both directions. */
    public static void onPlayerKill(ServerPlayer killer, ServerPlayer victim) {
        if (killer == victim) {
            return;
        }
        MinecraftServer server = killer.getServer();
        if (server == null) {
            return;
        }
        hunterKillsTarget(server, killer, victim);
        targetKillsHunter(server, killer, victim);
    }

    // ------------------------------------------------------------------ hunter kills target

    private static void hunterKillsTarget(MinecraftServer server, ServerPlayer hunter, ServerPlayer target) {
        List<QuestInstance> mine = openBounties(hunter, target.getUUID());
        if (mine.isEmpty()) {
            return;
        }
        BountyData data = BountyData.get(server);
        long now = Bounties.now(server);
        Set<ResourceLocation> factions = new LinkedHashSet<>();
        int value = 0;
        for (QuestInstance quest : mine) {
            Quests.markObjectiveDone(hunter, quest.uuid());
            factions.add(quest.giverFaction());
            value = Math.max(value, Math.round(quest.alignmentBase()));
        }
        for (ResourceLocation faction : factions) {
            data.setClaimed(faction, target.getUUID(), now);
            data.addClaim(target.getUUID(), faction, now);
        }
        // Other hunters of the same target for the same faction lose their bounty.
        for (ServerPlayer other : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (other == hunter || other == target) {
                continue;
            }
            for (QuestInstance quest : openBounties(other, target.getUUID())) {
                if (factions.contains(quest.giverFaction())) {
                    Quests.fail(other, quest.uuid(), FailReason.CLAIMED);
                }
            }
        }

        loseAlignment(target, value);
        ResourceKey<Faction> shown = factionKey(factions.iterator().next());
        target.sendSystemMessage(Component.translatable("bounty.lotrrevival.slain", hunter.getDisplayName())
                .withStyle(ChatFormatting.RED));
        target.sendSystemMessage(Component.translatable("bounty.lotrrevival.immune", Factions.displayName(shown))
                .withStyle(ChatFormatting.GRAY));
        announce(
                server,
                Component.translatable(
                                "bounty.lotrrevival.announce.claimed",
                                hunter.getDisplayName(),
                                target.getDisplayName(),
                                Factions.displayName(shown))
                        .withStyle(ChatFormatting.GOLD),
                target);
    }

    /** The target's loss: 2v from their pledged faction, else their highest faction above +100; never below +100. */
    private static void loseAlignment(ServerPlayer target, int value) {
        Optional<ResourceKey<Faction>> faction = Alignment.pledge(target);
        if (faction.isEmpty()) {
            faction = highestFaction(target, LOSS_FLOOR);
        }
        faction.ifPresent(key -> lowerToFloor(target, key, 2.0f * value));
    }

    /** Lowers the alignment by {@code amount}, but never below +100 and not at all when it is at or below it. */
    private static void lowerToFloor(ServerPlayer player, ResourceKey<Faction> faction, float amount) {
        float current = Alignment.get(player, faction);
        if (current <= LOSS_FLOOR) {
            return;
        }
        float target = Math.max(LOSS_FLOOR, current - amount);
        Alignment.add(player, faction, target - current, SOURCE);
    }

    /** The non-fixed faction with the highest alignment strictly above {@code above}. */
    private static Optional<ResourceKey<Faction>> highestFaction(ServerPlayer player, float above) {
        Registry<Faction> registry = player.level().registryAccess().registryOrThrow(ModDataRegistries.FACTION);
        ResourceKey<Faction> best = null;
        float bestValue = above;
        List<ResourceKey<Faction>> keys = new ArrayList<>(registry.registryKeySet());
        keys.sort((a, b) -> a.location().compareTo(b.location()));
        for (ResourceKey<Faction> key : keys) {
            Faction faction = registry.get(key);
            if (faction == null || faction.fixedAlignment().isPresent()) {
                continue;
            }
            float value = Alignment.get(player, key);
            if (value > bestValue) {
                best = key;
                bestValue = value;
            }
        }
        return Optional.ofNullable(best);
    }

    // ------------------------------------------------------------------ target kills hunter

    private static void targetKillsHunter(MinecraftServer server, ServerPlayer target, ServerPlayer hunter) {
        List<QuestInstance> hunting = openBounties(hunter, target.getUUID());
        if (hunting.isEmpty()) {
            return;
        }
        for (QuestInstance quest : hunting) {
            int value = Math.round(quest.alignmentBase());
            ResourceKey<Faction> giver = factionKey(quest.giverFaction());
            Quests.fail(hunter, quest.uuid(), FailReason.KILLED_BY_TARGET);
            lowerToFloor(hunter, giver, 2.0f * value);

            Optional<ResourceKey<Faction>> gain = Alignment.pledge(target);
            if (gain.isEmpty()) {
                gain = highestFaction(target, 0.0f);
            }
            gain.ifPresent(key -> Alignment.add(target, key, value, SOURCE));

            announce(
                    server,
                    Component.translatable(
                                    "bounty.lotrrevival.announce.turned",
                                    target.getDisplayName(),
                                    hunter.getDisplayName())
                            .withStyle(ChatFormatting.GOLD),
                    null);
        }
        BountySystem.award(target, BountySystem.TURNED_THE_HUNT);
    }

    // ------------------------------------------------------------------ login

    /** Fails the still-open bounties of a player whose target was claimed for the faction while they were away. */
    public static int failClaimed(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return 0;
        }
        BountyData data = BountyData.get(server);
        data.prune(Bounties.now(server));
        int failed = 0;
        for (BountyData.Claim claim : data.claims()) {
            for (QuestInstance quest : openBounties(player, claim.target())) {
                if (quest.giverFaction().equals(claim.faction())
                        && quest.givenAt() <= claim.at()
                        && Quests.fail(player, quest.uuid(), FailReason.CLAIMED)) {
                    failed++;
                }
            }
        }
        return failed;
    }

    // ------------------------------------------------------------------ helpers

    private static ResourceKey<Faction> factionKey(ResourceLocation id) {
        return ResourceKey.create(ModDataRegistries.FACTION, id);
    }

    /** Server-wide announcement to everyone but {@code except} (the target hears it in their own lines). */
    private static void announce(MinecraftServer server, Component line, @Nullable ServerPlayer except) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player != except) {
                player.sendSystemMessage(line);
            }
        }
        server.sendSystemMessage(line);
    }
}
