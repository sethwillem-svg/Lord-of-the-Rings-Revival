package io.github.sethwillemsvg.lotrrevival.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Parry sounds, sparks and the fx payload, played server side. TODO(spec ticket 8): our own sounds combat.parry_perfect,
 * combat.parry_glance, combat.heavy_warn and the parry_spark particle; vanilla sounds and particles stand in until then.
 */
final class CombatFx {
    private CombatFx() {}

    /** Between the two fighters, at chest height. */
    static Vec3 contact(LivingEntity defender, Entity attacker) {
        Vec3 a = defender.position().add(0, defender.getBbHeight() * 0.7, 0);
        Vec3 b = attacker.position().add(0, attacker.getBbHeight() * 0.7, 0);
        return a.add(b.subtract(a).scale(0.35));
    }

    static void perfect(ServerLevel level, LivingEntity defender, Entity attacker) {
        Vec3 at = contact(defender, attacker);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.6f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.9f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 12, 0.15, 0.15, 0.15, 0.4);
        fx(defender, ParryFxPayload.Kind.PERFECT);
    }

    static void glance(ServerLevel level, LivingEntity defender, Entity attacker) {
        Vec3 at = contact(defender, attacker);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 1.0f, 1.3f);
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 5, 0.1, 0.1, 0.1, 0.2);
        fx(defender, ParryFxPayload.Kind.GLANCE);
    }

    static void npcParry(ServerLevel level, LivingEntity defender, Entity attacker) {
        Vec3 at = contact(defender, attacker);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, 1.4f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.3);
        fx(defender, ParryFxPayload.Kind.NPC_PARRY);
    }

    /** The low warning to the target of a heavy swing only. */
    static void heavyWarn(ServerPlayer target) {
        target.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1.0f, 0.5f);
    }

    private static void fx(LivingEntity defender, ParryFxPayload.Kind kind) {
        ParryFxPayload p = ParryFxPayload.of(defender.getId(), kind);
        AttackTiming.sink(p);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(defender, p);
    }
}
