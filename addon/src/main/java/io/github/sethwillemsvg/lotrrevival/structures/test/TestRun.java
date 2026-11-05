package io.github.sethwillemsvg.lotrrevival.structures.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/** START / PASS / FAIL lines for structures game tests (specs/addon/debug-logging.md), and raw template reading. */
final class TestRun {
    private TestRun() {}

    /** Runs a synchronous test body, logging START and PASS or FAIL, then succeeds the helper. */
    static void run(GameTestHelper h, String name, Runnable body) {
        LotrLog.info("test", "START {}", name);
        try {
            body.run();
        } catch (RuntimeException | AssertionError e) {
            LotrLog.info("test", "FAIL {}: {}", name, e.getMessage());
            throw e;
        }
        LotrLog.info("test", "PASS {}", name);
        h.succeed();
    }

    static void check(boolean ok, String message) {
        if (!ok) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    /** Every template under {@code data/lotr_revival/structure/build/}, read as raw NBT (missing blocks are not hidden). */
    static Map<ResourceLocation, CompoundTag> buildTemplates(GameTestHelper h) {
        Map<ResourceLocation, CompoundTag> out = new TreeMap<>();
        Map<ResourceLocation, Resource> found = h.getLevel().getServer().getResourceManager()
                .listResources("structure/build", rl -> rl.getPath().endsWith(".nbt"));
        for (var e : found.entrySet()) {
            if (!e.getKey().getNamespace().equals(LotrRevival.MOD_ID)) continue;
            try (InputStream in = e.getValue().open()) {
                out.put(e.getKey(), NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()));
            } catch (Exception ex) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("cannot read " + e.getKey() + ": " + ex);
            }
        }
        return out;
    }
}
