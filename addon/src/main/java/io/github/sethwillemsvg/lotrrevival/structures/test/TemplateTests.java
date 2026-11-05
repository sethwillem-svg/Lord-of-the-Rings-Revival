package io.github.sethwillemsvg.lotrrevival.structures.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.BuildStructure;
import io.github.sethwillemsvg.lotrrevival.structures.StructureInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Every template under lotr_revival:build/ is valid and credited (specs/addon/structures.md, Acceptance tests). */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TemplateTests {
    private TemplateTests() {}

    static final Set<String> STRIPPED = Set.of("command_block", "chain_command_block", "repeating_command_block",
            "structure_block", "jigsaw", "barrier", "light", "spawner", "trial_spawner", "vault", "fire", "soul_fire", "tnt",
            "player_head", "player_wall_head", "bedrock", "end_portal", "end_portal_frame", "nether_portal", "end_gateway",
            "beacon", "conduit", "dragon_egg", "diamond_block", "emerald_block", "netherite_block", "gold_block", "iron_block",
            "ancient_debris", "lava");
    static final Set<String> ALLOWED_ENTITIES = Set.of("minecraft:painting", "minecraft:item_frame",
            "minecraft:glow_item_frame", "minecraft:armor_stand");
    static final Set<String> MANAGERS = Set.of("middle-earth:structure_manager", "middle-earth:orc_structure_manager");

    @GameTest(template = "empty")
    public static void allBuildTemplatesValid(GameTestHelper h) {
        TestRun.run(h, "allBuildTemplatesValid", () -> {
            Map<ResourceLocation, CompoundTag> templates = TestRun.buildTemplates(h);
            TestRun.check(!templates.isEmpty(), "no templates under lotr_revival:structure/build/");
            List<String> problems = new ArrayList<>();
            templates.forEach((id, t) -> problems.addAll(check(id, t)));
            TestRun.check(problems.isEmpty(), String.join("; ", problems));
            LotrLog.info("test", "allBuildTemplatesValid: {} templates ok {}", templates.size(), templates.keySet());
        });
    }

    static List<String> check(ResourceLocation id, CompoundTag t) {
        List<String> p = new ArrayList<>();
        ListTag size = t.getList("size", Tag.TAG_INT);
        if (size.size() != 3) p.add(id + ": no size");
        else if (size.getInt(0) > 96 || size.getInt(2) > 96 || size.getInt(1) > 64) p.add(id + ": too big " + size);
        ListTag palette = t.getList("palette", Tag.TAG_COMPOUND);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            String name = palette.getCompound(i).getString("Name");
            names.add(name);
            ResourceLocation rl = ResourceLocation.tryParse(name);
            if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) p.add(id + ": unknown block " + name);
            if (rl != null && rl.getNamespace().equals("minecraft")
                    && (STRIPPED.contains(rl.getPath()) || rl.getPath().endsWith("_ore") || rl.getPath().endsWith("shulker_box")))
                p.add(id + ": stripped block " + name);
        }
        BlockPos manager = null;
        List<BlockPos> nests = new ArrayList<>();
        ListTag blocks = t.getList("blocks", Tag.TAG_COMPOUND);
        int nonAir = 0;
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            String name = names.get(b.getInt("state"));
            if (!name.equals("minecraft:air")) nonAir++;
            ListTag pos = b.getList("pos", Tag.TAG_INT);
            BlockPos bp = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
            if (MANAGERS.contains(name)) manager = bp;
            if (name.equals("middle-earth:structure_nest")) nests.add(bp);
            CompoundTag nbt = b.getCompound("nbt");
            for (String k : List.of("Items", "LootTable", "LootTableSeed", "Book", "RecordItem")) {
                if (nbt.contains(k)) p.add(id + ": " + name + " at " + bp.toShortString() + " still has " + k);
            }
        }
        if (nonAir > 60_000) p.add(id + ": " + nonAir + " blocks");
        ListTag entities = t.getList("entities", Tag.TAG_COMPOUND);
        if (entities.size() > 32) p.add(id + ": " + entities.size() + " entities");
        for (int i = 0; i < entities.size(); i++) {
            String type = entities.getCompound(i).getCompound("nbt").getString("id");
            if (!ALLOWED_ENTITIES.contains(type)) p.add(id + ": entity " + type);
        }
        if (!nests.isEmpty() && manager == null) p.add(id + ": nests without a manager");
        for (BlockPos n : nests) {
            if (manager != null && n.distSqr(manager) > 400) p.add(id + ": nest " + n.toShortString() + " over 20 blocks from the manager");
        }
        return p;
    }

    @GameTest(template = "empty")
    public static void everyBuildHasCredit(GameTestHelper h) {
        TestRun.run(h, "everyBuildHasCredit", () -> {
            Registry<Structure> reg = h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
            Set<ResourceLocation> templates = TestRun.buildTemplates(h).keySet();
            List<String> problems = new ArrayList<>();
            int n = 0;
            for (var e : reg.entrySet()) {
                if (!(e.getValue() instanceof BuildStructure b)) continue;
                n++;
                StructureInfo info = reg.getHolderOrThrow(e.getKey()).getData(StructureInfo.TYPE);
                if (info == null || info.credit().isEmpty()) {
                    problems.add(e.getKey().location() + ": no structure_info credit");
                    continue;
                }
                var c = info.credit().get();
                if (!StructureInfo.ACCEPTED_LICENCES.contains(c.licence())) problems.add(e.getKey().location() + ": licence " + c.licence());
                if (!info.creditOk()) problems.add(e.getKey().location() + ": credit incomplete");
                ResourceLocation file = b.template().withPath(p -> "structure/" + p + ".nbt");
                if (!templates.contains(file)) problems.add(e.getKey().location() + ": no template " + file);
            }
            TestRun.check(n > 0, "no lotr_revival:build structures registered");
            TestRun.check(problems.isEmpty(), String.join("; ", problems));
            LotrLog.info("test", "everyBuildHasCredit: {} builds credited", n);
        });
    }
}
