package io.github.sethwillemsvg.lotrrevival.npc.hire.horn;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The squadron line shared by the horn and sword tooltips. */
final class SquadronTooltip {
    private SquadronTooltip() {}

    static void add(ItemStack stack, List<Component> tooltip) {
        String squadron = HornComponents.squadronOf(stack);
        tooltip.add(squadron.isEmpty()
                ? Component.translatable("item.lotrrevival.squadron.none").withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.lotrrevival.squadron", squadron).withStyle(ChatFormatting.GRAY));
    }
}
