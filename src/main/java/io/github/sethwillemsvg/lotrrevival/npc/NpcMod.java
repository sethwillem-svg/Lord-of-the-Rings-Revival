package io.github.sethwillemsvg.lotrrevival.npc;

import io.github.sethwillemsvg.lotrrevival.npc.data.NpcLoadoutData;
import io.github.sethwillemsvg.lotrrevival.npc.data.NpcRoleData;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.item.NpcItems;
import io.github.sethwillemsvg.lotrrevival.npc.name.NpcNames;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Entry point for the NPC pod; the NPC pod owns this file. */
public final class NpcMod {
    private NpcMod() {}

    public static void init(IEventBus modBus) {
        NpcEntityTypes.init(modBus);
        NpcItems.init(modBus);
        NeoForge.EVENT_BUS.addListener(NpcMod::onAddReloadListeners);
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new NpcRoleData());
        event.addListener(new NpcNames());
        event.addListener(new NpcLoadoutData());
    }
}
