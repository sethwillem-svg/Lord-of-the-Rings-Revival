package io.github.sethwillemsvg.lotrrevival.npc.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

/**
 * Reload listener for {@code data/<ns>/lotrrevival/npc_roles/<type>.json}. The file name is the entity type path
 * (for example {@code hobbit.json} for {@code lotrrevival:hobbit}); the content maps role names to
 * {@link NpcRoleStats}. Server side only.
 */
public final class NpcRoleData extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "lotrrevival/npc_roles";

    private static volatile Map<ResourceLocation, Map<NpcRole, NpcRoleStats>> tables = Map.of();

    public NpcRoleData() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Map<NpcRole, NpcRoleStats>> loaded = new HashMap<>();
        files.forEach((id, json) -> NpcRoleStats.TABLE_CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LOTRRevival.LOGGER.warn("Skipping NPC role file {}: {}", id, error))
                .ifPresent(table -> loaded.put(id, Map.copyOf(table))));
        tables = Map.copyOf(loaded);
    }

    /** Stats of a role for an entity type, if the datapack defines them. */
    public static Optional<NpcRoleStats> stats(EntityType<?> type, NpcRole role) {
        ResourceLocation key = EntityType.getKey(type);
        return Optional.ofNullable(tables.get(key)).map(table -> table.get(role));
    }

    /** Alignment kill value of a role for an entity type, if the datapack defines it. */
    public static Optional<Float> killValue(EntityType<?> type, NpcRole role) {
        return stats(type, role).map(NpcRoleStats::killValue);
    }
}
