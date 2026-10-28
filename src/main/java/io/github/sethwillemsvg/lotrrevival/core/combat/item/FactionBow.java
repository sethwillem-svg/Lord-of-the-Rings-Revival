package io.github.sethwillemsvg.lotrrevival.core.combat.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A faction bow (specs/core/combat-items.md, "Ranged weapons"): a vanilla bow with its own full-draw
 * time and a factor on the base damage of the arrow it shoots. Arrows, Infinity, Power and the other
 * vanilla bow enchantments work as on a vanilla bow.
 */
public class FactionBow extends BowItem {
    private final int fullDrawTicks;
    private final double arrowDamageFactor;

    /**
     * @param durability uses
     * @param fullDrawTicks ticks of drawing for full power (vanilla bow: 20)
     * @param arrowDamageFactor multiplier on the shot arrow's base damage
     */
    public FactionBow(int durability, int fullDrawTicks, double arrowDamageFactor) {
        super(new Item.Properties().durability(durability));
        this.fullDrawTicks = fullDrawTicks;
        this.arrowDamageFactor = arrowDamageFactor;
    }

    public int fullDrawTicks() {
        return fullDrawTicks;
    }

    public double arrowDamageFactor() {
        return arrowDamageFactor;
    }

    /** How far the bow is drawn after {@code usedTicks} ticks of drawing, 0 to 1 (the {@code pull} model predicate). */
    public float drawFraction(int usedTicks) {
        return Math.min(1.0F, (float) usedTicks / fullDrawTicks);
    }

    /**
     * Vanilla computes the power from a 20-tick draw. Rescale the drawn time so a bow whose full draw
     * is {@code fullDrawTicks} reaches the vanilla power curve at the same fraction of its draw.
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity shooter, int timeLeft) {
        int used = getUseDuration(stack, shooter) - timeLeft;
        int scaled = Math.round(used * (float) BowItem.MAX_DRAW_DURATION / fullDrawTicks);
        super.releaseUsing(stack, level, shooter, getUseDuration(stack, shooter) - scaled);
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        arrow.setBaseDamage(arrow.getBaseDamage() * arrowDamageFactor);
        return arrow;
    }
}
