package io.github.sethwillemsvg.lotrrevival.structures.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.StructureStats;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Dev mode {@code -PblockDump=<file>} (system property {@code lotr_revival.blockDump}): writes every registered block with
 * its properties, values and default state for tools/structure-import (same format as tests/fixtures/blocks.json), then
 * stops the server.
 */
public final class BlockDump {
    public static final String PROPERTY = "lotr_revival.blockDump";
    static final String SYS = StructureStats.SYS;

    private BlockDump() {}

    public static JsonObject toJson() {
        JsonObject blocks = new JsonObject();
        BuiltInRegistries.BLOCK.keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(id -> {
            Block block = BuiltInRegistries.BLOCK.get(id);
            BlockState def = block.defaultBlockState();
            JsonObject props = new JsonObject();
            JsonObject defaults = new JsonObject();
            for (Property<?> p : block.getStateDefinition().getProperties()) {
                JsonArray values = new JsonArray();
                p.getPossibleValues().forEach(v -> values.add(name(p, v)));
                props.add(p.getName(), values);
                defaults.addProperty(p.getName(), name(p, def.getValue(p)));
            }
            JsonObject entry = new JsonObject();
            entry.add("properties", props);
            entry.add("default", defaults);
            blocks.add(id.toString(), entry);
        });
        JsonObject root = new JsonObject();
        root.addProperty("source", "structures/dev/BlockDump.java");
        root.addProperty("data_version", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        root.add("blocks", blocks);
        return root;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String name(Property p, Object v) {
        return p.getName((Comparable) v);
    }

    /** Runs when the property is set; returns true if it ran (the caller stops the server). */
    public static boolean runIfAsked(MinecraftServer server) {
        String out = System.getProperty(PROPERTY);
        if (out == null || out.isBlank()) return false;
        try {
            JsonObject json = toJson();
            Path path = Path.of(out).toAbsolutePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n", StandardCharsets.UTF_8);
            LotrLog.info(SYS, "block dump: {} blocks written to {}", json.getAsJsonObject("blocks").size(), path);
        } catch (Exception e) {
            LotrLog.error(SYS, "block dump failed", e);
        }
        return true;
    }
}
