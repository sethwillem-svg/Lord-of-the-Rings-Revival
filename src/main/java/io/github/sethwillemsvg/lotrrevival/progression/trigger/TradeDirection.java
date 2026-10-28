package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Which way a trade went from the player's side; {@code ANY} is only meaningful as a condition. */
public enum TradeDirection implements StringRepresentable {
    BUY("buy"),
    SELL("sell"),
    ANY("any");

    public static final Codec<TradeDirection> CODEC = StringRepresentable.fromEnum(TradeDirection::values);

    private final String name;

    TradeDirection(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
