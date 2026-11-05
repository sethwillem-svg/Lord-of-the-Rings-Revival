package io.github.sethwillemsvg.lotrrevival.combat.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.combat.AttackTiming;
import io.github.sethwillemsvg.lotrrevival.combat.CombatConfig;
import io.github.sethwillemsvg.lotrrevival.combat.NpcParry;
import io.github.sethwillemsvg.lotrrevival.combat.Parry;
import io.github.sethwillemsvg.lotrrevival.combat.ParryState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.item.WeaponItemsME;

/**
 * Player and NPC parries against a real Middle-earth gondor.soldier (specs/addon/combat-parry-warning.md, Acceptance tests).
 * Mock players have 0 latency, so the grace g is 0 and hits resolve on the hit tick. Damage baselines come from the same
 * hit (AttackTiming.lastReplay: the damage before and after our multipliers), not from another test, so tests can run in
 * parallel. Each player stands one block in front of its NPC: NPC yaw 0 faces +Z, player yaw 180 faces -Z.
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ParryTests {
    private ParryTests() {}

    private static ItemStack shield(ServerPlayer p) {
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(WeaponItemsME.GONDORIAN_SHIELD));   // a MEDIUM Middle-earth shield
        return p.getOffhandItem();
    }

    private static void sword(ServerPlayer p) {
        p.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
    }

    private static void raise(ServerPlayer p) {
        p.startUsingItem(InteractionHand.OFF_HAND);
    }

    private static float lost(ServerPlayer p) {
        return p.getMaxHealth() - p.getHealth();
    }

    /** Checks a hit landed with dealt = base * mult and the player lost exactly that. */
    private static void checkHit(CombatTestKit.Run run, ServerPlayer p, double mult, String what) {
        AttackTiming.ReplayHit hit = AttackTiming.lastReplay(p);
        run.check(hit != null, what + ": no hit landed");
        if (hit == null) return;
        run.note(what + " base=" + hit.base() + " dealt=" + hit.dealt() + " lost=" + lost(p));
        run.check(CombatTestKit.near(hit.dealt(), (float) (hit.base() * mult)), what + ": dealt " + hit.dealt() + ", expected " + hit.base() + " x" + mult);
        run.check(CombatTestKit.near(lost(p), hit.dealt()), what + ": lost " + lost(p) + " health, the hit dealt " + hit.dealt());
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void perfectShieldParry(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "perfectShieldParry");
        NpcEntity npc = run.soldier(1, 1, 0, 0f);
        ServerPlayer p = run.player(1, 1, 1, 180f);
        ItemStack sh = shield(p);
        int wear = sh.getDamageValue();
        run.at(run.warmup(), () -> {
            long t0 = run.now();
            npc.doHurtTarget(p);
            long due = AttackTiming.dueTick(npc);
            run.check(due > t0, "swing not held");
            run.at(due - 3 - t0, () -> raise(p));
            run.finish(due + 2 - t0, () -> {
                long now = run.now();
                run.note("result=" + Parry.lastResult(p) + " stagger until " + ParryState.staggeredUntil(npc) + " now " + now);
                run.check(p.getHealth() == p.getMaxHealth(), "player lost " + lost(p) + " health");
                run.check(sh.getDamageValue() == wear, "shield wear " + wear + " -> " + sh.getDamageValue());
                run.check(ParryState.staggeredUntil(npc) > now, "NPC not staggered");
                run.check("parry ok".equals(Parry.lastResult(p)), "result " + Parry.lastResult(p));
                npc.doHurtTarget(p);
                run.check(AttackTiming.dueTick(npc) == -1, "a staggered NPC queued a swing");
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void earlyShieldIsVanillaBlock(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "earlyShieldIsVanillaBlock");
        NpcEntity npc = run.soldier(1, 1, 0, 0f);
        ServerPlayer p = run.player(1, 1, 1, 180f);
        ItemStack sh = shield(p);
        int wear = sh.getDamageValue();
        run.at(run.warmup(), () -> {
            raise(p);   // 15 ticks before the hit (the swing starts in 5, wind-up 10)
            // mock players are not ticked by a connection: tick the use so vanilla counts the shield as raised (>= 5 ticks)
            for (int i = 1; i <= 22; i++) run.at(i, p::doTick);
            run.at(5, () -> {
                long t0 = run.now();
                npc.doHurtTarget(p);
                long due = AttackTiming.dueTick(npc);
                run.note("press=" + ParryState.of(p).pressTick + " due=" + due);
                run.check(due - ParryState.of(p).pressTick == 15, "press was " + (due - ParryState.of(p).pressTick) + " ticks before the hit, not 15");
                run.finish(due + 2 - t0, () -> {
                    run.note("result=" + Parry.lastResult(p) + " blocking=" + p.isBlocking());
                    run.check(p.getHealth() == p.getMaxHealth(), "player lost " + lost(p) + " health through a raised shield");
                    run.check(sh.getDamageValue() > wear, "shield not worn by the vanilla block");
                    run.check(!ParryState.staggered(npc, run.now()), "NPC staggered by a vanilla block");
                    run.check("vanilla block".equals(Parry.lastResult(p)), "result " + Parry.lastResult(p));
                });
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void heavyIgnoresShield(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "heavyIgnoresShield");
        NpcEntity npc = run.soldier(1, 1, 0, 0f);
        AttackTiming.heavyChanceOverride(npc, 1.0);
        ServerPlayer p = run.player(1, 1, 1, 180f);
        shield(p);
        run.at(run.warmup(), () -> {
            long t0 = run.now();
            npc.doHurtTarget(p);
            AttackTiming.Pending pend = AttackTiming.pending(npc);
            run.check(pend != null && pend.heavy, "forced heavy swing not heavy");
            long due = AttackTiming.dueTick(npc);
            run.at(due - 3 - t0, () -> raise(p));
            run.finish(due + 2 - t0, () -> {
                checkHit(run, p, CombatConfig.heavyMultiplier(), "heavy");
                run.check(!ParryState.staggered(npc, run.now()), "NPC staggered by a heavy swing");
                run.check("heavy bypass".equals(Parry.lastResult(p)), "result " + Parry.lastResult(p));
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void bladePerfectAndGlance(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "bladePerfectAndGlance");
        NpcEntity npcA = run.soldier(0, 1, 0, 0f), npcB = run.soldier(2, 1, 0, 0f);
        ServerPlayer a = run.player(0, 1, 1, 180f), b = run.player(2, 1, 1, 180f);
        sword(a);
        sword(b);
        run.at(run.warmup(), () -> {
            long t0 = run.now();
            npcA.doHurtTarget(a);
            npcB.doHurtTarget(b);
            long due = AttackTiming.dueTick(npcA);
            run.check(AttackTiming.dueTick(npcB) == due, "the two swings are due on different ticks");
            run.at(due - 3 - t0, () -> run.check(Parry.press(a), "press A refused"));
            run.at(due - 9 - t0, () -> run.check(Parry.press(b), "press B refused"));
            run.finish(due + 2 - t0, () -> {
                run.check(a.getHealth() == a.getMaxHealth(), "perfect blade parry: A lost " + lost(a));
                run.check("parry ok".equals(Parry.lastResult(a)), "A result " + Parry.lastResult(a));
                checkHit(run, b, CombatConfig.glanceMultiplier(), "glance");
                run.check("glance".equals(Parry.lastResult(b)), "B result " + Parry.lastResult(b));
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void lateAndLockedPresses(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "lateAndLockedPresses");
        NpcEntity npcA = run.soldier(0, 1, 0, 0f), npcB = run.soldier(2, 1, 0, 0f);
        ServerPlayer a = run.player(0, 1, 1, 180f), b = run.player(2, 1, 1, 180f);
        sword(a);
        sword(b);
        run.at(run.warmup(), () -> {
            long t0 = run.now();
            npcA.doHurtTarget(a);
            long dueA = AttackTiming.dueTick(npcA);
            run.at(dueA + 1 - t0, () -> Parry.press(a));                     // late: g = 0
            run.check(Parry.press(b), "first press B refused");               // B: press at t0 ...
            run.at(10, () -> npcB.doHurtTarget(b));                          // ... swing due at t0 + 20: press 1 at due - 20
            long dueB = t0 + 10 + CombatConfig.windupNormal();
            run.at(dueB - 3 - t0, () -> run.check(Parry.press(b), "second press B refused (first guard should be over)"));
            run.finish(Math.max(dueA, dueB) + 2 - t0, () -> {
                checkHit(run, a, 1.0, "late press");
                run.check(lost(a) > 0, "a late press still parried");
                // spec rule: a locked blade press still guards, so the hit glances (the spec's test line says full damage;
                // the rule wins here, see the report) - anything but a perfect parry passes
                run.note("B result=" + Parry.lastResult(b) + " lost=" + lost(b));
                run.check("locked".equals(Parry.lastResult(b)), "B result " + Parry.lastResult(b) + ", expected locked");
                run.check(lost(b) > 0, "a locked press gave a perfect parry");
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void lockClearedByPerfect(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "lockClearedByPerfect");
        NpcEntity first = run.soldier(1, 1, 0, 0f), second = run.soldier(2, 1, 0, 0f);
        ServerPlayer p = run.player(1, 1, 1, 180f);
        sword(p);
        run.at(run.warmup(), () -> {
            long t0 = run.now();
            first.doHurtTarget(p);
            long due1 = AttackTiming.dueTick(first);
            run.at(due1 - 3 - t0, () -> Parry.press(p));
            long due2 = due1 + 8;   // second swing starts 2 ticks before the first lands
            run.at(due2 - CombatConfig.windupNormal() - t0, () -> second.doHurtTarget(p));
            run.at(due1 + 5 - t0, () -> run.check(Parry.press(p), "press 5 ticks after a perfect parry refused"));
            run.at(due1 + 1 - t0, () -> run.check("parry ok".equals(Parry.lastResult(p)), "first parry: " + Parry.lastResult(p)));
            run.finish(due2 + 2 - t0, () -> {
                run.check(p.getHealth() == p.getMaxHealth(), "lost " + lost(p) + " health");
                run.check("parry ok".equals(Parry.lastResult(p)), "second parry: " + Parry.lastResult(p));
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void behindCannotParry(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "behindCannotParry");
        NpcEntity npc = run.soldier(1, 1, 2, 180f);        // behind the player, facing it
        ServerPlayer p = run.player(1, 1, 1, 180f);       // faces -Z, away from the NPC
        shield(p);
        run.at(run.warmup(), () -> {
            long t0 = run.now();
            npc.doHurtTarget(p);
            long due = AttackTiming.dueTick(npc);
            run.at(due - 3 - t0, () -> raise(p));
            run.finish(due + 2 - t0, () -> {
                checkHit(run, p, 1.0, "from behind");
                run.check(lost(p) > 0, "no damage from behind");
                run.check("behind".equals(Parry.lastResult(p)), "result " + Parry.lastResult(p));
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void counterBonus(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "counterBonus");
        NpcEntity npcA = run.soldier(0, 1, 0, 0f), npcB = run.soldier(2, 1, 0, 0f);
        ServerPlayer a = run.player(0, 1, 1, 180f), b = run.player(2, 1, 1, 180f);
        shield(a);
        shield(b);
        run.at(run.warmup(), () -> {
            long t0 = run.now();
            npcA.doHurtTarget(a);
            npcB.doHurtTarget(b);
            long due = AttackTiming.dueTick(npcA);
            run.at(due - 3 - t0, () -> {
                raise(a);
                raise(b);
            });
            run.at(due + 1 - t0, () -> {
                run.check("parry ok".equals(Parry.lastResult(a)) && "parry ok".equals(Parry.lastResult(b)),
                        "setup parries: " + Parry.lastResult(a) + "/" + Parry.lastResult(b));
                npcA.invulnerableTime = 0;
                npcA.hurt(npcA.damageSources().playerAttack(a), 4f);
                float first = Parry.lastPlayerHit(npcA);
                npcA.invulnerableTime = 0;
                npcA.hurt(npcA.damageSources().playerAttack(a), 4f);
                float second = Parry.lastPlayerHit(npcA);
                run.note("counter=" + first + " next=" + second);
                run.check(CombatTestKit.near(first, 4f * (float) CombatConfig.counterMultiplier()), "counter hit dealt " + first);
                run.check(CombatTestKit.near(second, 4f), "second hit dealt " + second);
            });
            run.at(due + 1 + CombatConfig.counterTicks() + 1 - t0, () -> {
                npcB.invulnerableTime = 0;
                npcB.hurt(npcB.damageSources().playerAttack(b), 4f);
                run.note("expired=" + Parry.lastPlayerHit(npcB));
                run.check(CombatTestKit.near(Parry.lastPlayerHit(npcB), 4f), "hit after the counter window dealt " + Parry.lastPlayerHit(npcB));
            });
            run.finish(due + CombatConfig.counterTicks() + 4 - t0, () -> {});
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void npcParryRoll(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "npcParryRoll");
        NpcEntity front = run.soldier(0, 1, 0, 0f);     // faces +Z: the player at (1,1,1) is in front
        NpcEntity back = run.soldier(2, 1, 2, 0f);      // faces +Z: the player is behind it
        NpcEntity counter = run.soldier(0, 1, 2, 180f); // faces -Z: the player is in front
        for (NpcEntity n : new NpcEntity[] {front, back, counter}) NpcParry.chanceOverride(n, 1.0);
        ServerPlayer p = run.player(1, 1, 1, 0f);
        sword(p);
        run.at(1, () -> {
            float hp = front.getHealth();
            front.hurt(front.damageSources().playerAttack(p), 4f);
            run.check(Parry.lastPlayerHit(front) == 0f && front.getHealth() == hp, "front hit not parried: " + Parry.lastPlayerHit(front));
            front.invulnerableTime = 0;
            front.hurt(front.damageSources().playerAttack(p), 4f);
            run.check(CombatTestKit.near(Parry.lastPlayerHit(front), 4f), "second hit within the cooldown dealt " + Parry.lastPlayerHit(front));
            back.hurt(back.damageSources().playerAttack(p), 4f);
            run.check(CombatTestKit.near(Parry.lastPlayerHit(back), 4f), "hit from behind dealt " + Parry.lastPlayerHit(back));
            ParryState.PlayerParry s = ParryState.of(p);
            s.counterTarget = counter.getUUID();
            s.counterUntil = run.now() + 30;
            counter.hurt(counter.damageSources().playerAttack(p), 4f);
            run.check(CombatTestKit.near(Parry.lastPlayerHit(counter), 4f * (float) CombatConfig.counterMultiplier()),
                    "counter-bonus hit was rolled or not boosted: " + Parry.lastPlayerHit(counter));
        });
        run.finish(3, () -> {});
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void mountedAttackerParried(GameTestHelper h) {
        CombatTestKit.Run run = new CombatTestKit.Run(h, "mountedAttackerParried");
        Horse horse = h.spawnWithNoFreeWill(EntityType.HORSE, new BlockPos(1, 1, 0));
        NpcEntity npc = run.soldier(1, 1, 0, 0f);
        run.check(npc.startRiding(horse, true), "the soldier could not mount the horse");
        ServerPlayer p = run.player(1, 1, 2, 180f);
        shield(p);
        run.at(run.warmup(), () -> {
            long t0 = run.now();
            npc.doHurtTarget(p);
            AttackTiming.Pending pend = AttackTiming.pending(npc);
            run.check(pend != null && pend.mounted, "swing not held as mounted");
            long due = AttackTiming.dueTick(npc);
            run.at(due - 3 - t0, () -> raise(p));
            run.finish(due + 2 - t0, () -> {
                run.note("result=" + Parry.lastResult(p) + " dist=" + npc.distanceTo(p));
                run.check(p.getHealth() == p.getMaxHealth(), "player lost " + lost(p) + " to rider or mount");
                run.check("parry ok".equals(Parry.lastResult(p)), "result " + Parry.lastResult(p));
            });
        });
    }
}
