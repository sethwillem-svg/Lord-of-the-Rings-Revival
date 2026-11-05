package io.github.sethwillemsvg.lotrrevival.structures.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sevenstars.middleearth.registries.DynamicRegistriesME;
import net.sevenstars.middleearth.resources.datas.structure_manager_datas.SpawnNestNodeData;
import net.sevenstars.middleearth.resources.datas.structure_manager_datas.StructureManagerData;
import net.sevenstars.middleearth.resources.datas.structure_manager_datas.StructureSpawnNestPool;

/** Our structure_manager_data pools resolve against Middle-earth's registries (specs/addon/structures.md, Residents). */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PoolTests {
    private PoolTests() {}

    @GameTest(template = "empty")
    public static void ourPoolsResolve(GameTestHelper h) {
        TestRun.run(h, "ourPoolsResolve", () -> {
            var access = h.getLevel().registryAccess();
            Registry<StructureManagerData> pools = access.registryOrThrow(DynamicRegistriesME.STRUCTURE_MANAGER_DATA);
            var npcTypes = access.registryOrThrow(DynamicRegistriesME.NPC_TYPE);
            var factions = access.registryOrThrow(DynamicRegistriesME.FACTION);
            List<String> problems = new ArrayList<>();
            Map<ResourceLocation, Set<ResourceLocation>> nestsByPool = new HashMap<>();
            int ours = 0;
            for (ResourceLocation id : pools.keySet()) {
                if (!id.getNamespace().equals(LotrRevival.MOD_ID)) continue;
                ours++;
                StructureManagerData data = pools.get(id);
                Set<ResourceLocation> nestIds = new HashSet<>();
                for (SpawnNestNodeData nest : data.getNpcSpawnNest()) {
                    nestIds.add(nest.getId());
                    for (StructureSpawnNestPool p : nest.getNpcSpawnNestPool()) {
                        p.getNpcIdentifier().ifPresent(npc -> {
                            if (!npcTypes.containsKey(npc)) problems.add(id + "/" + nest.getId() + ": unknown NPC type " + npc);
                        });
                        p.getFaction().ifPresent(f -> {
                            if (!factions.containsKey(f.location())) problems.add(id + "/" + nest.getId() + ": unknown faction " + f.location());
                        });
                    }
                }
                nestsByPool.put(id, nestIds);
                LotrLog.info("test", "pool {}: nests {}", id, nestIds);
            }
            TestRun.check(ours > 0, "no lotr_revival pools in middle-earth:structure_manager_data");
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
