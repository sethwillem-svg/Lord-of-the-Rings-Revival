package io.github.sethwillemsvg.lotrrevival.core.alignment;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionKillEvent;
import io.github.sethwillemsvg.lotrrevival.core.faction.AlignmentCreditor;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionMember;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.core.network.PledgeSyncPayload;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Game-bus listeners of the alignment system: kills, online timers and client syncing. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class AlignmentEvents {
    /** Hired-unit kills are reported to the owner only when the owner is this close (squared: 64 blocks). */
    private static final double HIRE_CREDIT_RANGE_SQR = 64.0 * 64.0;

    private AlignmentEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim instanceof Player) {
            return; // PvP never changes alignment
        }
        Optional<ResourceKey<Faction>> victimFaction = FactionRelations.factionOf(victim);
        if (victimFaction.isEmpty()) {
            return;
        }
        final float base = victim instanceof FactionMember member ? member.alignmentKillValue() : 1.0f;
        final boolean civilian = victim instanceof FactionMember member && member.isCivilian();
        Entity killer = event.getSource().getEntity();
        if (killer instanceof ServerPlayer player) {
            if (victim instanceof FactionMember member && !member.grantsAlignmentOnKill(player)) {
                return;
            }
            FactionKillEvent kill = new FactionKillEvent(player, victim, victimFaction.get(), base, civilian, false);
            if (NeoForge.EVENT_BUS.post(kill).isCanceled()) {
                return;
            }
            Alignment.applyKill(
                    player,
                    victimFaction.get(),
                    kill.getBaseValue(),
                    civilian,
                    AlignmentSource.kill(EntityType.getKey(victim.getType()), victim.position()));
            Alignment.pledgeKillCheck(player, victimFaction.get(), victim);
        } else if (killer instanceof AlignmentCreditor creditor) {
            creditor.creditedPlayer()
                    .map(victim.level()::getPlayerByUUID)
                    .filter(owner -> owner instanceof ServerPlayer)
                    .map(owner -> (ServerPlayer) owner)
                    .filter(owner -> owner.distanceToSqr(victim) <= HIRE_CREDIT_RANGE_SQR)
                    .ifPresent(owner -> NeoForge.EVENT_BUS.post(
                            new FactionKillEvent(owner, victim, victimFaction.get(), base, civilian, true)));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Alignment.tickTimers(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resync(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            resync(player);
        }
    }

    /** A player starts tracking another player: tell them the other's pledge. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer tracker
                && event.getTarget() instanceof ServerPlayer target
                && tracker.connection != null
                && tracker.connection.hasChannel(PledgeSyncPayload.TYPE)) {
            Optional<ResourceKey<Faction>> pledge = Alignment.pledge(target);
            if (pledge.isPresent()) {
                PacketDistributor.sendToPlayer(
                        tracker, new PledgeSyncPayload(target.getUUID(), pledge.map(ResourceKey::location)));
            }
        }
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        FactionRelations.clearCaches();
    }

    private static void resync(ServerPlayer player) {
        Alignment.syncAll(player);
        Alignment.syncPledge(player);
    }
}
