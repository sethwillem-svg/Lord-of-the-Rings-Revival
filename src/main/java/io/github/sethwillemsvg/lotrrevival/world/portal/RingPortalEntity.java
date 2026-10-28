package io.github.sethwillemsvg.lotrrevival.world.portal;

import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * A ring portal: a 3 x 3 hit box that grows over 120 ticks, then sends whatever touches it through. It never moves,
 * cannot be hurt except by {@code /kill} (and the void), is saved with the chunk, and removes itself in any
 * dimension other than the overworld and Middle-earth.
 */
public class RingPortalEntity extends Entity {
    /** Ticks to grow to full size. */
    public static final int FULL_GROWTH = 120;
    /** Hit box width and height. */
    public static final float SIZE = 3.0f;
    /** The hit box bottom sits this far above the block the portal is made over. */
    public static final double BOTTOM_ABOVE_BASE = 1.5;

    private static final EntityDataAccessor<Integer> GROWTH =
            SynchedEntityData.defineId(RingPortalEntity.class, EntityDataSerializers.INT);

    private final ContactTracker contact = new ContactTracker();

    public RingPortalEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(GROWTH, 0);
    }

    public int getGrowth() {
        return entityData.get(GROWTH);
    }

    public void setGrowth(int growth) {
        entityData.set(GROWTH, Math.max(0, Math.min(FULL_GROWTH, growth)));
    }

    public boolean isFullyGrown() {
        return getGrowth() >= FULL_GROWTH;
    }

    /** Growth from 0 to 1, smoothed between ticks for rendering. */
    public float growthFraction(float partialTick) {
        float growth = getGrowth();
        if (growth < FULL_GROWTH) {
            growth += partialTick;
        }
        return Math.min(1.0f, growth / FULL_GROWTH);
    }

    public ContactTracker contact() {
        return contact;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (level.dimension() != Level.OVERWORLD && !level.dimension().equals(ModDimensions.MIDDLE_EARTH)) {
            discard();
            return;
        }
        int growth = getGrowth();
        if (growth < FULL_GROWTH) {
            setGrowth(growth + 1);
            return;
        }
        if (tickCount % 20 == 0 && level.dimension().equals(ModDimensions.MIDDLE_EARTH)) {
            RingPortalTravel.discardIfStaleTwin(this, level);
            if (isRemoved()) {
                return;
            }
        }
        RingPortalTravel.tickContact(this, level);
    }

    private void clientTick() {
        float fraction = growthFraction(0.0f);
        if (random.nextFloat() < 0.15f + 0.6f * fraction) {
            double half = SIZE * 0.5 * fraction;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = half * (0.85 + 0.2 * random.nextDouble());
            double cy = getY() + SIZE * 0.5;
            level().addParticle(
                    ParticleTypes.END_ROD,
                    getX() + Math.cos(angle) * radius,
                    cy + Math.sin(angle) * radius,
                    getZ() + (random.nextDouble() - 0.5) * 0.4,
                    0.0,
                    0.01,
                    0.0);
        }
        if (fraction >= 1.0f && random.nextFloat() < 0.2f) {
            level().addParticle(
                    ParticleTypes.FLAME,
                    getX() + (random.nextDouble() - 0.5) * SIZE * 0.6,
                    getY() + random.nextDouble() * SIZE,
                    getZ() + (random.nextDouble() - 0.5) * 0.4,
                    0.0,
                    0.0,
                    0.0);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setGrowth(tag.getInt("growth"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("growth", getGrowth());
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {}

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }
}
