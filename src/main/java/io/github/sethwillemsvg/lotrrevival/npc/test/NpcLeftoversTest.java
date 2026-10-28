package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Core hookups for npc types: the shared npcs tag and the coin loot pool. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcLeftoversTest {
    private NpcLeftoversTest() {}

    private static List<EntityType<?>> allTypes() {
        List<EntityType<?>> all = new ArrayList<>();
        all.add(NpcEntityTypes.HOBBIT.get());
        NpcEntityTypes.standardTypes().forEach(t -> all.add(t.get()));
        return all;
    }

    @GameTest(template = "empty")
    public static void allNpcTypesAreTagged(GameTestHelper helper) {
        for (EntityType<?> type : allTypes()) {
            helper.assertTrue(type.is(ModTags.EntityTypes.NPCS), EntityType.getKey(type) + " not in #npcs");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void npcLootTablesHaveCoinPool(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (EntityType<?> type : allTypes()) {
            LootTable table = server.reloadableRegistries().getLootTable(type.getDefaultLootTable());
            String json = LootTable.DIRECT_CODEC.encodeStart(ops, table).getOrThrow().toString();
            helper.assertTrue(json.contains("lotrrevival:silver_coin"), EntityType.getKey(type) + " has no coin pool");
            helper.assertTrue(json.contains("killed_by_player"), EntityType.getKey(type) + " coin not player-gated");
        }
        helper.succeed();
    }
}
