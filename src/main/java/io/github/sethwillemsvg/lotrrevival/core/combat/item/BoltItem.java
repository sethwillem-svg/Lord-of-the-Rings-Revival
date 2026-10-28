package io.github.sethwillemsvg.lotrrevival.core.combat.item;

import io.github.sethwillemsvg.lotrrevival.core.combat.ranged.BoltEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Crossbow ammunition. Extends {@link ArrowItem} so the shared projectile code spawns it, but it is
 * not in {@code minecraft:arrows}: vanilla bows and crossbows cannot load it, and only the
 * {@code lotrrevival:bolts} tag fits our crossbows.
 */
public class BoltItem extends ArrowItem {
    public BoltItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
        return new BoltEntity(level, shooter, ammo.copyWithCount(1), weapon);
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        BoltEntity bolt = new BoltEntity(level, pos.x(), pos.y(), pos.z(), stack.copyWithCount(1), null);
        bolt.pickup = AbstractArrow.Pickup.ALLOWED;
        return bolt;
    }
}
