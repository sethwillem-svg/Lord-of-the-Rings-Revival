package io.github.sethwillemsvg.lotrrevival.events.types;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.events.EventParticipant;
import io.github.sethwillemsvg.lotrrevival.events.EventsConfig;
import io.github.sethwillemsvg.lotrrevival.events.SiteRoster;
import io.github.sethwillemsvg.lotrrevival.events.WorldEvent;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventType;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisApi;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRandom;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;

import java.util.List;

/**
 * Muster: a nemesis gathers its band; no opposition unless players attack. Undisturbed for the duration, the lead wins (renown
 * +3, +1 bodyguard next time); if the nemesis dies or escapes, the opposition wins (+20 with each faction hostile to it).
 */
public final class MusterEvent implements WorldEventType {
    public static final ResourceLocation ID = LotrRevival.id("muster");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public List<String> venueKinds() {
        return List.of("camp", "outpost", "ruin");
    }

    @Override
    public String rejectReason(SiteRoster.Pick pick) {
        return NemesisApi.atLarge(pick.lead()).isEmpty() ? "no AT_LARGE nemesis of " + pick.lead() : null;
    }

    @Override
    public int stage(WorldEvent ev, ServerLevel level, SiteRoster.Pick pick) {
        BoundingBox b = ev.venue == null ? null : ev.venue.bounds();
        List<NpcEntity> band = Bands.spawn(level, ev, pick.lead(), EventParticipant.LEAD, NemesisRandom.between(2, 4), ev.centre, b, pick, true);
        if (ev.nemeses.isEmpty()) return 0;   // a muster needs its nemesis
        return band.size();
    }

    @Override
    public WorldEvent.Outcome check(WorldEvent ev, ServerLevel level) {
        return ev.nemeses.isEmpty() ? WorldEvent.Outcome.OPPOSITION_WON : WorldEvent.Outcome.NONE;
    }

    @Override
    public WorldEvent.Outcome onTimeout() {
        return WorldEvent.Outcome.LEAD_WON;
    }

    @Override
    public int bonus(WorldEvent.Outcome outcome) {
        return outcome == WorldEvent.Outcome.OPPOSITION_WON ? EventsConfig.i(EventsConfig.BONUS_MUSTER) : 0;
    }

    @Override
    public String titleKey(String viewerSide) {
        return EventParticipant.LEAD.equals(viewerSide) ? "lotr_revival.event.title.muster.lead" : "lotr_revival.event.title.muster.opposition";
    }
}
