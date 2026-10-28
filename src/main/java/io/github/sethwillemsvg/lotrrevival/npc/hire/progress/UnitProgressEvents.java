package io.github.sethwillemsvg.lotrrevival.npc.hire.progress;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.interact.Hireable;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeech;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Game-bus listeners for unit kills, levels, death handling and idle speech. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class UnitProgressEvents {
    /** Idle speech chance per tick while following: 1 in this many. */
    public static final int IDLE_SPEECH_ODDS = 4000;

    private UnitProgressEvents() {}

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getSource().getEntity() instanceof NpcEntity unit
                && unit != event.getEntity()
                && unit.isHired()) {
            UnitLevels.onKill(unit, event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onUnitDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof NpcEntity unit && !unit.level().isClientSide && unit.isHired()) {
            UnitDeaths.onDeath(unit, event.getSource());
        }
    }

    /** Hired units drop only gear (the player's, spawned in {@link UnitDeaths}, and originals at their chance). */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof NpcEntity unit && !unit.level().isClientSide && unit.isHired()) {
            HiredUnit state = HiredUnits.get(unit);
            event.getDrops().removeIf(drop -> state.originalGear().values().stream()
                    .noneMatch(original -> ItemStack.isSameItemSameComponents(original, drop.getItem())));
        }
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof NpcEntity unit && unit.isHired()) {
            event.setCanceled(true);
        }
    }

    /** Level-up fireworks look good but must not hurt anyone. */
    @SubscribeEvent
    public static void onFireworkDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof FireworkRocketEntity rocket
                && rocket.getTags().contains(UnitLevels.FIREWORK_TAG)) {
            event.setCanceled(true);
        }
    }

    /** Re-applies the level health bonus whenever a hired unit enters a level (load, dimension change). */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof NpcEntity unit && !event.getLevel().isClientSide && unit.isHired()) {
            UnitLevels.applyHealthBonus(unit);
        }
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof NpcEntity unit)
                || unit.level().isClientSide
                || !unit.isHired()
                || unit.getRandom().nextInt(IDLE_SPEECH_ODDS) != 0) {
            return;
        }
        HiredUnit state = HiredUnits.get(unit);
        if (state.command().state() != Hireable.CommandState.FOLLOW || state.command().halted()) {
            return;
        }
        ServerPlayer owner = HiredUnits.onlineOwner(unit);
        if (owner != null && UnitLevels.nearOwner(unit, owner)) {
            NpcSpeech.say(unit, owner, NpcSpeech.HIRED_IDLE);
        }
    }
}
