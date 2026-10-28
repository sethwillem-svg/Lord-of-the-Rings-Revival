package io.github.sethwillemsvg.lotrrevival.progression.bounty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * {@code SavedData lotrrevival_faction_bounties}: per faction and player the kill record times (newest
 * {@link #MAX_RECORDS}) and the last claimed time; queued target notices; the claimed list that fails the bounties
 * of offline hunters on login; and the last known biome of each player. Times are overworld game time.
 */
public final class BountyData extends SavedData {
    public static final String NAME = "lotrrevival_faction_bounties";
    /** A kill record stays live for 48 hours of game time. */
    public static final long RECORD_LIFETIME = 3_456_000L;
    /** Newest kill records kept per faction and player. */
    public static final int MAX_RECORDS = 128;
    /** After a claim the target is immune to that faction's bounties for 12 hours. */
    public static final long IMMUNITY = 864_000L;
    /** Claimed entries are kept for seven in-game days. */
    public static final long CLAIM_KEEP = 7L * 24_000L;

    /** A claimed bounty: {@code target} was killed for {@code faction} at {@code at}. */
    public record Claim(UUID target, ResourceLocation faction, long at) {}

    private final Map<ResourceLocation, Map<UUID, List<Long>>> kills = new HashMap<>();
    private final Map<ResourceLocation, Map<UUID, Long>> lastClaimed = new HashMap<>();
    private final Map<UUID, List<ResourceLocation>> notices = new HashMap<>();
    private final List<Claim> claims = new ArrayList<>();
    private final Map<UUID, ResourceLocation> lastBiome = new HashMap<>();

    public static SavedData.Factory<BountyData> factory() {
        return new SavedData.Factory<>(BountyData::new, BountyData::load, null);
    }

    public static BountyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), NAME);
    }

    // ------------------------------------------------------------------ kill records

    /** Adds {@code count} records at {@code time}, dropping the oldest beyond {@link #MAX_RECORDS}. */
    public void addKills(ResourceLocation faction, UUID player, long time, int count) {
        List<Long> list = kills.computeIfAbsent(faction, f -> new HashMap<>())
                .computeIfAbsent(player, p -> new ArrayList<>());
        for (int i = 0; i < count; i++) {
            list.add(time);
        }
        while (list.size() > MAX_RECORDS) {
            list.remove(0);
        }
        setDirty();
    }

    /** Records that are not older than {@link #RECORD_LIFETIME} at {@code now}. */
    public int liveKills(ResourceLocation faction, UUID player, long now) {
        List<Long> list = kills.getOrDefault(faction, Map.of()).get(player);
        if (list == null) {
            return 0;
        }
        int live = 0;
        for (long time : list) {
            if (now - time <= RECORD_LIFETIME) {
                live++;
            }
        }
        return live;
    }

    /** Players with at least one record for the faction (expired or not). */
    public List<UUID> recordedPlayers(ResourceLocation faction) {
        return new ArrayList<>(kills.getOrDefault(faction, Map.of()).keySet());
    }

    public List<ResourceLocation> recordedFactions() {
        return new ArrayList<>(kills.keySet());
    }

    /** Drops expired records, empty entries and old claimed entries. */
    public void prune(long now) {
        boolean changed = false;
        for (Map<UUID, List<Long>> perPlayer : kills.values()) {
            for (List<Long> list : perPlayer.values()) {
                changed |= list.removeIf(time -> now - time > RECORD_LIFETIME);
            }
            changed |= perPlayer.values().removeIf(List::isEmpty);
        }
        changed |= kills.values().removeIf(Map::isEmpty);
        changed |= claims.removeIf(claim -> now - claim.at() > CLAIM_KEEP);
        if (changed) {
            setDirty();
        }
    }

    /** Clears the kill records and immunity of a player, for one faction or (null) all of them. */
    public void clearKills(UUID player, @Nullable ResourceLocation faction) {
        boolean changed = false;
        for (Map.Entry<ResourceLocation, Map<UUID, List<Long>>> entry : kills.entrySet()) {
            if (faction == null || faction.equals(entry.getKey())) {
                changed |= entry.getValue().remove(player) != null;
            }
        }
        for (Map.Entry<ResourceLocation, Map<UUID, Long>> entry : lastClaimed.entrySet()) {
            if (faction == null || faction.equals(entry.getKey())) {
                changed |= entry.getValue().remove(player) != null;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    // ------------------------------------------------------------------ claims

    public Optional<Long> lastClaimed(ResourceLocation faction, UUID player) {
        return Optional.ofNullable(lastClaimed.getOrDefault(faction, Map.of()).get(player));
    }

    public void setClaimed(ResourceLocation faction, UUID player, long time) {
        lastClaimed.computeIfAbsent(faction, f -> new HashMap<>()).put(player, time);
        setDirty();
    }

    public void addClaim(UUID target, ResourceLocation faction, long time) {
        claims.add(new Claim(target, faction, time));
        setDirty();
    }

    public List<Claim> claims() {
        return List.copyOf(claims);
    }

    // ------------------------------------------------------------------ notices

    public void addNotice(UUID target, ResourceLocation faction) {
        notices.computeIfAbsent(target, t -> new ArrayList<>()).add(faction);
        setDirty();
    }

    public boolean hasNotices(UUID target) {
        return !notices.isEmpty() && notices.containsKey(target);
    }

    /** Removes and returns the queued notices of a target. */
    public List<ResourceLocation> takeNotices(UUID target) {
        List<ResourceLocation> taken = notices.remove(target);
        if (taken == null) {
            return List.of();
        }
        setDirty();
        return taken;
    }

    // ------------------------------------------------------------------ biome

    public void setLastBiome(UUID player, ResourceLocation biome) {
        if (!biome.equals(lastBiome.put(player, biome))) {
            setDirty();
        }
    }

    public Optional<ResourceLocation> lastBiome(UUID player) {
        return Optional.ofNullable(lastBiome.get(player));
    }

    // ------------------------------------------------------------------ persistence

    private static BountyData load(CompoundTag tag, HolderLookup.Provider registries) {
        BountyData data = new BountyData();
        ListTag killList = tag.getList("kills", Tag.TAG_COMPOUND);
        for (int i = 0; i < killList.size(); i++) {
            CompoundTag entry = killList.getCompound(i);
            ResourceLocation faction = ResourceLocation.tryParse(entry.getString("faction"));
            if (faction == null || !entry.hasUUID("player")) {
                continue;
            }
            List<Long> times = new ArrayList<>();
            for (long time : entry.getLongArray("times")) {
                times.add(time);
            }
            data.kills.computeIfAbsent(faction, f -> new HashMap<>()).put(entry.getUUID("player"), times);
        }
        ListTag claimedList = tag.getList("last_claimed", Tag.TAG_COMPOUND);
        for (int i = 0; i < claimedList.size(); i++) {
            CompoundTag entry = claimedList.getCompound(i);
            ResourceLocation faction = ResourceLocation.tryParse(entry.getString("faction"));
            if (faction != null && entry.hasUUID("player")) {
                data.lastClaimed
                        .computeIfAbsent(faction, f -> new HashMap<>())
                        .put(entry.getUUID("player"), entry.getLong("at"));
            }
        }
        ListTag noticeList = tag.getList("notices", Tag.TAG_COMPOUND);
        for (int i = 0; i < noticeList.size(); i++) {
            CompoundTag entry = noticeList.getCompound(i);
            if (!entry.hasUUID("target")) {
                continue;
            }
            List<ResourceLocation> factions = new ArrayList<>();
            ListTag names = entry.getList("factions", Tag.TAG_STRING);
            for (int n = 0; n < names.size(); n++) {
                ResourceLocation faction = ResourceLocation.tryParse(names.getString(n));
                if (faction != null) {
                    factions.add(faction);
                }
            }
            if (!factions.isEmpty()) {
                data.notices.put(entry.getUUID("target"), factions);
            }
        }
        ListTag claimList = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < claimList.size(); i++) {
            CompoundTag entry = claimList.getCompound(i);
            ResourceLocation faction = ResourceLocation.tryParse(entry.getString("faction"));
            if (faction != null && entry.hasUUID("target")) {
                data.claims.add(new Claim(entry.getUUID("target"), faction, entry.getLong("at")));
            }
        }
        ListTag biomeList = tag.getList("biomes", Tag.TAG_COMPOUND);
        for (int i = 0; i < biomeList.size(); i++) {
            CompoundTag entry = biomeList.getCompound(i);
            ResourceLocation biome = ResourceLocation.tryParse(entry.getString("biome"));
            if (biome != null && entry.hasUUID("player")) {
                data.lastBiome.put(entry.getUUID("player"), biome);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag killList = new ListTag();
        kills.forEach((faction, perPlayer) -> perPlayer.forEach((player, times) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("faction", faction.toString());
            entry.putUUID("player", player);
            entry.putLongArray("times", times.stream().mapToLong(Long::longValue).toArray());
            killList.add(entry);
        }));
        tag.put("kills", killList);

        ListTag claimedList = new ListTag();
        lastClaimed.forEach((faction, perPlayer) -> perPlayer.forEach((player, at) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("faction", faction.toString());
            entry.putUUID("player", player);
            entry.putLong("at", at);
            claimedList.add(entry);
        }));
        tag.put("last_claimed", claimedList);

        ListTag noticeList = new ListTag();
        notices.forEach((target, factions) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("target", target);
            ListTag names = new ListTag();
            factions.forEach(faction -> names.add(StringTag.valueOf(faction.toString())));
            entry.put("factions", names);
            noticeList.add(entry);
        });
        tag.put("notices", noticeList);

        ListTag claimList = new ListTag();
        for (Claim claim : claims) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("target", claim.target());
            entry.putString("faction", claim.faction().toString());
            entry.putLong("at", claim.at());
            claimList.add(entry);
        }
        tag.put("claims", claimList);

        ListTag biomeList = new ListTag();
        lastBiome.forEach((player, biome) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.putString("biome", biome.toString());
            biomeList.add(entry);
        });
        tag.put("biomes", biomeList);
        return tag;
    }
}
