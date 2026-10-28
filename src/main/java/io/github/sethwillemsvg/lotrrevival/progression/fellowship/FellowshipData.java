package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * {@code lotrrevival_fellowships}: all fellowships by id, plus the last known name of every player that ever joined
 * or was invited, so offline members and invitees can be listed and found by name.
 */
public final class FellowshipData extends SavedData {
    public static final String NAME = "lotrrevival_fellowships";

    private static final SavedData.Factory<FellowshipData> FACTORY =
            new SavedData.Factory<>(FellowshipData::new, FellowshipData::load);

    private final Map<UUID, Fellowship> fellowships = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();

    public static FellowshipData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Optional<Fellowship> find(UUID id) {
        return Optional.ofNullable(fellowships.get(id));
    }

    public List<Fellowship> all() {
        return new ArrayList<>(fellowships.values());
    }

    /** Fellowships the player owns, administers or belongs to. */
    public List<Fellowship> of(UUID player) {
        List<Fellowship> result = new ArrayList<>();
        for (Fellowship fellowship : fellowships.values()) {
            if (fellowship.isMember(player)) {
                result.add(fellowship);
            }
        }
        return result;
    }

    /** Fellowships with a pending invite for the player. */
    public List<Fellowship> invitesFor(UUID player) {
        List<Fellowship> result = new ArrayList<>();
        for (Fellowship fellowship : fellowships.values()) {
            if (fellowship.invites().containsKey(player)) {
                result.add(fellowship);
            }
        }
        return result;
    }

    /** How many fellowships the player owns. */
    public int ownedCount(UUID player) {
        int count = 0;
        for (Fellowship fellowship : fellowships.values()) {
            if (fellowship.owner().equals(player)) {
                count++;
            }
        }
        return count;
    }

    void add(Fellowship fellowship) {
        fellowships.put(fellowship.id(), fellowship);
        setDirty();
    }

    void remove(UUID id) {
        if (fellowships.remove(id) != null) {
            setDirty();
        }
    }

    public String nameOf(UUID player) {
        return names.getOrDefault(player, player.toString().substring(0, 8));
    }

    /** Records the player's current name; marks dirty only when it changed. */
    public void rememberName(UUID player, String name) {
        if (!name.equals(names.put(player, name))) {
            setDirty();
        }
    }

    public Optional<UUID> findByName(String name) {
        for (Map.Entry<UUID, String> entry : names.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public static FellowshipData load(CompoundTag tag, HolderLookup.Provider registries) {
        FellowshipData data = new FellowshipData();
        ListTag list = tag.getList("fellowships", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (row.hasUUID("id") && row.hasUUID("owner")) {
                Fellowship fellowship = Fellowship.load(row, registries);
                data.fellowships.put(fellowship.id(), fellowship);
            }
        }
        ListTag nameList = tag.getList("names", Tag.TAG_COMPOUND);
        for (int i = 0; i < nameList.size(); i++) {
            CompoundTag row = nameList.getCompound(i);
            if (row.hasUUID("uuid")) {
                data.names.put(row.getUUID("uuid"), row.getString("name"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Fellowship fellowship : fellowships.values()) {
            list.add(fellowship.save(registries));
        }
        tag.put("fellowships", list);
        ListTag nameList = new ListTag();
        names.forEach((uuid, name) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("uuid", uuid);
            row.putString("name", name);
            nameList.add(row);
        });
        tag.put("names", nameList);
        return tag;
    }
}
