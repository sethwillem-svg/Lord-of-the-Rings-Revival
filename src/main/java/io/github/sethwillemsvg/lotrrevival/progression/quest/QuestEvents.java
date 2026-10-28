package io.github.sethwillemsvg.lotrrevival.progression.quest;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionKillEvent;
import io.github.sethwillemsvg.lotrrevival.core.faction.AlignmentCreditor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Game-bus listeners of the quest system: kill progress, login checks, datapack changes and commands. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class QuestEvents {
    /** Hired-unit kills count for the owner only within this distance (squared: 64 blocks), as in Core. */
    private static final double CREDIT_RANGE_SQR = 64.0 * 64.0;

    private QuestEvents() {}

    /**
     * Faction kills by the player or a unit credited to them (the event covers both). Lowest priority and
     * receiving cancelled events: the kill happened even if a listener cancelled the alignment.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onFactionKill(FactionKillEvent event) {
        Quests.onFactionKill(event.getPlayer(), event.getVictimFaction());
    }

    /** Entity kills by the player or by a unit credited to them. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim instanceof Player) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (killer instanceof ServerPlayer player) {
            Quests.onEntityKill(player, victim.getType());
        } else if (killer instanceof AlignmentCreditor creditor) {
            creditor.creditedPlayer()
                    .map(victim.level()::getPlayerByUUID)
                    .filter(owner -> owner instanceof ServerPlayer)
                    .map(owner -> (ServerPlayer) owner)
                    .filter(owner -> owner.distanceToSqr(victim) <= CREDIT_RANGE_SQR)
                    .ifPresent(owner -> Quests.onEntityKill(owner, victim.getType()));
        }
    }

    /** Runs before the full sync (which listens at LOW) so the client gets the corrected state. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Quests.checkDeadGivers(player);
            Quests.validate(player);
        }
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(Quests::validate);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        QuestCommands.register(event.getDispatcher());
    }
}
