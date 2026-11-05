package io.github.sethwillemsvg.lotrrevival.events;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code lotr_revival_world_events} (overworld data storage): the last 10 ended events (kept one in-game day), per-player event
 * cooldowns (game tick until which no event is staged for them) and venue cooldowns (by venue key).
 */
public final class WorldEventSavedData extends SavedData {
    public static final String NAME = "lotr_revival_world_events";
    private static final Factory<WorldEventSavedData> FACTORY = new Factory<>(WorldEventSavedData::new, WorldEventSavedData::load, null);

    public final List<WorldEvent.Summary> ended = new ArrayList<>();
    public final Map<UUID, Long> playerCooldownUntil = new HashMap<>();
    public final Map<String, Long> venueCooldownUntil = new HashMap<>();

    public static WorldEventSavedData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static WorldEventSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        WorldEventSavedData d = new WorldEventSavedData();
        try {
            for (Tag t : tag.getList("ended", Tag.TAG_COMPOUND)) d.ended.add(WorldEvent.Summary.load((CompoundTag) t));
            CompoundTag pc = tag.getCompound("player_cd");
            for (String k : pc.getAllKeys()) d.playerCooldownUntil.put(UUID.fromString(k), pc.getLong(k));
            CompoundTag vc = tag.getCompound("venue_cd");
            for (String k : vc.getAllKeys()) d.venueCooldownUntil.put(k, vc.getLong(k));
        } catch (RuntimeException e) {
            LotrLog.error("events", "world event SavedData load failed part-way", e);
        }
        LotrLog.info("events", "world event data loaded: ended={} player_cooldowns={} venue_cooldowns={}", d.ended.size(),
                d.playerCooldownUntil.size(), d.venueCooldownUntil.size());
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag l = new ListTag();
        for (WorldEvent.Summary s : ended) l.add(s.save());
        tag.put("ended", l);
        CompoundTag pc = new CompoundTag();
        playerCooldownUntil.forEach((k, v) -> pc.putLong(k.toString(), v));
        tag.put("player_cd", pc);
        CompoundTag vc = new CompoundTag();
        venueCooldownUntil.forEach(vc::putLong);
        tag.put("venue_cd", vc);
        return tag;
    }

    public void addEnded(WorldEvent.Summary s, long now) {
        ended.add(0, s);
        ended.removeIf(e -> now - e.endTick() > 24000L);
        while (ended.size() > 10) ended.removeLast();
        setDirty();
    }
}
