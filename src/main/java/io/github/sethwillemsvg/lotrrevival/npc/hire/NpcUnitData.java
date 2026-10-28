package io.github.sethwillemsvg.lotrrevival.npc.hire;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

/**
 * Reload listener for {@code data/<ns>/lotrrevival/npc_units/<entity type path>.json}. The file name is the unit
 * trader's entity type path; the content maps role names to {@link UnitList}. Entries naming an unknown entity type
 * are skipped with one warning. Server side only.
 */
public final class NpcUnitData extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "lotrrevival/npc_units";
    public static final Codec<Map<NpcRole, UnitList>> TABLE_CODEC = Codec.unboundedMap(NpcRole.CODEC, UnitList.CODEC);

    private static volatile Map<ResourceLocation, Map<NpcRole, UnitList>> tables = Map.of();

    public NpcUnitData() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Map<NpcRole, UnitList>> loaded = new HashMap<>();
        files.forEach((id, json) -> TABLE_CODEC
                .parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LOTRRevival.LOGGER.warn("Skipping NPC unit file {}: {}", id, error))
                .ifPresent(table -> {
                    if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                        LOTRRevival.LOGGER.warn("Skipping NPC unit file {}: unknown trader entity type", id);
                        return;
                    }
                    Map<NpcRole, UnitList> cleaned = new HashMap<>();
                    table.forEach((role, list) -> cleaned.put(role, validate(id, list)));
                    loaded.put(id, Map.copyOf(cleaned));
                }));
        tables = Map.copyOf(loaded);
    }

    private static UnitList validate(ResourceLocation file, UnitList list) {
        List<UnitEntry> kept = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (UnitEntry entry : list.units()) {
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entry.entity())) {
                LOTRRevival.LOGGER.warn("Skipping unit {} in {}: unknown entity type {}", entry.id(), file, entry.entity());
            } else if (!ids.add(entry.id())) {
                LOTRRevival.LOGGER.warn("Skipping unit {} in {}: duplicate id", entry.id(), file);
            } else {
                kept.add(entry);
            }
        }
        return new UnitList(list.baseAlignment(), List.copyOf(kept));
    }

    /** The unit list of a trader type and role, if the datapack defines one. */
    public static Optional<UnitList> list(EntityType<?> trader, NpcRole role) {
        return Optional.ofNullable(tables.get(EntityType.getKey(trader))).map(table -> table.get(role));
    }
}
