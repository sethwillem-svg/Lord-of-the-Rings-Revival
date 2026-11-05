package io.github.sethwillemsvg.lotrrevival.nodemap;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Player-facing text: translation keys {@code nodemap.lotr_revival.<key>} with English fallbacks until the add-on has lang datagen. */
public final class Msg {
    private Msg() {}

    public static MutableComponent t(String key, String fallback, Object... args) {
        return Component.translatableWithFallback("nodemap.lotr_revival." + key, fallback, args);
    }
}
