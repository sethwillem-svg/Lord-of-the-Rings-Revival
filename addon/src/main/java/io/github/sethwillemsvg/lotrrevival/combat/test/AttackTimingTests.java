package io.github.sethwillemsvg.lotrrevival.combat.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.combat.AttackTiming;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.entity.npcs.initializer.NpcEntityBuilder;

/**
 * combat.AttackTiming on a real Middle-earth NPC: its melee damage waits for the attack clip's hit tick, lands on it, and
 * misses a target that stepped out of reach. Run with ./gradlew runGameTestServer -PmeJar=... (the Middle-earth jar).
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AttackTimingTests {
    private AttackTimingTests() {}

    private static final ResourceLocation SOLDIER = ResourceLocation.fromNamespaceAndPath("middle-earth", "gondor.soldier");

    private static NpcEntity soldier(GameTestHelper h) {
        NpcEntity npc = new NpcEntityBuilder(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1))).withNpcType(SOLDIER).build();
        if (npc == null) throw new IllegalStateException("Middle-earth could not build " + SOLDIER);
        npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        npc.setNoAi(true);
        h.getLevel().addFreshEntity(npc);
        AttackTiming.heavyChanceOverride(npc, 0.0);   // normal wind-up every run (weak map: cleared with the NPC)
        return npc;
    }

    private static Zombie target(GameTestHelper h) {
        Zombie z = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 2));   // one block in front
        z.setSilent(true);
        // a helmet keeps it from burning in daylight: fire damage must not pass for an NPC hit (stepOutOfReachAndItMisses)
        z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return z;
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void damageLandsOnTheHitTick(GameTestHelper h) {
        LotrLog.info("test", "START damageLandsOnTheHitTick");
        NpcEntity npc = soldier(h);
        Zombie target = target(h);
        npc.doHurtTarget(target);
        long now = h.getLevel().getGameTime(), due = AttackTiming.dueTick(npc);
        h.assertTrue(due > now, "the swing's damage was not held for the hit frame");
        // judged by our replayed hit, not by health: the zombie can take other damage (it took inWall in run 212)
        h.assertTrue(AttackTiming.lastReplay(target) == null, "damage was dealt at the swing, not at the hit frame");
        long wait = due - now;
        if (wait > 2) h.runAfterDelay(wait - 2, () -> {
            if (AttackTiming.lastReplay(target) != null) {
                String why = "the NPC hit landed at tick " + h.getLevel().getGameTime() + ", before the hit tick " + due;
                LotrLog.info("test", "FAIL damageLandsOnTheHitTick: {}", why);
                h.fail(why);
            }
        });
        h.runAfterDelay(wait + 2, () -> {
            AttackTiming.ReplayHit hit = AttackTiming.lastReplay(target);
            if (hit == null) {
                LotrLog.info("test", "FAIL damageLandsOnTheHitTick: no hit on the hit tick (dist {}, reach {})",
                        String.format("%.2f", npc.distanceTo(target)), String.format("%.2f", AttackTiming.reachFor(npc, target)));
                h.fail("no damage on the hit tick");
            }
            LotrLog.info("test", "PASS damageLandsOnTheHitTick: hit at {} (due {}), dealt {}", h.getLevel().getGameTime(), due, hit.dealt());
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void stepOutOfReachAndItMisses(GameTestHelper h) {
        LotrLog.info("test", "START stepOutOfReachAndItMisses");
        NpcEntity npc = soldier(h);
        Zombie target = target(h);
        float health = target.getHealth();
        npc.doHurtTarget(target);
        long wait = AttackTiming.dueTick(npc) - h.getLevel().getGameTime();
        h.assertTrue(wait > 0, "the swing's damage was not held for the hit frame");
        target.teleportTo(target.getX() + 6, target.getY(), target.getZ());   // well past reach plus slack
        LotrLog.info("test", "stepOutOfReachAndItMisses: moved target to dist {} (reach + slack {}), due in {} ticks",
                String.format("%.2f", npc.distanceTo(target)), String.format("%.2f", AttackTiming.reachFor(npc, target)), wait);
        h.runAfterDelay(wait + 2, () -> {
            // judged by our replayed hit, not by health: anything else hurting the zombie (sunlight, a neighbour test) is not a hit
            AttackTiming.ReplayHit hit = AttackTiming.lastReplay(target);
            String why = hit != null ? "a target out of reach was still hit (dist " + String.format("%.2f", npc.distanceTo(target)) + ", dealt "
                    + hit.dealt() + ")" : null;
            if (why == null && target.getHealth() != health)
                LotrLog.info("test", "stepOutOfReachAndItMisses: zombie lost {} health to {} (not the NPC swing)", health - target.getHealth(),
                        target.getLastDamageSource() == null ? "?" : target.getLastDamageSource().getMsgId());
            if (why != null) {
                LotrLog.info("test", "FAIL stepOutOfReachAndItMisses: {}", why);
                h.fail(why);
            }
            LotrLog.info("test", "PASS stepOutOfReachAndItMisses: no hit landed");
            h.succeed();
        });
    }
}
