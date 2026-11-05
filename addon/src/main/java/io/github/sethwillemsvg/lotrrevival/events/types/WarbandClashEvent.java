package io.github.sethwillemsvg.lotrrevival.events.types;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.events.EventParticipant;
import io.github.sethwillemsvg.lotrrevival.events.EventsConfig;
import io.github.sethwillemsvg.lotrrevival.events.SiteRoster;
import io.github.sethwillemsvg.lotrrevival.events.WorldEvent;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventType;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRandom;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;

import java.util.List;

/** Warband clash: a band of faction A and a band of a faction HOSTILE to A; last side standing wins (+15 with each winner). */
public final class WarbandClashEvent implements WorldEventType {
    public static final ResourceLocation ID = LotrRevival.id("warband_clash");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public List<String> venueKinds() {
        return List.of("bridge", "ruin", "outpost");
    }

    @Override
    public int stage(WorldEvent ev, ServerLevel level, SiteRoster.Pick pick) {
        BoundingBox b = ev.venue == null ? null : ev.venue.bounds();
        List<NpcEntity> lead = Bands.spawn(level, ev, pick.lead(), EventParticipant.LEAD, NemesisRandom.between(4, 6), ev.centre.offset(-6, 0, 0),
                b, pick, true);
        List<NpcEntity> opp = Bands.spawn(level, ev, pick.opposition(), EventParticipant.OPPOSITION, NemesisRandom.between(4, 6),
                ev.centre.offset(6, 0, 0), b, pick, false);
        if (lead.isEmpty() || opp.isEmpty()) return 0;
        Bands.engage(lead, opp);
        return lead.size() + opp.size();
    }

    @Override
    public WorldEvent.Outcome check(WorldEvent ev, ServerLevel level) {
        int l = ev.count(EventParticipant.LEAD), o = ev.count(EventParticipant.OPPOSITION);
        if (l == 0 && o == 0) return WorldEvent.Outcome.OPPOSITION_WON;
        if (o == 0) return WorldEvent.Outcome.LEAD_WON;
        if (l == 0) return WorldEvent.Outcome.OPPOSITION_WON;
        return WorldEvent.Outcome.NONE;
    }

    @Override
    public int bonus(WorldEvent.Outcome outcome) {
        return EventsConfig.i(EventsConfig.BONUS_CLASH);
    }

    @Override
    public String titleKey(String viewerSide) {
        return "lotr_revival.event.title.warband_clash";
    }
}
