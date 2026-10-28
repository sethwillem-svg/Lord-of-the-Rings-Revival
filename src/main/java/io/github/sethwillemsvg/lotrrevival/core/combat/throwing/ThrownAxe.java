package io.github.sethwillemsvg.lotrrevival.core.combat.throwing;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** A thrown throwing axe: renders as its item and always drops it again where it lands. */
public class ThrownAxe extends ThrownGear {
    public ThrownAxe(EntityType<? extends ThrownAxe> type, Level level) {
        super(type, level);
    }

    public ThrownAxe(Level level, LivingEntity owner) {
        super(ThrowingEntities.THROWN_AXE.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_AXE;
    }

    @Override
    protected boolean landsPermanent() {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04;
    }
}
