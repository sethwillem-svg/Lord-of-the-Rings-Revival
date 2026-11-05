package io.github.sethwillemsvg.lotrrevival.structures.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.Regions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sevenstars.middleearth.registries.DynamicRegistriesME;
import net.sevenstars.middleearth.resources.datas.npc_types.NpcType;
import net.sevenstars.middleearth.resources.datas.structure_manager_datas.SpawnNestNodeData;
import net.sevenstars.middleearth.resources.datas.structure_manager_datas.StructureManagerData;
import net.sevenstars.middleearth.resources.datas.structure_manager_datas.StructureSpawnNestPool;

/**
 * Our structure_manager_data pools resolve against Middle-earth's registries (specs/addon/structures.md, Residents):
 * every pool file loads, every NPC type and faction exists and the type belongs to the entry's faction, every region
 * with a faction has a pool, and every nest a template names is in its pool. Logs the NPC types used.
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PoolTests {
    private PoolTests() {}

    /** Datapack folder of Middle-earth's registry {@code middle-earth:structure_manager_data} inside a namespace. */
    static final String POOL_DIR = "middle-earth/structure_manager_data";
    static final ResourceLocation NPC_ENTITY = ResourceLocation.fromNamespaceAndPath("middle-earth", "npc");
    static final ResourceLocation WARG_ENTITY = ResourceLocation.fromNamespaceAndPath("middle-earth", "warg");

    @GameTest(template = "empty")
    public static void ourPoolsResolve(GameTestHelper h) {
        TestRun.run(h, "ourPoolsResolve", () -> {
            var access = h.getLevel().registryAccess();
            Registry<StructureManagerData> pools = access.registryOrThrow(DynamicRegistriesME.STRUCTURE_MANAGER_DATA);
            var npcTypes = access.registryOrThrow(DynamicRegistriesME.NPC_TYPE);
            var factions = access.registryOrThrow(DynamicRegistriesME.FACTION);
            List<String> problems = new ArrayList<>();
            Map<ResourceLocation, Set<ResourceLocation>> nestsByPool = new HashMap<>();
            Map<ResourceLocation, Set<String>> poolFactions = new TreeMap<>();
            Set<String> used = new TreeSet<>();
            int ours = 0;
            for (ResourceLocation id : pools.keySet()) {
                if (!id.getNamespace().equals(LotrRevival.MOD_ID)) continue;
                ours++;
                StructureManagerData data = pools.get(id);
                Set<ResourceLocation> nestIds = new HashSet<>();
                for (SpawnNestNodeData nest : data.getNpcSpawnNest()) {
                    nestIds.add(nest.getId());
                    if (!nest.getId().getNamespace().equals(id.getNamespace())
                            || !nest.getId().getPath().startsWith(id.getPath() + ".")) {
                        problems.add(id + ": nest id " + nest.getId() + " is not <pool>.<nest>");
                    }
                    for (StructureSpawnNestPool p : nest.getNpcSpawnNestPool()) {
                        String where = id + "/" + nest.getId();
                        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(p.getEntityType());
                        if (!type.equals(NPC_ENTITY) && !type.equals(WARG_ENTITY)) {
                            problems.add(where + ": entity type " + type + " is neither the NPC nor the warg");
                        }
                        if (type.equals(NPC_ENTITY) && (p.getNpcIdentifier().isEmpty() || p.getFaction().isEmpty())) {
                            problems.add(where + ": NPC entry without npc_identifier and faction_key");
                        }
                        p.getFaction().ifPresent(f -> {
                            if (!factions.containsKey(f.location())) problems.add(where + ": unknown faction " + f.location());
                            poolFactions.computeIfAbsent(id, k -> new TreeSet<>()).add(f.location().toString());
                        });
                        p.getNpcIdentifier().ifPresent(npc -> {
                            NpcType t = npcTypes.get(npc);
                            if (t == null) {
                                problems.add(where + ": unknown NPC type " + npc);
                                return;
                            }
                            used.add(npc.toString());
                            p.getFaction().ifPresent(f -> {
                                if (!f.location().equals(t.getFactionIdentifier())) {
                                    problems.add(where + ": " + npc + " belongs to " + t.getFactionIdentifier() + ", not "
                                            + f.location());
                                }
                            });
                        });
                    }
                }
                nestsByPool.put(id, nestIds);
                LotrLog.info("test", "pool {}: nests {} factions {}", id, nestIds, poolFactions.getOrDefault(id, Set.of()));
            }
            TestRun.check(ours > 0, "no lotr_revival pools in middle-earth:structure_manager_data");
            LotrLog.info("test", "ourPoolsResolve: {} pools use {} NPC types: {}", ours, used.size(), used);
            // every pool file we ship loaded into the registry (a file that fails to parse would be missing here)
            Map<ResourceLocation, Resource> files = h.getLevel().getServer().getResourceManager()
                    .listResources(POOL_DIR, rl -> rl.getPath().endsWith(".json"));
            int shipped = 0;
            for (ResourceLocation file : files.keySet()) {
                if (!file.getNamespace().equals(LotrRevival.MOD_ID)) continue;
                shipped++;
                String path = file.getPath().substring(POOL_DIR.length() + 1, file.getPath().length() - ".json".length());
                ResourceLocation pool = ResourceLocation.fromNamespaceAndPath(LotrRevival.MOD_ID, path);
                if (!pools.containsKey(pool)) problems.add("pool file " + file + " did not load as " + pool);
            }
            TestRun.check(shipped == ours, "shipped " + shipped + " pool files but " + ours + " lotr_revival pools loaded; "
                    + String.join("; ", problems));
            // every region with a faction has at least one pool of that faction only
            for (Regions.Region r : Regions.ALL) {
                r.factionId().ifPresent(f -> {
                    List<ResourceLocation> mine = poolFactions.entrySet().stream()
                            .filter(e -> e.getValue().equals(Set.of(f))).map(Map.Entry::getKey).toList();
                    if (mine.isEmpty()) problems.add("region " + r.id() + ": no pool of faction " + f);
                    LotrLog.info("test", "region {}: pools {}", r.id(), mine);
                });
            }
            // every nest a template names exists in the pool its nest block names
            TestRun.buildTemplates(h).forEach((tid, t) -> {
                ListTag blocks = t.getList("blocks", Tag.TAG_COMPOUND);
                for (int i = 0; i < blocks.size(); i++) {
                    CompoundTag nbt = blocks.getCompound(i).getCompound("nbt");
                    if (!nbt.contains("structure_nest.NestId")) continue;
                    ResourceLocation pool = ResourceLocation.tryParse(nbt.getString("structure_nest.ManagerId"));
                    ResourceLocation nest = ResourceLocation.tryParse(nbt.getString("structure_nest.NestId"));
                    if (pool == null || nest == null) {
                        problems.add(tid + ": nest with a bad id");
                    } else if (pool.getNamespace().equals(LotrRevival.MOD_ID)
                            && !nestsByPool.getOrDefault(pool, Set.of()).contains(nest)) {
                        problems.add(tid + ": nest " + nest + " not in pool " + pool);
                    } else if (!pools.containsKey(ResourceKey.create(DynamicRegistriesME.STRUCTURE_MANAGER_DATA, pool))) {
                        problems.add(tid + ": pool " + pool + " does not exist");
                    }
                }
            });
            TestRun.check(problems.isEmpty(), String.join("; ", problems));
        });
    }
}
