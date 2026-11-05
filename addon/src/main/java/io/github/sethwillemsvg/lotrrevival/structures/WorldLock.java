package io.github.sethwillemsvg.lotrrevival.structures;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Spacing, separation and salt per structure set, written on a world's first start and kept after (specs/addon/structures.md,
 * World lock). Stored on the overworld as {@code lotr_revival_structures}. Placement reads {@link #effective}.
 */
public final class WorldLock extends SavedData {
    public static final String NAME = "lotr_revival_structures";
    static final String SYS = StructureStats.SYS;

    public record Entry(int spacing, int separation, int salt) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("spacing", spacing);
            t.putInt("separation", separation);
            t.putInt("salt", salt);
            return t;
        }

        static Entry load(CompoundTag t) {
            return new Entry(t.getInt("spacing"), t.getInt("separation"), t.getInt("salt"));
        }
    }

    /** What the server uses now (set id -> values). Read on worldgen threads. */
    private static volatile Map<String, Entry> effective = Map.of();
    /** For log lines: which sets were locked before this start. */
    private static volatile Map<String, Boolean> lockedBefore = Map.of();

    final Map<String, Entry> entries = new TreeMap<>();
    long relockRequestedAt = -1;

    public WorldLock() {}

    public static SavedData.Factory<WorldLock> factory() {
        return new SavedData.Factory<>(WorldLock::new, WorldLock::load);
    }

    public static WorldLock get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(factory(), NAME);
    }

    public static WorldLock load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldLock w = new WorldLock();
        CompoundTag sets = tag.getCompound("sets");
        for (String k : sets.getAllKeys()) w.entries.put(k, Entry.load(sets.getCompound(k)));
        return w;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag sets = new CompoundTag();
        entries.forEach((k, v) -> sets.put(k, v.save()));
        tag.put("sets", sets);
        return tag;
    }

    /**
     * Merges config into the stored lock: sets not stored yet take the config value and are stored; stored sets keep
     * their values, adding a warning when the config differs. With {@code relock} the config replaces everything.
     */
    public static Map<String, Entry> reconcile(Map<String, Entry> stored, Map<String, Entry> config, boolean relock,
                                               List<String> warnings) {
        Map<String, Entry> out = new TreeMap<>(relock ? Map.of() : stored);
        config.forEach((id, want) -> {
            Entry have = out.get(id);
            if (have == null) {
                out.put(id, want);
            } else if (!have.equals(want)) {
                if (have.spacing() != want.spacing())
                    warnings.add("world lock keeps " + id + " spacing " + have.spacing() + " (config says " + want.spacing() + ")");
                if (have.separation() != want.separation())
                    warnings.add("world lock keeps " + id + " separation " + have.separation() + " (config says " + want.separation() + ")");
                if (have.salt() != want.salt())
                    warnings.add("world lock keeps " + id + " salt " + have.salt() + " (data says " + want.salt() + ")");
            }
        });
        return out;
    }

    /** The config's view of every lotr_revival set in the registry. */
    public static Map<String, Entry> fromConfig(ServerLevel level) {
        Map<String, Entry> out = new LinkedHashMap<>();
        level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).entrySet().forEach(e -> {
            StructureSet set = e.getValue();
            if (set.placement() instanceof ConfiguredSpreadPlacement p) {
                out.put(e.getKey().location().toString(), p.configValues());
            }
        });
        return out;
    }

    /** Overworld load: lock new sets, keep old ones, publish the effective values. */
    public void apply(ServerLevel overworld, boolean relock) {
        Map<String, Entry> config = fromConfig(overworld);
        List<String> warnings = new ArrayList<>();
        Map<String, Boolean> before = new TreeMap<>();
        config.keySet().forEach(k -> before.put(k, entries.containsKey(k) && !relock));
        Map<String, Entry> merged = reconcile(entries, config, relock, warnings);
        warnings.forEach(w -> LotrLog.warn(SYS, w));
        if (!merged.equals(entries)) {
            entries.clear();
            entries.putAll(merged);
            setDirty();
        }
        effective = Map.copyOf(entries);
        lockedBefore = Map.copyOf(before);
        LotrLog.info(SYS, "world lock{}: {}", relock ? " (relocked)" : "", describe());
    }

    public static String describe() {
        StringBuilder b = new StringBuilder();
        effective.forEach((k, v) -> b.append(b.isEmpty() ? "" : ", ").append(k).append(' ').append(v.spacing()).append('/')
                .append(v.separation()).append(Boolean.TRUE.equals(lockedBefore.get(k)) ? " locked" : " new"));
        return b.isEmpty() ? "no sets" : b.toString();
    }

    public static Entry effective(String setId, Entry fallback) {
        return effective.getOrDefault(setId, fallback);
    }

    public static void clear() {
        effective = Map.of();
        lockedBefore = Map.of();
    }

    /** {@code /lotr_revival structures relock}: true when this call applied it (the second call within 30 s). */
    public boolean requestRelock(long gameTime) {
        if (relockRequestedAt >= 0 && gameTime - relockRequestedAt <= 600) {
            relockRequestedAt = -1;
            return true;
        }
        relockRequestedAt = gameTime;
        return false;
    }

    public Map<String, Entry> entries() {
        return Map.copyOf(entries);
    }
}
