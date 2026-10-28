package io.github.sethwillemsvg.lotrrevival.progression.network;

import io.github.sethwillemsvg.lotrrevival.core.network.CoreNetwork;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers the Progression pod's payloads. Ids follow {@code lotrrevival:progression/<name>}.
 * Empty for now; later tickets add one registration line per payload.
 */
public final class ProgressionPayloads {
    private ProgressionPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = CoreNetwork.registrar(event);
        // Later tickets: registrar.playToClient(...), registrar.playToServer(...)
    }
}
