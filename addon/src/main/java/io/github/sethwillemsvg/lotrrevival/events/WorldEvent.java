package io.github.sethwillemsvg.lotrrevival.events;

import io.github.sethwillemsvg.lotrrevival.core.api.StructureVenues;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One world event: a bounded scene with a lead side and an opposing side (spec World events). Active state is in memory only
 * (events never resume after a restart); ended events are kept as {@link Summary} in {@link WorldEventSavedData}.
 */
public final class WorldEvent {
    public enum State { ACTIVE, ENDED }

    public enum Outcome {
        NONE, LEAD_WON, OPPOSITION_WON, CANCELLED;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** One player who joined: damage dealt to each side's NPCs, and the side they are on. */
    public static final class PlayerPart {
        public float damageToLead, damageToOpposition;
        public boolean hurtByLead, hurtByOpposition;

        public String side() {
            if (damageToLead > damageToOpposition) return EventParticipant.OPPOSITION;
            if (damageToOpposition > damageToLead) return EventParticipant.LEAD;
            return hurtByLead ? EventParticipant.OPPOSITION : EventParticipant.LEAD;
        }
    }

    public final UUID id;
    public final ResourceLocation type;
    public final ResourceLocation dimension;
    public BlockPos centre;
    public int radius = 24;
    public UUID owner;
    public final List<ResourceLocation> leadFactions = new ArrayList<>(), oppFactions = new ArrayList<>();
    public @Nullable StructureVenues.Venue venue;
    public State state = State.ACTIVE;
    public Outcome outcome = Outcome.NONE;
    public boolean offscreen;
    public final long startTick;
    public long endTick = -1;
    public long deadline;
    /** Entity UUID -> side, for the NPCs this event spawned (and residents that joined the defence). */
    public final Map<UUID, String> npcs = new LinkedHashMap<>();
    /** Nemesis record ids taking part, by side. */
    public final Map<UUID, String> nemeses = new LinkedHashMap<>();
    public final Map<UUID, PlayerPart> players = new HashMap<>();
    public long ownerFarSinceTick = -1;
    public long removeNpcsAtTick = -1;
    public int initialLead, initialOpposition;

    public WorldEvent(UUID id, ResourceLocation type, ResourceLocation dimension, BlockPos centre, UUID owner, long startTick) {
        this.id = id;
        this.type = type;
        this.dimension = dimension;
        this.centre = centre;
        this.owner = owner;
        this.startTick = startTick;
    }

    public String shortId() {
        return LotrLog.id(id);
    }

    public int count(String side) {
        int n = 0;
        for (String s : npcs.values()) if (s.equals(side)) n++;
        return n;
    }

    public @Nullable String venueKind() {
        return venue == null ? null : venue.kind();
    }

    /** What the ended-events list and the marker payload keep. */
    public record Summary(UUID id, ResourceLocation type, ResourceLocation dimension, int x, int z, int radius, @Nullable String venueKind,
                          String outcome, List<ResourceLocation> lead, List<ResourceLocation> opposition, long startTick, long endTick,
                          UUID owner) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("type", type.toString());
            t.putString("dim", dimension.toString());
            t.putInt("x", x);
            t.putInt("z", z);
            t.putInt("r", radius);
            if (venueKind != null) t.putString("venue", venueKind);
            t.putString("outcome", outcome);
            t.putString("lead", String.join(",", lead.stream().map(ResourceLocation::toString).toList()));
            t.putString("opp", String.join(",", opposition.stream().map(ResourceLocation::toString).toList()));
            t.putLong("start", startTick);
            t.putLong("end", endTick);
            t.put("owner", NbtUtils.createUUID(owner));
            return t;
        }

        public static Summary load(CompoundTag t) {
            return new Summary(t.getUUID("id"), ResourceLocation.parse(t.getString("type")), ResourceLocation.parse(t.getString("dim")),
                    t.getInt("x"), t.getInt("z"), t.getInt("r"), t.contains("venue") ? t.getString("venue") : null, t.getString("outcome"),
                    ids(t.getString("lead")), ids(t.getString("opp")), t.getLong("start"), t.getLong("end"), NbtUtils.loadUUID(t.get("owner")));
        }

        private static List<ResourceLocation> ids(String s) {
            List<ResourceLocation> out = new ArrayList<>();
            for (String p : s.split(",")) if (!p.isBlank()) out.add(ResourceLocation.parse(p));
            return out;
        }
    }

    public Summary summary() {
        return new Summary(id, type, dimension, centre.getX(), centre.getZ(), radius, venueKind(), outcome.key(), List.copyOf(leadFactions),
                List.copyOf(oppFactions), startTick, endTick, owner);
    }
}
