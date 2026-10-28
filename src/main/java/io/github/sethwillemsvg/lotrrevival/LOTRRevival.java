package io.github.sethwillemsvg.lotrrevival;

import com.mojang.logging.LogUtils;
import io.github.sethwillemsvg.lotrrevival.core.CoreMod;
import io.github.sethwillemsvg.lotrrevival.core.config.CoreConfig;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import io.github.sethwillemsvg.lotrrevival.datagen.DataGenerators;
import io.github.sethwillemsvg.lotrrevival.npc.NpcMod;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionMod;
import io.github.sethwillemsvg.lotrrevival.world.WorldMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Entry point for The Lord of the Rings: Revival.
 *
 * <p>Keep this class thin. Each pod wires itself in through {@link ModRegistries}
 * or its own entry class; nothing pod-specific belongs here.
 */
@Mod(LOTRRevival.MOD_ID)
public final class LOTRRevival {
    public static final String MOD_ID = "lotrrevival";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LOTRRevival(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistries.register(modEventBus);
        CoreMod.init(modEventBus);
        WorldMod.init(modEventBus);
        NpcMod.init(modEventBus);
        ProgressionMod.init(modEventBus);
        CoreConfig.register(modContainer);
        modEventBus.addListener(DataGenerators::gatherData);
        LOGGER.info("The Lord of the Rings: Revival loading");
    }

    /** Builds a {@code lotrrevival:<path>} id. Use this instead of hand-writing namespaces. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
