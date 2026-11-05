package io.github.sethwillemsvg.lotrrevival.structures.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;

/**
 * The placeholder watchtower, placed as the test structure, gets Gondor residents from Middle-earth's own manager and
 * nests (specs/addon/structures.md, Residents). Positions are the template's (see the placeholder manifest).
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResidentTests {
    private ResidentTests() {}

    static final BlockPos MANAGER = new BlockPos(1, 3, 1);
    static final List<BlockPos> NESTS = List.of(new BlockPos(3, 9, 3), new BlockPos(1, 3, 5));
    static final ResourceLocation GONDOR = ResourceLocation.fromNamespaceAndPath("middle-earth", "gondor");
    static final ResourceLocation NEST_BLOCK = ResourceLocation.fromNamespaceAndPath("middle-earth", "structure_nest");

    @GameTest(template = "build/placeholder_watchtower", timeoutTicks = 200)
    public static void placeholderSpawnsGondorGuards(GameTestHelper h) {
        String name = "placeholderSpawnsGondorGuards";
        LotrLog.info("test", "START {}", name);
        BlockEntity be = h.getLevel().getBlockEntity(h.absolutePos(MANAGER));
        if (be == null) {
            LotrLog.info("test", "FAIL {}: no structure manager block entity at {}", name, MANAGER.toShortString());
            h.fail("no structure manager at " + MANAGER.toShortString());
            return;
        }
        CompoundTag tag = be.saveWithoutMetadata(h.getLevel().registryAccess());
        LotrLog.info("test", "{}: manager {} Identifier={} ToInitialize={} Enabled={}", name,
                BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()), tag.getString("structure_manager.Identifier"),
                tag.getBoolean("structure_manager.ToInitialize"), tag.getBoolean("structure_manager.Enabled"));
        if (!tag.getString("structure_manager.Identifier").equals("lotr_revival:gondor_watchtower")) {
            LotrLog.info("test", "FAIL {}: manager pool is {}", name, tag.getString("structure_manager.Identifier"));
            h.fail("manager pool is " + tag.getString("structure_manager.Identifier"));
            return;
        }
        h.succeedWhen(() -> {
            for (BlockPos n : NESTS) {
                var id = BuiltInRegistries.BLOCK.getKey(h.getBlockState(n).getBlock());
                h.assertTrue(!id.equals(NEST_BLOCK), "nest at " + n.toShortString() + " has not subscribed yet");
            }
            List<NpcEntity> npcs = h.getLevel().getEntitiesOfClass(NpcEntity.class, h.getBounds().inflate(8),
                    npc -> GONDOR.equals(npc.getFactionIdentifier()));
            h.assertTrue(npcs.size() >= 2, "only " + npcs.size() + " Gondor NPCs so far");
            LotrLog.info("test", "PASS {}: {} Gondor NPCs, nests subscribed", name, npcs.size());
        });
        // TODO(spec §Acceptance): a FAIL line on timeout needs a hook into the test's timeout; CI reads the gametest report.
    }
}
