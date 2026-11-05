package io.github.sethwillemsvg.lotrrevival.structures.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;

/**
 * The placeholder watchtower, placed as the test structure, gets Gondor residents from Middle-earth's own manager and
 * nests (specs/addon/structures.md, Residents).
 *
 * <p>The manager and nests are found by scanning the test area rather than trusted to sit at the template positions:
 * GameTestHelper's relative positions are measured from the test's structure block, which may not be the template's own
 * origin (CI run 212 found no block entity at the template position 1,3,1). Every run logs the expected position, what is
 * there, what the scan found and the offset between them, so a failure explains itself.
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResidentTests {
    private ResidentTests() {}

    /** Positions in the template (tools/structure-import/builds/placeholder_watchtower/build.json). */
    static final BlockPos MANAGER = new BlockPos(1, 3, 1);
    static final List<BlockPos> NESTS = List.of(new BlockPos(3, 9, 3), new BlockPos(1, 3, 5));
    static final ResourceLocation GONDOR = ResourceLocation.fromNamespaceAndPath("middle-earth", "gondor");
    static final ResourceLocation MANAGER_BLOCK = ResourceLocation.fromNamespaceAndPath("middle-earth", "structure_manager");
    static final ResourceLocation ORC_MANAGER_BLOCK = ResourceLocation.fromNamespaceAndPath("middle-earth", "orc_structure_manager");
    static final ResourceLocation NEST_BLOCK = ResourceLocation.fromNamespaceAndPath("middle-earth", "structure_nest");
    static final ResourceLocation LIMESTONE = ResourceLocation.fromNamespaceAndPath("middle-earth", "limestone_bricks");

    /** What the scan of the test area found. Positions are relative (same frame as GameTestHelper#absolutePos). */
    record Scan(List<BlockPos> managers, List<BlockPos> nests, Map<String, Integer> counts, BlockPos origin, AABB bounds) {}

    static Scan scan(GameTestHelper h) {
        AABB b = h.getBounds();
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        List<BlockPos> managers = new ArrayList<>(), nests = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        BlockPos lo = BlockPos.containing(b.minX - 1, b.minY - 1, b.minZ - 1);
        BlockPos hi = BlockPos.containing(b.maxX + 1, b.maxY + 1, b.maxZ + 1);
        for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
            BlockState s = h.getLevel().getBlockState(p);
            if (s.isAir()) continue;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
            counts.merge(id.toString(), 1, Integer::sum);
            if (id.equals(MANAGER_BLOCK) || id.equals(ORC_MANAGER_BLOCK)) managers.add(h.relativePos(p.immutable()));
            if (id.equals(NEST_BLOCK)) nests.add(h.relativePos(p.immutable()));
        }
        return new Scan(managers, nests, counts, origin, b);
    }

    /** Block, state and block entity (type and keys) at a relative position. */
    static String describe(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        BlockState s = h.getLevel().getBlockState(abs);
        StringBuilder out = new StringBuilder(rel.toShortString()).append(" (abs ").append(abs.toShortString()).append(") = ")
                .append(s);
        BlockEntity be = h.getLevel().getBlockEntity(abs);
        if (be == null) {
            out.append(", no block entity");
        } else {
            CompoundTag tag = be.saveWithoutMetadata(h.getLevel().registryAccess());
            out.append(", block entity ").append(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType())).append(' ')
                    .append(tag.getAllKeys());
        }
        return out.toString();
    }

    static String summary(GameTestHelper h, Scan sc) {
        StringBuilder out = new StringBuilder();
        out.append("structure-block origin ").append(sc.origin().toShortString()).append(", bounds ").append(sc.bounds())
                .append("; expected manager ").append(describe(h, MANAGER)).append("; nearby (+-1): ");
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos p = MANAGER.offset(0, dy, 0);
            out.append(p.toShortString()).append('=').append(BuiltInRegistries.BLOCK.getKey(h.getBlockState(p).getBlock())).append(' ');
        }
        out.append("; scan found managers ").append(sc.managers().stream().map(BlockPos::toShortString).toList())
                .append(" nests ").append(sc.nests().stream().map(BlockPos::toShortString).toList());
        if (!sc.managers().isEmpty()) {
            BlockPos d = sc.managers().get(0).subtract(MANAGER);
            out.append(" (offset from the template position ").append(d.toShortString()).append(')');
        }
        out.append("; limestone_bricks=").append(sc.counts().getOrDefault(LIMESTONE.toString(), 0))
                .append(" (template has 176); block counts ").append(sc.counts());
        return out.toString();
    }

    @GameTest(template = "build/placeholder_watchtower", timeoutTicks = 200)
    public static void placeholderSpawnsGondorGuards(GameTestHelper h) {
        String name = "placeholderSpawnsGondorGuards";
        LotrLog.info("test", "START {}", name);
        Scan sc = scan(h);
        String where = summary(h, sc);
        LotrLog.info("test", "{}: {}", name, where);
        if (sc.managers().isEmpty()) {
            String msg = "no structure manager in the test area: " + where;
            LotrLog.info("test", "FAIL {}: {}", name, msg);
            h.fail(msg);
            return;
        }
        BlockPos manager = sc.managers().get(0);
        BlockEntity be = h.getLevel().getBlockEntity(h.absolutePos(manager));
        if (be == null) {
            String msg = "manager block at " + manager.toShortString() + " has no block entity: " + where;
            LotrLog.info("test", "FAIL {}: {}", name, msg);
            h.fail(msg);
            return;
        }
        CompoundTag tag = be.saveWithoutMetadata(h.getLevel().registryAccess());
        String pool = tag.getString("structure_manager.Identifier");
        LotrLog.info("test", "{}: manager at {} type {} Identifier={} ToInitialize={} Enabled={} keys={}", name,
                manager.toShortString(), BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()), pool,
                tag.getBoolean("structure_manager.ToInitialize"), tag.getBoolean("structure_manager.Enabled"), tag.getAllKeys());
        if (!pool.equals("lotr_revival:gondor_watchtower")) {
            String msg = "manager pool is '" + pool + "' (keys " + tag.getAllKeys() + "): " + where;
            LotrLog.info("test", "FAIL {}: {}", name, msg);
            h.fail(msg);
            return;
        }
        // Nests subscribe within about 20 ticks and remove themselves, so some may already be gone at test start.
        BlockPos offset = manager.subtract(MANAGER);
        List<BlockPos> nests = NESTS.stream().map(n -> n.offset(offset)).toList();
        int[] ticks = {0};
        h.succeedWhen(() -> {
            ticks[0]++;
            List<NpcEntity> npcs = h.getLevel().getEntitiesOfClass(NpcEntity.class, h.getBounds().inflate(8),
                    npc -> GONDOR.equals(npc.getFactionIdentifier()));
            List<String> waiting = new ArrayList<>();
            for (BlockPos n : nests) {
                if (BuiltInRegistries.BLOCK.getKey(h.getBlockState(n).getBlock()).equals(NEST_BLOCK)) waiting.add(n.toShortString());
            }
            if (ticks[0] % 40 == 0) {
                BlockEntity m = h.getLevel().getBlockEntity(h.absolutePos(manager));
                CompoundTag t = m == null ? new CompoundTag() : m.saveWithoutMetadata(h.getLevel().registryAccess());
                LotrLog.info("test", "{}: tick {} nests waiting {} Gondor NPCs {} manager Enabled={} ToInitialize={} Nests={}", name,
                        ticks[0], waiting, npcs.size(), t.getBoolean("structure_manager.Enabled"),
                        t.getBoolean("structure_manager.ToInitialize"), t.contains("structure_manager.Nests"));
            }
            h.assertTrue(waiting.isEmpty(), "nests at " + waiting + " have not subscribed yet");
            h.assertTrue(npcs.size() >= 2, "only " + npcs.size() + " Gondor NPCs so far");
            LotrLog.info("test", "PASS {}: {} Gondor NPCs, nests subscribed (manager at {}, offset {})", name, npcs.size(),
                    manager.toShortString(), offset.toShortString());
        });
        // TODO(spec §Acceptance): a FAIL line on timeout needs a hook into the test's timeout; the tick-40 lines above
        // show how far it got.
    }
}
