package io.github.sethwillemsvg.lotrrevival.core.brewing;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** The five strength tiers of a brewed drink. Stored on drink items as {@code drink_strength} (0-4). */
public enum DrinkTier {
    THIN("thin", ChatFormatting.GRAY, 0.25, 0.50),
    FAIR("fair", ChatFormatting.WHITE, 0.5, 0.75),
    HEARTY("hearty", ChatFormatting.GREEN, 1.0, 1.00),
    STOUT("stout", ChatFormatting.AQUA, 2.0, 1.25),
    MIGHTY("mighty", ChatFormatting.GOLD, 3.0, 1.50);

    /** Tier used by a drink item that has no strength component. */
    public static final DrinkTier DEFAULT = HEARTY;

    public static final int MIN_INDEX = 0;
    public static final int MAX_INDEX = 4;

    private static final DrinkTier[] VALUES = values();

    private final String id;
    private final ChatFormatting color;
    private final double strengthMultiplier;
    private final double foodMultiplier;

    DrinkTier(String id, ChatFormatting color, double strengthMultiplier, double foodMultiplier) {
        this.id = id;
        this.color = color;
        this.strengthMultiplier = strengthMultiplier;
        this.foodMultiplier = foodMultiplier;
    }

    public String id() {
        return id;
    }

    public int index() {
        return ordinal();
    }

    public ChatFormatting color() {
        return color;
    }

    public double strengthMultiplier() {
        return strengthMultiplier;
    }

    public double foodMultiplier() {
        return foodMultiplier;
    }

    public String translationKey() {
        return "drink_strength.lotrrevival." + id;
    }

    /** Tier name in the tier's color. */
    public Component displayName() {
        return Component.translatable(translationKey()).withStyle(color);
    }

    /** floor(base duration x strength multiplier), in ticks. */
    public int scaleDuration(int baseTicks) {
        return (int) Math.floor(baseTicks * strengthMultiplier);
    }

    /** max(1, round-half-up(base nutrition x food multiplier)). */
    public int scaleNutrition(int baseNutrition) {
        return Math.max(1, (int) Math.floor(baseNutrition * foodMultiplier + 0.5));
    }

    /** Alcohol power: alcohol value x strength multiplier. */
    public double alcoholPower(double alcoholValue) {
        return alcoholValue * strengthMultiplier;
    }

    /** Tier for an index, clamped to the valid range. */
    public static DrinkTier byIndex(int index) {
        return VALUES[Math.max(MIN_INDEX, Math.min(MAX_INDEX, index))];
    }
}
