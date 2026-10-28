package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Entry point of the waypoint system, called once from {@code WorldMod.init}. */
public final class WaypointSystem {
    private WaypointSystem() {}

    public static void init() {
        WaypointAttachments.init();
        ModDataRegistries.registerSynced(ModDataRegistries.<WaypointRegion>waypointRegion(), WaypointRegion.CODEC);
        ModDataRegistries.registerSynced(ModDataRegistries.<Waypoint>waypoint(), Waypoint.CODEC);
        NeoForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) -> WaypointCommands.register(event.getDispatcher()));
    }

    /** English strings for the waypoint system; called from datagen. */
    public static void contributeLang() {
        io.github.sethwillemsvg.lotrrevival.world.travel.TravelLang.contribute();
        ModLangProvider.contribute(lang -> {
            lang.add(
                    "message.lotrrevival.region_unlocked",
                    "New lands discovered: %s. Its waypoints are now on your map.");
            lang.add("message.lotrrevival.waypoint_barred", "%s will not receive you");
            lang.add("waypoint.state.lotrrevival.locked", "locked");
            lang.add("waypoint.state.lotrrevival.usable", "usable");
            lang.add("waypoint.state.lotrrevival.barred", "barred");
            lang.add(
                    "commands.lotrrevival.waypoint.list.header",
                    "%1$s: travel clock %2$s, %3$s regions unlocked");
            lang.add("commands.lotrrevival.waypoint.list.entry", "  %1$s (%2$s): %3$s, needs %4$s");
            lang.add("commands.lotrrevival.waypoint.list.custom", "  Custom %1$s, %2$s at %3$s, used %4$s times");
            lang.add("commands.lotrrevival.waypoint.region.unknown", "Unknown waypoint region: %s");
            lang.add("commands.lotrrevival.waypoint.unlock", "Unlocked %1$s regions for %2$s players");
            lang.add("commands.lotrrevival.waypoint.lock", "Locked %1$s regions for %2$s players");
            lang.add("commands.lotrrevival.waypoint.clock", "Set the travel clock of %1$s players to %2$s");
        });
    }
}
