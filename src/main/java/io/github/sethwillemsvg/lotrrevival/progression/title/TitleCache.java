package io.github.sethwillemsvg.lotrrevival.progression.title;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * {@code lotrrevival_title_cache}: player UUID to the selected title, colour and feminine flag. Updated on every
 * change so lists that show offline players (the later fellowship member list) can still show their title.
 */
public final class TitleCache extends SavedData {
    public static final String NAME = "lotrrevival_title_cache";

    /** What a list needs to render a player's title. */
    public record Entry(ResourceLocation title, ChatFormatting color, boolean feminine) {}

    private static final SavedData.Factory<TitleCache> FACTORY = new SavedData.Factory<>(TitleCache::new, TitleCache::load);

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static TitleCache get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** The cached title of a player, empty when none is selected. */
    public Optional<Entry> get(UUID player) {
        return Optional.ofNullable(entries.get(player));
    }

    /** Records the player's choice; an empty title removes the entry. */
    public void put(UUID player, Optional<ResourceLocation> title, ChatFormatting color, boolean feminine) {
        Entry entry = title.map(id -> new Entry(id, color, feminine)).orElse(null);
        boolean changed = entry == null ? entries.remove(player) != null : !entry.equals(entries.put(player, entry));
        if (changed) {
            setDirty();
        }
    }

    private static TitleCache load(CompoundTag tag, HolderLookup.Provider registries) {
        TitleCache cache = new TitleCache();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            if (!row.hasUUID("uuid")) {
                continue;
            }
            ResourceLocation title = ResourceLocation.tryParse(row.getString("title"));
            ChatFormatting color = TitleData.parseColor(row.getString("color"));
            if (title != null) {
                cache.entries.put(
                        row.getUUID("uuid"),
                        new Entry(title, color == null ? ChatFormatting.WHITE : color, row.getBoolean("feminine")));
            }
        }
        return cache;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        entries.forEach((uuid, entry) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("uuid", uuid);
            row.putString("title", entry.title().toString());
            row.putString("color", entry.color().getName());
            row.putBoolean("feminine", entry.feminine());
            list.add(row);
        });
        tag.put("players", list);
        return tag;
    }
}
