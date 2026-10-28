package io.github.sethwillemsvg.lotrrevival.core.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (lotrrevival-server.toml). Alignment keys are added by the alignment ticket. */
public final class CoreConfig {
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("alignment");
        // Placeholder: alignment config keys land with the alignment API ticket.
        builder.pop();
        SPEC = builder.build();
    }

    private CoreConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "lotrrevival-server.toml");
    }
}
