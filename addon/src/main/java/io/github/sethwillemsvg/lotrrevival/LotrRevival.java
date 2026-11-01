package io.github.sethwillemsvg.lotrrevival;

import io.github.sethwillemsvg.lotrrevival.bench.BenchCommands;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Add-on for the Middle-earth mod. Client side it re-poses Middle-earth NPCs with our clips (client.anim) and can draw them
 * with PulseLib (client.render); /lotrbench spawns NPC crowds to compare renderers.
 */
@Mod(LotrRevival.MOD_ID)
public final class LotrRevival {
    public static final String MOD_ID = "lotr_revival";

    public LotrRevival() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BenchCommands.register(e.getDispatcher()));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
