package io.github.sethwillemsvg.lotrrevival;

import io.github.sethwillemsvg.lotrrevival.bench.BenchCommands;
import io.github.sethwillemsvg.lotrrevival.combat.CombatModule;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Add-on for the Middle-earth mod. Client side it re-poses Middle-earth NPCs with our clips (client.anim) and can draw them
 * with PulseLib (client.render); /lotrbench spawns NPC crowds to compare renderers.
 */
@Mod(LotrRevival.MOD_ID)
public final class LotrRevival {
    public static final String MOD_ID = "lotr_revival";

    public LotrRevival(IEventBus modBus) {
        CombatModule.init(modBus);   // attack timing, parry, warning payloads (registers the npc_attack payload, version 2)
        io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapModule.init(modBus);
        io.github.sethwillemsvg.lotrrevival.structures.StructuresModule.init(modBus);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> BenchCommands.register(e.getDispatcher()));
        // benchmark NPCs never come back from disk: each run (and each reload of the world) starts from the count asked for
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (e.loadedFromDisk() && e.getEntity().getTags().contains(BenchCommands.TAG)) e.setCanceled(true);
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
