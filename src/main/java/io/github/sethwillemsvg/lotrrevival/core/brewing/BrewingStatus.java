package io.github.sethwillemsvg.lotrrevival.core.brewing;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/** Status lines shared by the barrel screen and the barrel item tooltip. */
public final class BrewingStatus {
    private BrewingStatus() {}

    /** "Brewing Ale (Hearty)". */
    public static Component brewing(Item drink, DrinkTier tier) {
        return Component.translatable("brewing.lotrrevival.status.brewing", drink.getDescription(), tier.displayName());
    }

    /** "Ale (Hearty), 10 left". */
    public static Component full(Item drink, DrinkTier tier, int servings) {
        return Component.translatable(
                "brewing.lotrrevival.status.full", drink.getDescription(), tier.displayName(), servings);
    }

    /** "Empty". */
    public static Component empty() {
        return Component.translatable("brewing.lotrrevival.status.empty");
    }

    /** "Ready to brew Ale". */
    public static Component ready(Item drink) {
        return Component.translatable("brewing.lotrrevival.status.ready", drink.getDescription());
    }
}
