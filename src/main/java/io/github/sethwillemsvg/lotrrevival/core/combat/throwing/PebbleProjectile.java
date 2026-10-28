package io.github.sethwillemsvg.lotrrevival.core.combat.throwing;

import io.github.sethwillemsvg.lotrrevival.core.combat.gear.RangedGear;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A pebble thrown from a sling. Deals {@link #FULL_DAMAGE} at full wind-up (less when wound up less),
 * and is destroyed on any impact: it is never picked up again.
 */
public class PebbleProjectile extends ThrowableItemProjectile {
    /** Damage of a pebble thrown at full wind-up, in half-hearts. */
    public static final float FULL_DAMAGE = 2.0F;

    private float damage = FULL_DAMAGE;

    public PebbleProjectile(EntityType<? extends PebbleProjectile> type, Level level) {
        super(type, level);
    }

    public PebbleProjectile(Level level, LivingEntity shooter) {
        super(PebbleTypes.PEBBLE.get(), shooter, level);
    }

    public float damage() {
        return damage;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    protected Item getDefaultItem() {
        return RangedGear.PEBBLE.get();
    }

    /** Hurts the target as a thrown projectile; separate from {@link #onHitEntity} so tests can call it. */
    public void damageTarget(Entity target) {
        target.hurt(damageSources().thrown(this, getOwner()), damage);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        damageTarget(result.getEntity());
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Damage")) {
            damage = tag.getFloat("Damage");
        }
    }
}
