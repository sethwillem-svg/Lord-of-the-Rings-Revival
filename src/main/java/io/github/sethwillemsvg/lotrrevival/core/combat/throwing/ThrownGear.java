package io.github.sethwillemsvg.lotrrevival.core.combat.throwing;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared behavior of thrown spears and throwing axes (specs/core/combat-items.md, "Thrown weapons"). The projectile
 * carries a fixed damage worked out at the throw. On any hit it deals that damage (entity hits only), then turns into
 * an item pickup at the spot and removes itself, unless the thrower was a creative player, in which case the thrown
 * copy is not a real item and just vanishes.
 */
public abstract class ThrownGear extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> CRIT =
            SynchedEntityData.defineId(ThrownGear.class, EntityDataSerializers.BOOLEAN);

    private float damage;
    private boolean noPickup;

    protected ThrownGear(EntityType<? extends ThrownGear> type, Level level) {
        super(type, level);
    }

    protected ThrownGear(EntityType<? extends ThrownGear> type, LivingEntity owner, Level level) {
        super(type, owner, level);
    }

    /** Fills in the per-throw values; called once right after construction. */
    public void configure(ItemStack stack, float damage, boolean crit, boolean noPickup) {
        setItem(stack);
        this.damage = damage;
        this.noPickup = noPickup;
        this.entityData.set(CRIT, crit);
    }

    /** Damage dealt on an entity hit, before enchantment bonuses. */
    public float damage() {
        return damage;
    }

    public boolean isCrit() {
        return this.entityData.get(CRIT);
    }

    /** Whether landing leaves nothing behind (creative throw). */
    public boolean leavesNothing() {
        return noPickup;
    }

    /** Whether the landed item never despawns (spears, like tridents). */
    protected abstract boolean landsPermanent();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CRIT, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isCrit()) {
            Vec3 motion = getDeltaMovement();
            for (int i = 0; i < 4; i++) {
                level().addParticle(
                        ParticleTypes.CRIT,
                        getX() + motion.x * i / 4.0,
                        getY() + motion.y * i / 4.0,
                        getZ() + motion.z * i / 4.0,
                        -motion.x,
                        -motion.y + 0.2,
                        -motion.z);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Entity target = result.getEntity();
        Entity owner = getOwner();
        DamageSource source = damageSources().thrown(this, owner == null ? this : owner);
        float amount = EnchantmentHelper.modifyDamage(server, getItem(), target, source, damage);
        if (target.hurt(source, amount)) {
            EnchantmentHelper.doPostAttackEffectsWithItemSource(server, target, source, getItem());
        }
        playSound(SoundEvents.TRIDENT_HIT, 1.0f, 1.0f);
        land(result.getLocation());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(level() instanceof ServerLevel)) {
            return;
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.NEUTRAL, 1.0f, 1.0f);
        Vec3 out = Vec3.atLowerCornerOf(result.getDirection().getNormal()).scale(0.2);
        land(result.getLocation().add(out));
    }

    /** Drops the weapon as an item at {@code pos} that only the thrower may take for 20 seconds, then removes this. */
    private void land(Vec3 pos) {
        if (!(level() instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        if (!noPickup) {
            ItemEntity drop = new ItemEntity(server, pos.x, pos.y, pos.z, getItem().copyWithCount(1));
            drop.setDeltaMovement(getDeltaMovement().scale(-0.02).add(0.0, 0.1, 0.0));
            drop.setPickUpDelay(10);
            if (landsPermanent()) {
                drop.setUnlimitedLifetime();
            }
            ThrownPickup.restrictToThrower(drop, getOwner(), server.getGameTime());
            server.addFreshEntity(drop);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putBoolean("Crit", isCrit());
        tag.putBoolean("NoPickup", noPickup);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        noPickup = tag.getBoolean("NoPickup");
        this.entityData.set(CRIT, tag.getBoolean("Crit"));
    }
}
