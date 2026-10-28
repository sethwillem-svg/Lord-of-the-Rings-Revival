package io.github.sethwillemsvg.lotrrevival.client.progression.shield;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/** Where a worn shield is drawn; plain logic so it can be checked without a renderer. */
public final class ShieldPlacement {
    public enum Spot {
        BACK,
        ARM,
        FRONT
    }

    private ShieldPlacement() {}

    /** Blocking wins over holding something; holding a weapon or tool moves the shield from the back to the arm. */
    public static Spot choose(boolean blocking, ItemStack mainHand) {
        if (blocking) {
            return Spot.FRONT;
        }
        return isWeaponOrTool(mainHand) ? Spot.ARM : Spot.BACK;
    }

    /** A sword, axe, pickaxe, shovel, hoe, or anything in a tag under {@code lotrrevival:weapons/}. */
    public static boolean isWeaponOrTool(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof DiggerItem) {
            return true;
        }
        return stack.getTags()
                .anyMatch(tag -> tag.location().getNamespace().equals(LOTRRevival.MOD_ID)
                        && tag.location().getPath().startsWith("weapons/"));
    }
}
