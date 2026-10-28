package io.github.sethwillemsvg.lotrrevival.core.combat.poison;

import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.function.Predicate;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * A melee hit that deals damage, struck by a living attacker holding a poisoned item in the main hand,
 * poisons the target (Poison I, 60-140 ticks, x0.5 on Easy, x1.5 on Hard). Targets in
 * {@code #lotrrevival:poison_immune} are skipped; vanilla undead ignore the effect on their own.
 */
public final class PoisonHits {
    public static final int MIN_TICKS = 60;
    public static final int MAX_TICKS = 140;

    private PoisonHits() {}

    /** Poison duration in ticks for one hit: uniform 60-140, scaled by difficulty. */
    public static int durationTicks(RandomSource random, Difficulty difficulty) {
        int base = MIN_TICKS + random.nextInt(MAX_TICKS - MIN_TICKS + 1);
        return switch (difficulty) {
            case EASY -> Math.round(base * 0.5f);
            case HARD -> Math.round(base * 1.5f);
            default -> base;
        };
    }

    public static boolean isImmune(LivingEntity target) {
        return target.getType().is(ModTags.EntityTypes.POISON_IMMUNE);
    }

    /** Melee means the attacker itself struck: not an arrow, thorns or other indirect damage. */
    public static boolean isMelee(DamageSource source) {
        return source.getDirectEntity() != null
                && source.getDirectEntity() == source.getEntity()
                && !source.is(DamageTypeTags.IS_PROJECTILE)
                && !source.is(DamageTypes.THORNS)
                && !source.is(DamageTypes.EXPLOSION)
                && !source.is(DamageTypes.PLAYER_EXPLOSION);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0f) {
            return;
        }
        tryPoison(event.getSource(), event.getEntity(), PoisonHits::isImmune);
    }

    /** Applies poison if the source is a poisoned melee hit and the target is not immune. Returns whether it did. */
    public static boolean tryPoison(DamageSource source, LivingEntity target, Predicate<LivingEntity> immune) {
        if (target.level().isClientSide() || !isMelee(source) || !(source.getEntity() instanceof LivingEntity attacker)) {
            return false;
        }
        ItemStack held = attacker.getMainHandItem();
        if (!PoisonItems.isPoisoned(held) || immune.test(target)) {
            return false;
        }
        int ticks = durationTicks(target.getRandom(), target.level().getDifficulty());
        return target.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, 0), attacker);
    }
}
