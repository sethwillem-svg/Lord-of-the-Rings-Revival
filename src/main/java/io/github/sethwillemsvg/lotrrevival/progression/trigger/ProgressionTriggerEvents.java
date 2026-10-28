package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.event.AlignmentEvent;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionTableCraftEvent;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionKillEvent;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressCounters;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Game-bus listeners that fire alignment, kill and armour triggers. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class ProgressionTriggerEvents {
    private ProgressionTriggerEvents() {}

    /** Core fires this when a player takes a result from a faction crafting table (#109). */
    @SubscribeEvent
    public static void onFactionTableCraft(FactionTableCraftEvent event) {
        ProgressionTriggers.CRAFT_AT_TABLE
                .get()
                .trigger(event.getPlayer(), event.getTable(), event.getFaction(), event.getResult());
    }

    @SubscribeEvent
    public static void onRankChanged(AlignmentEvent.RankChanged event) {
        ProgressionTriggers.ALIGNMENT_RANK.get().trigger(event.getPlayer(), event.getFaction());
    }

    /** Grants ranks reached before the advancement existed. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.level()
                    .registryAccess()
                    .registryOrThrow(ModDataRegistries.FACTION)
                    .registryKeySet()
                    .forEach(faction -> ProgressionTriggers.ALIGNMENT_RANK.get().trigger(player, faction));
        }
    }

    /** Lowest priority and receiving cancelled events: the kill happened even if the alignment was cancelled. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onFactionKill(FactionKillEvent event) {
        ServerPlayer player = event.getPlayer();
        ProgressCounters counters = player.getData(ProgressionAttachments.PROGRESS_COUNTERS);
        player.setData(
                ProgressionAttachments.PROGRESS_COUNTERS,
                counters.withKill(event.getVictimFaction().location()));
        ProgressionTriggers.KILL_FACTION_NPC
                .get()
                .trigger(player, event.getVictim(), event.getVictimFaction(), event.isCreditedViaHire());
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            ProgressionTriggers.WEAR_ARMOR_SET.get().trigger(player);
        }
    }
}
