package io.github.sethwillemsvg.lotrrevival.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Parry sounds, sparks and the fx payload, played server side with our own sounds (CombatSounds, synthesized by
 * tools/sfx/make_combat_sfx.sh) and particles (CombatParticles).
 */
final class CombatFx {
    private CombatFx() {}

    /** Between the two fighters, at chest height. */
    static Vec3 contact(LivingEntity defender, Entity attacker) {
        Vec3 a = defender.position().add(0, defender.getBbHeight() * 0.7, 0);
        Vec3 b = attacker.position().add(0, attacker.getBbHeight() * 0.7, 0);
        return a.add(b.subtract(a).scale(0.35));
    }

    /** A player's perfect parry: the clang (plus the shield's thud when it was a shield), sparks, and the counter chime. */
    static void perfect(ServerLevel level, LivingEntity defender, Entity attacker, boolean shield) {
        Vec3 at = contact(defender, attacker);
        level.playSound(null, at.x, at.y, at.z, CombatSounds.PARRY_PERFECT.get(), SoundSource.PLAYERS, 1.0f, 0.95f + level.random.nextFloat() * 0.1f);
        if (shield) level.playSound(null, at.x, at.y, at.z, CombatSounds.SHIELD_BLOCK.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        level.sendParticles(CombatParticles.PARRY_SPARK.get(), at.x, at.y, at.z, 14, 0.1, 0.1, 0.1, 0.25);
        if (defender instanceof ServerPlayer p) p.playNotifySound(CombatSounds.COUNTER_READY.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
        fx(defender, ParryFxPayload.Kind.PERFECT);
    }

    static void glance(ServerLevel level, LivingEntity defender, Entity attacker) {
        Vec3 at = contact(defender, attacker);
        level.playSound(null, at.x, at.y, at.z, CombatSounds.PARRY_GLANCE.get(), SoundSource.PLAYERS, 1.0f, 0.95f + level.random.nextFloat() * 0.1f);
        level.sendParticles(CombatParticles.PARRY_SPARK.get(), at.x, at.y, at.z, 4, 0.08, 0.08, 0.08, 0.12);
        fx(defender, ParryFxPayload.Kind.GLANCE);
    }

    static void npcParry(ServerLevel level, LivingEntity defender, Entity attacker) {
        Vec3 at = contact(defender, attacker);
        boolean shield = "shield".equals(WeaponClass.of(Hands.other(defender)));
        level.playSound(null, at.x, at.y, at.z, CombatSounds.PARRY_PERFECT.get(), SoundSource.HOSTILE, shield ? 0.6f : 0.9f, 1.05f);
        if (shield) level.playSound(null, at.x, at.y, at.z, CombatSounds.SHIELD_BLOCK.get(), SoundSource.HOSTILE, 0.9f, 1.0f);
        level.sendParticles(CombatParticles.PARRY_SPARK.get(), at.x, at.y, at.z, 9, 0.1, 0.1, 0.1, 0.2);
        fx(defender, ParryFxPayload.Kind.NPC_PARRY);
    }

    /** A staggered NPC: a whoosh and stumble at its feet. */
    static void stagger(ServerLevel level, LivingEntity npc) {
        level.playSound(null, npc.getX(), npc.getY() + 0.5, npc.getZ(), CombatSounds.STAGGER.get(), SoundSource.HOSTILE, 0.9f,
                0.9f + level.random.nextFloat() * 0.2f);
    }

    /** A heavy wind-up: red motes around the attacker (everyone near sees them), and the low warning to the target only. */
    static void heavyStart(ServerLevel level, LivingEntity attacker, LivingEntity target) {
        level.sendParticles(CombatParticles.HEAVY_WARN.get(), attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(),
                6, attacker.getBbWidth() * 0.5, attacker.getBbHeight() * 0.3, attacker.getBbWidth() * 0.5, 0.02);
        if (target instanceof ServerPlayer sp) sp.playNotifySound(CombatSounds.HEAVY_WARN.get(), SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    private static void fx(LivingEntity defender, ParryFxPayload.Kind kind) {
        ParryFxPayload p = ParryFxPayload.of(defender.getId(), kind);
        AttackTiming.sink(p);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(defender, p);
    }
}
