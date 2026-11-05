package io.github.sethwillemsvg.lotrrevival.events.types;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.events.EventParticipant;
import io.github.sethwillemsvg.lotrrevival.events.EventsConfig;
import io.github.sethwillemsvg.lotrrevival.events.SiteRoster;
import io.github.sethwillemsvg.lotrrevival.events.WorldEvent;
import io.github.sethwillemsvg.lotrrevival.events.WorldEventType;
import io.github.sethwillemsvg.lotrrevival.nemesis.NemesisRandom;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Raid / defence: raiders of A attack a {@code village} venue owned by B. Defenders are the settlement's residents plus 2-4
 * spawned guards of B. Lead wins when half the defenders are dead or after 5 min with the raiders still in the field; defence
 * wins when every raider is dead or fled. Needs a village venue, so it is never picked until structures.md lands.
 */
public final class RaidEvent implements WorldEventType {
    public static final ResourceLocation ID = LotrRevival.id("raid");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public List<String> venueKinds() {
        return List.of("village");
    }

    @Override
    public boolean venueRequired() {
        return true;
    }

    @Override
    public int stage(WorldEvent ev, ServerLevel level, SiteRoster.Pick pick) {
        if (ev.venue == null) return 0;
        List<NpcEntity> defenders = new ArrayList<>(Bands.spawn(level, ev, pick.opposition(), EventParticipant.OPPOSITION,
                NemesisRandom.between(2, 4) - 1, ev.venue.centre(), ev.venue.bounds(), pick, false));
        int residents = 0;
        for (NpcEntity n : level.getEntitiesOfClass(NpcEntity.class, AABB.of(ev.venue.bounds()))) {
            if (!pick.opposition().equals(n.getFactionIdentifier()) || EventParticipant.of(n) != null) continue;
            n.setData(EventParticipant.TYPE, new EventParticipant(ev.id, EventParticipant.OPPOSITION, "resident"));
            ev.npcs.put(n.getUUID(), EventParticipant.OPPOSITION);
            defenders.add(n);
            residents++;
        }
        List<NpcEntity> raiders = Bands.spawn(level, ev, pick.lead(), EventParticipant.LEAD, NemesisRandom.between(5, 7),
                ev.venue.centre().offset(ev.venue.bounds().getXSpan() / 2 + 8, 0, 0), null, pick, true);
        LotrLog.dbg("events", "raid {}: {} raiders, {} defenders ({} residents)", ev.shortId(), raiders.size(), defenders.size(), residents);
        if (raiders.isEmpty() || defenders.isEmpty()) return 0;
        Bands.engage(raiders, defenders);
        return raiders.size() + defenders.size();
    }

    @Override
    public WorldEvent.Outcome check(WorldEvent ev, ServerLevel level) {
        int raiders = ev.count(EventParticipant.LEAD), defenders = ev.count(EventParticipant.OPPOSITION);
        if (raiders == 0) return WorldEvent.Outcome.OPPOSITION_WON;
        if (defenders * 2 <= ev.initialOpposition) return WorldEvent.Outcome.LEAD_WON;
        // TODO(spec §Event types): "5 min survived with the leader alive" uses any raider alive for now
        if (level.getGameTime() - ev.startTick >= 6000) return WorldEvent.Outcome.LEAD_WON;
        return WorldEvent.Outcome.NONE;
    }

    @Override
    public int bonus(WorldEvent.Outcome outcome) {
        return outcome == WorldEvent.Outcome.LEAD_WON ? EventsConfig.i(EventsConfig.BONUS_RAID) : EventsConfig.i(EventsConfig.BONUS_DEFENCE);
    }

    @Override
    public String titleKey(String viewerSide) {
        return EventParticipant.LEAD.equals(viewerSide) ? "lotr_revival.event.title.raid" : "lotr_revival.event.title.defence";
    }
}
