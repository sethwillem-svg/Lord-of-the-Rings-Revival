package io.github.sethwillemsvg.lotrrevival.core.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;

/**
 * A lance in the main hand slows a walking wielder by 20% (players and mobs); the slowdown is gone
 * when the wielder rides or switches items. Driven by equipment-change and mount events, so there is
 * no per-tick cost.
 */
public final class LanceSlow {
    public static final ResourceLocation MODIFIER_ID = LOTRRevival.id("lance_slow");
    public static final double AMOUNT = -0.2;

    private LanceSlow() {}

    /** Whether the stack is a lance. */
    public static boolean isLance(ItemStack stack) {
        return WeaponStats.of(stack).filter(p -> p.weaponClass() == WeaponClass.LANCE).isPresent();
    }

    /** Whether the wielder should be slowed: holding a lance and not riding. */
    public static boolean shouldSlow(boolean holdingLance, boolean riding) {
        return holdingLance && !riding;
    }

    /** Applies or removes the modifier so it matches {@link #shouldSlow}. Server side only. */
    public static void refresh(LivingEntity entity, boolean holdingLance, boolean riding) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean want = shouldSlow(holdingLance, riding);
        boolean has = speed.hasModifier(MODIFIER_ID);
        if (want && !has) {
            speed.addTransientModifier(
                    new AttributeModifier(MODIFIER_ID, AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!want && has) {
            speed.removeModifier(MODIFIER_ID);
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.MAINHAND) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        refresh(entity, isLance(event.getTo()), entity.isPassenger());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMount(EntityMountEvent event) {
        if (event.isCanceled() || !(event.getEntityMounting() instanceof LivingEntity rider)) {
            return;
        }
        if (rider.level().isClientSide) {
            return;
        }
        refresh(rider, isLance(rider.getMainHandItem()), event.isMounting());
    }
}
