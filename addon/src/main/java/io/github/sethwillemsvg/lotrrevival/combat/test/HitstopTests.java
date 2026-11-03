package io.github.sethwillemsvg.lotrrevival.combat.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.combat.Hitstop;
import io.github.sethwillemsvg.lotrrevival.combat.NpcHitPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** combat.Hitstop: freeze ticks by weapon class, plus one for dwarves, never above four; and NpcHitPayload's codec. */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HitstopTests {
    private HitstopTests() {}

    private static void expect(GameTestHelper h, String race, String cls, int ticks) {
        int got = Hitstop.ticks(race, cls);
        h.assertTrue(got == ticks, "hitstop for " + race + "/" + cls + " was " + got + ", expected " + ticks);
    }

    @GameTest(template = "empty")
    public static void hitstopTable(GameTestHelper h) {
        expect(h, "man", "dagger", 1);
        expect(h, "man", "sword", 2);
        expect(h, "man", "axe", 3);
        expect(h, "dwarf", "axe", 4);
        expect(h, "dwarf", "longsword", 4);
        expect(h, "orc", null, 1);
        expect(h, "elf", "banner", 2);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void hitPayloadRoundTrips(GameTestHelper h) {
        NpcHitPayload sent = new NpcHitPayload(42, 300_000, 3);   // a target id past one var-int byte
        ByteBuf buf = Unpooled.buffer();
        try {
            NpcHitPayload.STREAM_CODEC.encode(buf, sent);
            NpcHitPayload got = NpcHitPayload.STREAM_CODEC.decode(buf);
            h.assertTrue(sent.equals(got), "NpcHitPayload came back as " + got + ", sent " + sent);
            h.assertTrue(buf.readableBytes() == 0, buf.readableBytes() + " bytes left after decoding NpcHitPayload");
        } finally {
            buf.release();
        }
        h.succeed();
    }
}
