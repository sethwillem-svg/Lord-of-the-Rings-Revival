package io.github.sethwillemsvg.lotrrevival.core.combat.poison;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Adds the dark green "Poisoned" line to poisoned items. The event only fires on the client. */
public final class PoisonTooltip {
    public static final String KEY = "tooltip.lotrrevival.poisoned";

    private PoisonTooltip() {}

    public static Component line() {
        return Component.translatable(KEY).withStyle(ChatFormatting.DARK_GREEN);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (PoisonItems.isPoisoned(event.getItemStack())) {
            event.getToolTip().add(line());
        }
    }
}
