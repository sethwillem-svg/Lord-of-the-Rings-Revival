package io.github.sethwillemsvg.lotrrevival.core.combat.ranged;

import io.github.sethwillemsvg.lotrrevival.core.combat.gear.CrossbowGear;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A crossbow bolt (specs/core/combat-items.md, "Ranged weapons"): an arrow in every way (base damage
 * 2.0, same gravity and pickup rules) that drops back as a {@code bolt} item. The crossbow that
 * fired it scales the base damage by its material factor.
 */
public class BoltEntity extends AbstractArrow {
    public BoltEntity(EntityType<? extends BoltEntity> type, Level level) {
        super(type, level);
    }

    public BoltEntity(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(BoltEntities.BOLT.get(), owner, level, pickup, weapon);
    }

    public BoltEntity(Level level, double x, double y, double z, ItemStack pickup, @Nullable ItemStack weapon) {
        super(BoltEntities.BOLT.get(), x, y, z, level, pickup, weapon);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(CrossbowGear.BOLT.get());
    }
}
