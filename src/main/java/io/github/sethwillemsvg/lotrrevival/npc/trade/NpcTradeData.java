package io.github.sethwillemsvg.lotrrevival.npc.trade;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

/**
 * Reload listener for {@code data/<ns>/lotrrevival/npc_trades/<type>.json}. The file name is the entity type path;
 * the content maps role names to {@link TradePool}. Entries naming an unknown item are skipped with one warning.
 */
public final class NpcTradeData extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "lotrrevival/npc_trades";

    private static volatile Map<ResourceLocation, Map<NpcRole, TradePool>> tables = Map.of();

    public NpcTradeData() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, Map<NpcRole, TradePool>> loaded = new HashMap<>();
        files.forEach((id, json) -> TradePool.TABLE_CODEC
                .parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LOTRRevival.LOGGER.warn("Skipping NPC trade file {}: {}", id, error))
                .ifPresent(table -> {
                    Map<NpcRole, TradePool> clean = new HashMap<>();
                    table.forEach((role, pool) -> clean.put(
                            role,
                            new TradePool(known(id, role, pool.playerBuys()), known(id, role, pool.playerSells()))));
                    loaded.put(id, Map.copyOf(clean));
                }));
        tables = Map.copyOf(loaded);
    }

    private static List<TradeEntry> known(ResourceLocation file, NpcRole role, List<TradeEntry> entries) {
        return entries.stream()
                .filter(entry -> {
                    boolean ok = entry.item().map(BuiltInRegistries.ITEM::containsKey).orElse(true);
                    if (!ok) {
                        LOTRRevival.LOGGER.warn(
                                "NPC trade file {} ({}): unknown item {}, skipped",
                                file,
                                role,
                                entry.item().get());
                    }
                    return ok;
                })
                .toList();
    }

    /** The pool of a role for an entity type, if the datapack defines one. */
    public static Optional<TradePool> pool(EntityType<?> type, NpcRole role) {
        ResourceLocation key = EntityType.getKey(type);
        return Optional.ofNullable(tables.get(key)).map(table -> table.get(role));
    }
}
