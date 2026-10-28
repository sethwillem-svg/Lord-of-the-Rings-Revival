package io.github.sethwillemsvg.lotrrevival.world.travel;

import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;

/** English strings for fast travel; called from {@code WaypointSystem.contributeLang}. */
public final class TravelLang {
    private TravelLang() {}

    public static void contribute() {
        ModLangProvider.contribute(lang -> {
            lang.add("message.lotrrevival.travel.disabled", "Fast travel is turned off.");
            lang.add("message.lotrrevival.travel.wrong_dimension", "You can only fast travel in Middle-earth.");
            lang.add("message.lotrrevival.travel.unknown", "That waypoint no longer exists.");
            lang.add("message.lotrrevival.travel.locked", "You have not discovered %s yet.");
            lang.add("message.lotrrevival.travel.wait", "You must wait %1$s more before travelling to %2$s.");
            lang.add("message.lotrrevival.travel.targeted", "You cannot travel while enemies are hunting you.");
            lang.add("message.lotrrevival.travel.sleeping", "You cannot travel while asleep.");
            lang.add("message.lotrrevival.travel.start", "Travelling to %1$s in %2$s seconds. Stand still.");
            lang.add("message.lotrrevival.travel.tick", "%s");
            lang.add("message.lotrrevival.travel.cancelled", "Fast travel cancelled.");
            lang.add("message.lotrrevival.travel.arrived", "You arrive at %s.");
            lang.add("commands.lotrrevival.waypoint.travel.bad_ref", "Not a waypoint reference: %s");
        });
    }
}
