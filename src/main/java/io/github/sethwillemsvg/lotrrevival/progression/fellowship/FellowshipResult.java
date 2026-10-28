package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import net.minecraft.network.chat.Component;

/** Outcome of a fellowship action: success or a refusal, each with the line shown to the actor. */
public record FellowshipResult(boolean ok, Component message) {
    public static FellowshipResult ok(String key, Object... args) {
        return new FellowshipResult(true, Component.translatable(key, args));
    }

    public static FellowshipResult fail(String key, Object... args) {
        return new FellowshipResult(false, Component.translatable(key, args));
    }
}
