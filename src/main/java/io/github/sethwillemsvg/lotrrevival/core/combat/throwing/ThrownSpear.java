package io.github.sethwillemsvg.lotrrevival.core.combat.throwing;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** A thrown spear: renders as its item and keeps the (already worn) item to drop. */
public class ThrownSpear extends ThrownGear {
    public ThrownSpear(EntityType<? extends ThrownSpear> type, Level level) {
        super(type, level);
    }

    public ThrownSpear(Level level, LivingEntity owner) {
        super(ThrowingEntities.THROWN_SPEAR.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.TRIDENT;
    }

    @Override
    protected boolean landsPermanent() {
        return true;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.05;
    }
}
