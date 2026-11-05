package io.github.sethwillemsvg.lotrrevival.structures.test;

import com.google.gson.JsonObject;
import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.dev.BlockDump;
import io.github.sethwillemsvg.lotrrevival.structures.dev.StructureUpgrade;
import net.minecraft.SharedConstants;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The dev modes' output formats (specs/addon/structures.md, Dev modes). */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DevModeTests {
    private DevModeTests() {}

    @GameTest(template = "empty")
    public static void blockDumpFormat(GameTestHelper h) {
        TestRun.run(h, "blockDumpFormat", () -> {
            JsonObject blocks = BlockDump.toJson().getAsJsonObject("blocks");
            TestRun.check(blocks.has("minecraft:stone"), "no minecraft:stone");
            JsonObject stairs = blocks.getAsJsonObject("minecraft:stone_brick_stairs");
            TestRun.check(stairs != null && stairs.getAsJsonObject("properties").has("facing")
                    && stairs.getAsJsonObject("default").get("half").getAsString().equals("bottom"), "stairs properties: " + stairs);
            String me = blocks.keySet().stream().filter(k -> k.startsWith("middle-earth:")).findFirst().orElse(null);
            TestRun.check(me != null, "no middle-earth: block in the dump");
            TestRun.check(blocks.has("middle-earth:structure_nest")
                    && blocks.getAsJsonObject("middle-earth:structure_nest").getAsJsonObject("properties").has("enabled"),
                    "structure_nest has no enabled property");
            LotrLog.info("test", "blockDumpFormat: {} blocks, first Middle-earth block {}", blocks.size(), me);
        });
    }

    @GameTest(template = "empty")
    public static void structureUpgradeSetsDataVersion(GameTestHelper h) {
        TestRun.run(h, "structureUpgradeSetsDataVersion", () -> {
            CompoundTag raw = new CompoundTag();
            raw.putInt("DataVersion", 3700);
            raw.put("palette", new ListTag());
            raw.put("blocks", new ListTag());
            raw.put("entities", new ListTag());
            CompoundTag up = StructureUpgrade.upgrade(raw);
            int now = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            TestRun.check(up.getInt("DataVersion") == now, "DataVersion " + up.getInt("DataVersion") + " != " + now);
        });
    }
}
