package io.github.sethwillemsvg.lotrrevival.client.combat;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client combat wiring, called from combat.CombatModule on a client only: warning config, marker layer, parry input. */
public final class CombatClientModule {
    private CombatClientModule() {}

    public static void init(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, WarningConfig.SPEC, "lotr_revival-combat-client.toml");
        modBus.addListener((ModConfigEvent.Loading e) -> {
            if (e.getConfig().getSpec() == WarningConfig.SPEC) WarningConfig.logInUse();
        });
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(LotrRevival.id("attack_warnings"), MarkerRenderer::draw));
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(ParryInput.KEY));
        NeoForge.EVENT_BUS.addListener(ParryInput::onInteract);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            AttackMarkers.tick();
            ParryInput.tick();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> AttackMarkers.clear());
    }
}
