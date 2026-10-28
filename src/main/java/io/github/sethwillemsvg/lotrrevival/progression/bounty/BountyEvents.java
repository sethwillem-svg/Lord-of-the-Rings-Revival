package io.github.sethwillemsvg.lotrrevival.progression.bounty;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionKillEvent;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import io.github.sethwillemsvg.lotrrevival.progression.quest.Quests;
import io.github.sethwillemsvg.lotrrevival.progression.quest.event.QuestCompletedEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Game-bus listeners of the bounty system. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class BountyEvents {
    /** The last known biome is refreshed this often while the player is online. */
    public static final int BIOME_INTERVAL = 1_200;

    private BountyEvents() {}

    /**
     * Kill records: direct kills by a survival or adventure player only. Hired-unit kills do not count, and the
     * kill must be inside the faction's area of influence (always true until Core exposes the influence reader).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onFactionKill(FactionKillEvent event) {
        if (!ProgressionConfig.get(ProgressionConfig.BOUNTIES_ENABLED) || event.isCreditedViaHire()) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.getServer();
        if (server == null || player.isCreative() || player.isSpectator()) {
            return;
        }
        // TODO(core): skip when Alignment.influence(player, faction) is 0 once Core ships the accessor.
        Bounties.recordKill(server, player.getUUID(), event.getVictimFaction());
    }

    /** A player killed by another player: bounty resolution in both directions. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!ProgressionConfig.get(ProgressionConfig.BOUNTIES_ENABLED)) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer victim
                && event.getSource().getEntity() instanceof ServerPlayer killer) {
            BountyResolution.onPlayerKill(killer, victim);
        }
    }

    /** Completed bounties: the tally, the achievements and a trophy on every fifth. */
    @SubscribeEvent
    public static void onQuestCompleted(QuestCompletedEvent event) {
        if (event.getQuest().objectiveData().bountyTarget().isEmpty()) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        int completed = Quests.incrementBountiesCompleted(player);
        if (completed == 1) {
            BountySystem.award(player, BountySystem.FIRST_BOUNTY);
        }
        if (completed % 5 == 0) {
            event.getRewards().addItem(new ItemStack(BountyItems.BOUNTY_TROPHY.get()));
        }
        if (completed == 5) {
            BountySystem.award(player, BountySystem.BOUNTY_HUNTER);
        }
    }

    /** Runs before the full sync (LOW), like the other login checks. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BountyResolution.failClaimed(player);
            Bounties.deliverNotices(player);
            rememberBiome(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            rememberBiome(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Bounties.deliverNotices(player);
            if (player.tickCount % BIOME_INTERVAL == 0) {
                rememberBiome(player);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BountyCommands.register(event.getDispatcher());
    }

    /** Stores the player's current biome as their last known one. */
    public static void rememberBiome(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        player.level()
                .getBiome(player.blockPosition())
                .unwrapKey()
                .map(ResourceKey::location)
                .ifPresent(biome -> BountyData.get(server).setLastBiome(player.getUUID(), biome));
    }
}
