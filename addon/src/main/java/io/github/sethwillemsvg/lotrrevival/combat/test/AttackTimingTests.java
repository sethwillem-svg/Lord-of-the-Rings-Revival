package io.github.sethwillemsvg.lotrrevival.combat.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.combat.AttackTiming;
import io.github.sethwillemsvg.lotrrevival.combat.Hitstop;
import io.github.sethwillemsvg.lotrrevival.combat.NpcRaces;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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

    /** The "empty" template is a solid 3x3x3 stone block, so test targets suffocate (and burn in daylight); only the NPC's hits may hurt them. */
    private static final String TARGET_TAG = "attack_timing_target";

    static {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (LivingIncomingDamageEvent e) -> {
            if (e.getEntity().getTags().contains(TARGET_TAG) && !e.getSource().is(DamageTypes.MOB_ATTACK)) e.setCanceled(true);
        });
    }

    private static final ResourceLocation SOLDIER = ResourceLocation.fromNamespaceAndPath("middle-earth", "gondor.soldier");

    private static NpcEntity soldier(GameTestHelper h) {
        return soldier(h, new ItemStack(Items.IRON_SWORD));
    }

    private static NpcEntity soldier(GameTestHelper h, ItemStack weapon) {
        NpcEntity npc = new NpcEntityBuilder(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1))).withNpcType(SOLDIER).build();
        if (npc == null) throw new IllegalStateException("Middle-earth could not build " + SOLDIER);
        npc.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        npc.setNoAi(true);
        h.getLevel().addFreshEntity(npc);
        return npc;
    }

    private static Zombie target(GameTestHelper h) {
        Zombie z = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 2));   // one block in front
        z.setSilent(true);
        z.addTag(TARGET_TAG);
        return z;
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void damageLandsOnTheHitTick(GameTestHelper h) {
        NpcEntity npc = soldier(h);
        Zombie target = target(h);
        float health = target.getHealth();
        npc.doHurtTarget(target);
        long now = h.getLevel().getGameTime(), due = AttackTiming.dueTick(npc);
        h.assertTrue(due > now, "the swing's damage was not held for the hit frame");
        h.assertTrue(target.getHealth() == health, "damage was dealt at the swing, not at the hit frame");
        long wait = due - now;
        if (wait > 2) h.runAfterDelay(wait - 2, () -> h.assertTrue(target.getHealth() == health, "damage came before the hit tick"));
        h.runAfterDelay(wait + 2, () -> {
            h.assertTrue(target.getHealth() < health, "no damage on the hit tick");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void stepOutOfReachAndItMisses(GameTestHelper h) {
        NpcEntity npc = soldier(h);
        Zombie target = target(h);
        float health = target.getHealth();
        npc.doHurtTarget(target);
        long wait = AttackTiming.dueTick(npc) - h.getLevel().getGameTime();
        h.assertTrue(wait > 0, "the swing's damage was not held for the hit frame");
        target.teleportTo(target.getX() + 6, target.getY(), target.getZ());   // well past reach plus slack
        h.runAfterDelay(wait + 2, () -> {
            h.assertTrue(target.getHealth() == health, "a target out of reach was still hit");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void aLandedHitSendsHitstop(GameTestHelper h) {
        NpcEntity npc = soldier(h);
        Zombie target = target(h);
        npc.doHurtTarget(target);
        long wait = AttackTiming.dueTick(npc) - h.getLevel().getGameTime();
        h.assertTrue(wait > 0, "the swing's damage was not held for the hit frame");
        h.runAfterDelay(wait + 2, () -> {
            int expected = Hitstop.ticks(NpcRaces.of(npc), "sword");
            h.assertTrue(AttackTiming.hitsSent(npc) == 1, "a landed hit sent " + AttackTiming.hitsSent(npc) + " hit payloads, expected 1");
            h.assertTrue(AttackTiming.lastStop(npc) == expected, "hitstop sent was " + AttackTiming.lastStop(npc) + ", expected " + expected);
            h.succeed();
        });
    }

    /** The stop follows the clip played, not the item held: a bow-holder's melee blow is an unarmed clip and stops like one. */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void aBowHoldersPunchStopsLikeUnarmed(GameTestHelper h) {
        NpcEntity npc = soldier(h, new ItemStack(Items.BOW));
        Zombie target = target(h);
        npc.doHurtTarget(target);
        long wait = AttackTiming.dueTick(npc) - h.getLevel().getGameTime();
        h.assertTrue(wait > 0, "the swing's damage was not held for the hit frame");
        h.runAfterDelay(wait + 2, () -> {
            int expected = Hitstop.ticks(NpcRaces.of(npc), "unarmed");
            h.assertTrue(AttackTiming.hitsSent(npc) == 1, "a landed hit sent " + AttackTiming.hitsSent(npc) + " hit payloads, expected 1");
            h.assertTrue(AttackTiming.lastStop(npc) == expected, "a bow-holder's punch stopped " + AttackTiming.lastStop(npc) + " ticks, expected " + expected);
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void aMissSendsNoHitstop(GameTestHelper h) {
        NpcEntity npc = soldier(h);
        Zombie target = target(h);
        npc.doHurtTarget(target);
        long wait = AttackTiming.dueTick(npc) - h.getLevel().getGameTime();
        h.assertTrue(wait > 0, "the swing's damage was not held for the hit frame");
        target.teleportTo(target.getX() + 6, target.getY(), target.getZ());
        h.runAfterDelay(wait + 2, () -> {
            h.assertTrue(AttackTiming.lastStop(npc) == -1, "a miss sent hitstop " + AttackTiming.lastStop(npc));
            h.assertTrue(AttackTiming.hitsSent(npc) == 0, "a miss sent " + AttackTiming.hitsSent(npc) + " hit payloads");
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void aMissAfterAHitClearsTheHitstop(GameTestHelper h) {
        NpcEntity npc = soldier(h);
        Zombie target = target(h);
        npc.doHurtTarget(target);
        long first = AttackTiming.dueTick(npc) - h.getLevel().getGameTime();
        h.runAfterDelay(first + 2, () -> {
            h.assertTrue(AttackTiming.lastStop(npc) > 0, "the first hit sent no hitstop");
            target.setHealth(target.getMaxHealth());
            target.invulnerableTime = 0;
            npc.doHurtTarget(target);   // second attack: held, and the target steps out before it lands
            long second = AttackTiming.dueTick(npc) - h.getLevel().getGameTime();
            h.assertTrue(second > 0, "the second swing's damage was not held");
            h.assertTrue(AttackTiming.lastStop(npc) == -1, "a new attack kept the old hitstop");
            target.teleportTo(target.getX() + 6, target.getY(), target.getZ());
            h.runAfterDelay(second + 2, () -> {
                h.assertTrue(AttackTiming.lastStop(npc) == -1, "a miss after a hit sent hitstop " + AttackTiming.lastStop(npc));
                h.assertTrue(AttackTiming.hitsSent(npc) == 1, "a hit then a miss sent " + AttackTiming.hitsSent(npc) + " hit payloads, expected 1");
                h.succeed();
            });
        });
    }
}
