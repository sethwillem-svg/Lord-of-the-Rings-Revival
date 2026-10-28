package io.github.sethwillemsvg.lotrrevival.npc.ai.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.AlignmentCreditor;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelation;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitWitnesses;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeech;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Hit retaliation (only the NPC that was hit reacts) and kill witnesses (nearby allies turn on the killer). */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class RetaliationEvents {
    /** Allies this close to a slain NPC react to the killer. */
    public static final double WITNESS_RANGE = 8.0;
    /** At most this many witnesses speak. */
    public static final int MAX_WITNESS_LINES = 5;

    private RetaliationEvents() {}

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof NpcEntity npc) || npc.level().isClientSide || !npc.isAlive()) {
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            npc.retaliation().provoke(attacker);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof NpcEntity victim) || victim.level().isClientSide) {
            return;
        }
        Optional<ServerPlayer> killer = killerOf(event.getSource());
        if (killer.isEmpty() || !NpcCombat.isAttackable(killer.get())) {
            return;
        }
        ServerPlayer player = killer.get();
        AABB box = victim.getBoundingBox().inflate(WITNESS_RANGE);
        List<NpcEntity> reacting = victim.level().getEntitiesOfClass(NpcEntity.class, box).stream()
                .filter(other -> other != victim
                        && other.isAlive()
                        && other.getTarget() == null
                        && !UnitWitnesses.excluded(other, player)
                        && other.distanceToSqr(victim) <= WITNESS_RANGE * WITNESS_RANGE
                        && sameSide(victim, other)
                        && other.retaliation().wouldReactTo(player))
                .sorted(Comparator.comparingDouble(other -> other.distanceToSqr(victim)))
                .toList();
        int spoken = 0;
        for (NpcEntity witness : reacting) {
            witness.retaliation().provoke(player);
            if (spoken < MAX_WITNESS_LINES) {
                NpcSpeech.say(witness, player, NpcSpeech.WITNESS);
                spoken++;
            }
        }
    }

    /** The player credited with a kill: direct, their own projectile or their tamed wolf. Hired units are excluded. */
    private static Optional<ServerPlayer> killerOf(DamageSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof AlignmentCreditor) {
            return Optional.empty();
        }
        if (entity instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        if (entity instanceof TamableAnimal pet && pet.getOwner() instanceof ServerPlayer owner) {
            return Optional.of(owner);
        }
        return Optional.empty();
    }

    private static boolean sameSide(NpcEntity victim, NpcEntity other) {
        return FactionRelations.relation(victim.level().registryAccess(), victim.faction(), other.faction())
                == FactionRelation.ALLY;
    }
}
