package io.github.sethwillemsvg.lotrrevival.combat.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.combat.AttackTiming;
import io.github.sethwillemsvg.lotrrevival.combat.CombatConfig;
import io.github.sethwillemsvg.lotrrevival.combat.NpcAttackEndPayload;
import io.github.sethwillemsvg.lotrrevival.combat.NpcAttackPayload;
import io.github.sethwillemsvg.lotrrevival.combat.ParryState;
import io.github.sethwillemsvg.lotrrevival.combat.ParryWindow;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;

import java.util.ArrayList;
import java.util.List;

import static io.github.sethwillemsvg.lotrrevival.combat.ParryWindow.Result.EARLY;
import static io.github.sethwillemsvg.lotrrevival.combat.ParryWindow.Result.GLANCE;
import static io.github.sethwillemsvg.lotrrevival.combat.ParryWindow.Result.LATE;
import static io.github.sethwillemsvg.lotrrevival.combat.ParryWindow.Result.NONE;
import static io.github.sethwillemsvg.lotrrevival.combat.ParryWindow.Result.PERFECT;

/** Wind-up lengths, the pure parry window, and the payload codecs (specs/addon/combat-parry-warning.md, Acceptance tests). */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WindupTests {
    private WindupTests() {}

    private static Zombie zombie(GameTestHelper h, int x, int z) {
        Zombie zb = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(x, 1, z));
        zb.setSilent(true);
        return zb;
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void windupIsReadable(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "windupIsReadable");
        NpcEntity normal = run.soldier(0, 1, 0, 0f);
        NpcEntity heavy = run.soldier(2, 1, 0, 0f);
        AttackTiming.heavyChanceOverride(heavy, 1.0);
        Zombie a = zombie(h, 0, 1), b = zombie(h, 2, 1);
        long now = run.now();
        normal.doHurtTarget(a);
        heavy.doHurtTarget(b);
        AttackTiming.Pending pn = AttackTiming.pending(normal), ph = AttackTiming.pending(heavy);
        run.check(pn != null, "normal swing not held");
        run.check(ph != null, "heavy swing not held");
        if (pn != null) {
            run.note("normal due=" + pn.due + " start=" + pn.startTick);
            run.check(pn.due == now + CombatConfig.windupNormal(), "normal due " + pn.due + " != start " + now + " + " + CombatConfig.windupNormal());
            run.check(pn.startTick == now, "normal start tick " + pn.startTick + " != " + now);
            run.check(!pn.heavy, "normal swing marked heavy");
        }
        if (ph != null) {
            run.note("heavy due=" + ph.due);
            run.check(ph.due == now + CombatConfig.windupHeavy(), "heavy due " + ph.due + " != start " + now + " + " + CombatConfig.windupHeavy());
            run.check(ph.heavy, "forced heavy swing not marked heavy");
        }
        run.check(CombatConfig.windupNormal() == 10 && CombatConfig.windupHeavy() == 18,
                "config wind-ups are " + CombatConfig.windupNormal() + "/" + CombatConfig.windupHeavy() + ", spec defaults 10/18");
        run.finishNow();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void graceWindow(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "graceWindow");
        long hit = 1000;
        int w = 5, guard = 12;
        expect(run, ParryWindow.classify(ParryState.NONE, hit, w, guard, 0), NONE, "no press");
        expect(run, ParryWindow.classify(hit - 3, hit, w, guard, 0), PERFECT, "blade press at hit-3");
        expect(run, ParryWindow.classify(hit - 5, hit, w, guard, 0), PERFECT, "blade press at hit-5 (window edge)");
        expect(run, ParryWindow.classify(hit, hit, w, guard, 0), PERFECT, "press on the hit tick");
        expect(run, ParryWindow.classify(hit - 9, hit, w, guard, 0), GLANCE, "blade press at hit-9");
        expect(run, ParryWindow.classify(hit - 12, hit, w, guard, 0), EARLY, "blade press at hit-12 (guard over)");
        expect(run, ParryWindow.classify(hit - 9, hit, w, 0, 0), EARLY, "shield press at hit-9");
        expect(run, ParryWindow.classify(hit + 1, hit, w, guard, 0), LATE, "press at hit+1, g=0");
        expect(run, ParryWindow.classify(hit + 1, hit, w, guard, 3), PERFECT, "press at hit+1, g=3");
        expect(run, ParryWindow.classify(hit + 3, hit, w, guard, 3), PERFECT, "press at hit+3, g=3");
        expect(run, ParryWindow.classify(hit + 4, hit, w, guard, 3), LATE, "press at hit+4, g=3");
        expect(run, ParryWindow.classify(hit - 6, hit, w, guard, 3), GLANCE, "press at hit-6, g=3");
        run.check(ParryWindow.grace(0, 4) == 0, "grace(0 ms) = " + ParryWindow.grace(0, 4));
        run.check(ParryWindow.grace(120, 4) == 3, "grace(120 ms) = " + ParryWindow.grace(120, 4));
        run.check(ParryWindow.grace(500, 4) == 4, "grace(500 ms) = " + ParryWindow.grace(500, 4));
        run.check(ParryWindow.grace(50, 4) == 1, "grace(50 ms) = " + ParryWindow.grace(50, 4));
        run.finishNow();
    }

    private static void expect(CombatTestKit.Run run, ParryWindow.Result got, ParryWindow.Result want, String what) {
        run.check(got == want, what + ": " + got + ", expected " + want);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void payloadRoundTrip(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "payloadRoundTrip");
        for (String slot : new String[] {"", "attack_sword_2"})
            for (boolean heavy : new boolean[] {false, true})
                for (boolean mounted : new boolean[] {false, true}) {
                    NpcAttackPayload p = new NpcAttackPayload(123456, slot, heavy ? 18 : 10, mounted ? -1 : 77, NpcAttackPayload.flags(heavy, mounted));
                    ByteBuf buf = Unpooled.buffer();
                    NpcAttackPayload.STREAM_CODEC.encode(buf, p);
                    NpcAttackPayload back = NpcAttackPayload.STREAM_CODEC.decode(buf);
                    run.check(p.equals(back), "attack payload round trip: " + p + " -> " + back);
                    run.check(back.heavy() == heavy && back.mounted() == mounted && back.hasClip() == !slot.isEmpty(), "flags lost: " + back);
                }
        for (NpcAttackEndPayload.Reason reason : NpcAttackEndPayload.Reason.values()) {
            NpcAttackEndPayload p = NpcAttackEndPayload.of(42, reason);
            ByteBuf buf = Unpooled.buffer();
            NpcAttackEndPayload.STREAM_CODEC.encode(buf, p);
            NpcAttackEndPayload back = NpcAttackEndPayload.STREAM_CODEC.decode(buf);
            run.check(back.reasonValue() == reason && back.entityId() == 42, "end payload round trip: " + p + " -> " + back);
        }
        // every held swing sends a payload (captured through the test sink), clip or not
        NpcEntity npc = run.soldier(0, 1, 0, 0f);
        Zombie z = zombie(h, 0, 1);
        List<CustomPacketPayload> seen = new ArrayList<>();
        AttackTiming.testSink = p -> {
            if (p instanceof NpcAttackPayload a && a.entityId() == npc.getId()) seen.add(p);
            if (p instanceof NpcAttackEndPayload e && e.entityId() == npc.getId()) seen.add(p);
        };
        npc.doHurtTarget(z);   // the end payload comes at the hit frame: the sink stays until then
        long wait = AttackTiming.dueTick(npc) - run.now();
        run.finish(wait + 2, () -> {
            AttackTiming.testSink = null;
            run.note("payloads=" + seen);
            run.check(seen.stream().anyMatch(p -> p instanceof NpcAttackPayload a && a.targetId() == z.getId() && a.windupTicks() > 0),
                    "no attack payload for the swing");
            run.check(seen.stream().anyMatch(p -> p instanceof NpcAttackEndPayload), "no end payload after the hit frame");
            // TODO(spec "payloadRoundTrip"): force a race/class with no clip (all have one today) to check the clip-less payload
        });
    }
}
