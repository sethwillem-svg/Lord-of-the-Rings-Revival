package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * {@code lotr_revival_nemeses} in the overworld's data storage: records, politics timer, per-player ambush cooldowns and
 * online-time counters, sergeants per owner. Written only when dirty.
 */
public final class NemesisSavedData extends SavedData {
    public static final String NAME = "lotr_revival_nemeses";
    private static final Factory<NemesisSavedData> FACTORY = new Factory<>(NemesisSavedData::new, NemesisSavedData::load, null);

    public final Map<UUID, NemesisRecord> records = new LinkedHashMap<>();
    /** Server tick of the next politics round. */
    public long politicsNextTick = -1;
    /** Player -> total online ticks counted by the ambush check. */
    public final Map<UUID, Long> onlineTicks = new HashMap<>();
    /** Player -> online-tick count before which no ambush may start. */
    public final Map<UUID, Long> ambushCooldownUntil = new HashMap<>();
    /** Owner -> sergeant unit UUIDs. */
    public final Map<UUID, Set<UUID>> sergeants = new HashMap<>();

    private final Map<UUID, UUID> byBody = new HashMap<>();

    public NemesisSavedData() {}

    public static NemesisSavedData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static NemesisSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        NemesisSavedData d = new NemesisSavedData();
        try {
            for (Tag t : tag.getList("records", Tag.TAG_COMPOUND)) {
                NemesisRecord r = NemesisRecord.load((CompoundTag) t, provider, NemesisTraits::isKnownOrUnloaded);
                d.records.put(r.id, r);
                if (r.body != null) d.byBody.put(r.body, r.id);
            }
            d.politicsNextTick = tag.getLong("politics_next");
            readLongMap(tag.getCompound("online"), d.onlineTicks);
            readLongMap(tag.getCompound("ambush_cd"), d.ambushCooldownUntil);
            CompoundTag sg = tag.getCompound("sergeants");
            for (String k : sg.getAllKeys()) {
                Set<UUID> units = new HashSet<>();
                for (Tag u : sg.getList(k, Tag.TAG_INT_ARRAY)) units.add(net.minecraft.nbt.NbtUtils.loadUUID(u));
                d.sergeants.put(UUID.fromString(k), units);
            }
        } catch (RuntimeException e) {
            LotrLog.error("nemesis", "SavedData load failed part-way; keeping what loaded", e);
        }
        d.logLoaded();
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (NemesisRecord r : records.values()) list.add(r.save(provider));
        tag.put("records", list);
        tag.putLong("politics_next", politicsNextTick);
        tag.put("online", writeLongMap(onlineTicks));
        tag.put("ambush_cd", writeLongMap(ambushCooldownUntil));
        CompoundTag sg = new CompoundTag();
        sergeants.forEach((owner, units) -> {
            ListTag l = new ListTag();
            for (UUID u : units) l.add(net.minecraft.nbt.NbtUtils.createUUID(u));
            sg.put(owner.toString(), l);
        });
        tag.put("sergeants", sg);
        LotrLog.dbg("nemesis", "saved data written: records={}", records.size());
        return tag;
    }

    public void logLoaded() {
        Map<String, Integer> counts = new TreeMap<>();
        for (NemesisRecord r : records.values()) counts.merge(r.faction + "/" + r.status, 1, Integer::sum);
        LotrLog.info("nemesis", "records loaded: total={} by faction/status={}", records.size(), counts);
    }

    public void put(NemesisRecord r) {
        records.put(r.id, r);
        setDirty();
    }

    public @Nullable NemesisRecord find(UUID id) {
        return records.get(id);
    }

    public @Nullable NemesisRecord byBody(UUID body) {
        UUID id = byBody.get(body);
        return id == null ? null : records.get(id);
    }

    public void bindBody(NemesisRecord r, @Nullable UUID body) {
        if (r.body != null) byBody.remove(r.body);
        r.body = body;
        if (body != null) byBody.put(body, r.id);
        setDirty();
    }

    public List<NemesisRecord> living(ResourceLocation faction) {
        List<NemesisRecord> out = new ArrayList<>();
        for (NemesisRecord r : records.values()) if (r.alive() && r.faction.equals(faction)) out.add(r);
        return out;
    }

    public long manifestCount() {
        return records.values().stream().filter(r -> r.status == NemesisRecord.Status.MANIFEST).count();
    }

    /** Game tests: forget every record of this faction (each test starts clean). */
    public void clearFactionForTest(ResourceLocation faction) {
        records.values().removeIf(r -> r.faction.equals(faction));
        byBody.values().removeIf(id -> !records.containsKey(id));
        setDirty();
    }

    private static CompoundTag writeLongMap(Map<UUID, Long> map) {
        CompoundTag t = new CompoundTag();
        map.forEach((k, v) -> t.putLong(k.toString(), v));
        return t;
    }

    private static void readLongMap(CompoundTag t, Map<UUID, Long> into) {
        for (String k : t.getAllKeys()) {
            try {
                into.put(UUID.fromString(k), t.getLong(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
