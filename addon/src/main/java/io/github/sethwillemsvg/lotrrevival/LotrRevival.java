package io.github.sethwillemsvg.lotrrevival;

import io.github.sethwillemsvg.lotrrevival.bench.BenchCommands;
import io.github.sethwillemsvg.lotrrevival.client.anim.ClientAttack;
import io.github.sethwillemsvg.lotrrevival.combat.AttackTiming;
import io.github.sethwillemsvg.lotrrevival.combat.NpcAttackPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Add-on for the Middle-earth mod. Client side it re-poses Middle-earth NPCs with our clips (client.anim) and can draw them
 * with PulseLib (client.render); /lotrbench spawns NPC crowds to compare renderers.
 */
@Mod(LotrRevival.MOD_ID)
public final class LotrRevival {
    public static final String MOD_ID = "lotr_revival";

    public LotrRevival(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToClient(NpcAttackPayload.TYPE,
                NpcAttackPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientAttack.handle(p))));
        AttackTiming.register();
        io.github.sethwillemsvg.lotrrevival.nemesis.NemesisModule.init(modBus);
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
