package io.github.sethwillemsvg.lotrrevival.npc.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.spawn.NpcSpawnData;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

/** {@code npc_spawns/<biome>.json}: the G2 wild spawn tables. */
public final class NpcSpawnTableProvider implements DataProvider {
    private final PackOutput.PathProvider paths;

    public NpcSpawnTableProvider(PackOutput output) {
        this.paths = output.createPathProvider(PackOutput.Target.DATA_PACK, NpcSpawnData.DIRECTORY);
    }

    private record Row(String type, int weight, int groupMin, int groupMax, int chance) {}

    private static Map<ResourceLocation, List<Row>> tables() {
        return Map.of(
                LOTRRevival.id("shire"),
                List.of(new Row("hobbit", 40, 1, 4, 1), new Row("hobbit_bounder", 1, 1, 3, 1)),
                // The Bree-land biome is not in the world yet; the table is inert until it is.
                LOTRRevival.id("bree_land"),
                List.of(
                        new Row("bree_man", 8, 1, 3, 500),
                        new Row("bree_hobbit", 4, 1, 3, 500),
                        new Row("bree_guard", 2, 1, 2, 500)));
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return CompletableFuture.allOf(tables().entrySet().stream()
                .map(entry -> {
                    JsonArray entries = new JsonArray();
                    for (Row row : entry.getValue()) {
                        JsonObject json = new JsonObject();
                        json.addProperty("type", LOTRRevival.id(row.type()).toString());
                        json.addProperty("weight", row.weight());
                        json.addProperty("group_min", row.groupMin());
                        json.addProperty("group_max", row.groupMax());
                        if (row.chance() > 1) {
                            json.addProperty("chance", row.chance());
                        }
                        entries.add(json);
                    }
                    JsonObject file = new JsonObject();
                    file.add(NpcSpawnData.ENTRIES_KEY, entries);
                    return DataProvider.saveStable(cache, file, paths.json(entry.getKey()));
                })
                .toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "NPC spawn tables";
    }
}
