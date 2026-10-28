package io.github.sethwillemsvg.lotrrevival.core.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Fixes the network protocol version for every pod's payloads. Bumped by Core on breaking wire changes. */
public final class CoreNetwork {
    public static final String PROTOCOL_VERSION = "1";

    private CoreNetwork() {}

    public static PayloadRegistrar registrar(RegisterPayloadHandlersEvent event) {
        return event.registrar(PROTOCOL_VERSION);
    }
}
