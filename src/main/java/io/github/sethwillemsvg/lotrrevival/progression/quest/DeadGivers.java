package io.github.sethwillemsvg.lotrrevival.progression.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * {@code SavedData lotrrevival_dead_givers}: givers that died, with the game time of death, kept for
 * {@link #KEEP_TICKS} so offline holders fail their quest on login.
 */
public final class DeadGivers extends SavedData {
    public static final String NAME = "lotrrevival_dead_givers";
    /** Seven in-game days. */
    public static final long KEEP_TICKS = 7L * 24_000L;

    private final Map<UUID, Long> deaths = new HashMap<>();

    public static SavedData.Factory<DeadGivers> factory() {
        return new SavedData.Factory<>(DeadGivers::new, DeadGivers::load, null);
    }

    public static DeadGivers get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    private static DeadGivers load(CompoundTag tag, HolderLookup.Provider registries) {
        DeadGivers data = new DeadGivers();
        ListTag list = tag.getList("deaths", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("giver")) {
                data.deaths.put(entry.getUUID("giver"), entry.getLong("at"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        deaths.forEach((giver, at) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("giver", giver);
            entry.putLong("at", at);
            list.add(entry);
        });
        tag.put("deaths", list);
        return tag;
    }

    public void add(UUID giver, long gameTime) {
        deaths.put(giver, gameTime);
        setDirty();
    }

    public boolean contains(UUID giver) {
        return deaths.containsKey(giver);
    }

    /** Drops entries older than {@link #KEEP_TICKS}. */
    public void prune(long gameTime) {
        if (deaths.values().removeIf(at -> gameTime - at > KEEP_TICKS)) {
            setDirty();
        }
    }

    public int size() {
        return deaths.size();
    }
}
