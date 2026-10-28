package io.github.sethwillemsvg.lotrrevival.npc.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.npc.ai.combat.NpcCombat;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.NpcUnitData;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitEntry;
import io.github.sethwillemsvg.lotrrevival.npc.hire.UnitList;
import io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitAi;
import io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitCommands;
import io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitTargeting;
import io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitWitnesses;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Hired-unit AI (ticket #70): follow, teleport, guard, halt/ready, owner-aware targeting and witnesses. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcUnitAiTest {
    private NpcUnitAiTest() {}

    // ------------------------------------------------------------------ fixtures

    private static ServerPlayer mockPlayer(GameTestHelper helper, UUID id, int x, int z) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, "npc-unit-ai-mock"), false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        Vec3 pos = helper.absoluteVec(new Vec3(x + 0.5, 0, z + 0.5));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    private static ServerPlayer mockPlayer(GameTestHelper helper, int x, int z) {
        return mockPlayer(helper, UUID.randomUUID(), x, z);
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static NpcEntity hiredUnit(GameTestHelper helper, ServerPlayer owner, int x, int z) {
        NpcEntity unit = helper.spawn(NpcEntityTypes.HOBBIT_BOUNDER.get(), new BlockPos(x, 0, z));
        unit.setPersistenceRequired();
        unit.setInvulnerable(true);
        UnitList list = NpcUnitData.list(NpcEntityTypes.HOBBIT_SHIRRIFF.get(), NpcRole.UNIT_TRADER)
                .orElseThrow(() -> new AssertionError("shirriff unit list missing"));
        UnitEntry entry = list.entry("bounder").orElseThrow(() -> new AssertionError("bounder entry missing"));
        // Keep the owner in good standing so the desertion check never fires during the test.
        Alignment.set(owner, Factions.HOBBIT, 500, AlignmentSource.command());
        HiredUnits.hire(unit, owner, list, entry, "");
        return unit;
    }

    private static <T extends net.minecraft.world.entity.Mob> T still(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T mob = helper.spawn(type, new BlockPos(x, 0, z));
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.setPersistenceRequired();
        return mob;
    }

    private static double flat(NpcEntity a, net.minecraft.world.entity.Entity b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** Fails the test if the unit moves farther than a walk in one tick (a teleport). */
    private static void assertNeverJumps(GameTestHelper helper, NpcEntity unit, String what) {
        Vec3[] last = {unit.position()};
        helper.onEachTick(() -> {
            double step = unit.position().distanceTo(last[0]);
            last[0] = unit.position();
            helper.assertTrue(step < 3.0, what + ": moved " + step + " in one tick");
        });
    }

    // ------------------------------------------------------------------ following and teleport

    @GameTest(template = "npc_arena", timeoutTicks = 260)
    public static void followingUnitWalksBackAndStopsWithinSix(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 34);
        try {
            NpcEntity unit = hiredUnit(helper, owner, 8, 14);
            assertNeverJumps(helper, unit, "20 blocks away is walkable, no teleport");
            double[] closest = {Double.MAX_VALUE};
            helper.onEachTick(() -> closest[0] = Math.min(closest[0], unit.distanceTo(owner)));
            helper.runAfterDelay(200, () -> {
                try {
                    double dist = unit.distanceTo(owner);
                    helper.assertTrue(closest[0] <= 6.2, "should path back to within 6 blocks, closest " + closest[0]);
                    helper.assertTrue(closest[0] >= 2.0, "should stop, not run into the owner: " + closest[0]);
                    helper.assertTrue(dist <= 9.5, "should stay near the owner afterwards, is " + dist);
                    helper.assertTrue(UnitAi.status(unit) == UnitAi.Status.FOLLOWING, "status " + UnitAi.status(unit));
                    unit.discard();
                    helper.succeed();
                } finally {
                    remove(helper, owner);
                }
            });
        } catch (RuntimeException e) {
            remove(helper, owner);
            throw e;
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void farUnitWithNoPathTeleportsNextToOwner(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 38);
        try {
            NpcEntity unit = hiredUnit(helper, owner, 8, 0);
            helper.assertTrue(unit.distanceTo(owner) > 30, "setup distance " + unit.distanceTo(owner));
            helper.runAfterDelay(40, () -> {
                try {
                    double dist = flat(unit, owner);
                    helper.assertTrue(dist >= 2.5 && dist <= 6.5, "expected 3-6 blocks from the owner, is " + dist);
                    unit.discard();
                    helper.succeed();
                } finally {
                    remove(helper, owner);
                }
            });
        } catch (RuntimeException e) {
            remove(helper, owner);
            throw e;
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void teleportOffKeepsFarUnitWalking(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 38);
        try {
            NpcEntity unit = hiredUnit(helper, owner, 8, 0);
            helper.assertTrue(UnitCommands.setTeleport(unit, false), "teleport switch");
            assertNeverJumps(helper, unit, "teleport off");
            helper.runAfterDelay(45, () -> {
                try {
                    unit.discard();
                    helper.succeed();
                } finally {
                    remove(helper, owner);
                }
            });
        } catch (RuntimeException e) {
            remove(helper, owner);
            throw e;
        }
    }

    // ------------------------------------------------------------------ guard

    @GameTest(template = "npc_arena", timeoutTicks = 200)
    public static void guardIgnoresFarZombieAndAttacksNearOne(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 2);
        try {
            NpcEntity unit = hiredUnit(helper, owner, 8, 10);
            helper.assertTrue(UnitCommands.setGuard(unit, true, 8), "guard on");
            Zombie far = still(helper, EntityType.ZOMBIE, 8, 22);
            helper.onEachTick(() -> {
                if (far.isAlive()) {
                    helper.assertTrue(unit.getTarget() != far, "guard must ignore a zombie 12 blocks away");
                }
            });
            helper.runAfterDelay(50, () -> {
                helper.assertTrue(unit.getTarget() == null, "still no target, has " + unit.getTarget());
                Zombie near = still(helper, EntityType.ZOMBIE, 8, 15);
                helper.succeedWhen(() -> {
                    helper.assertTrue(unit.getTarget() == near, "should attack the zombie 5 blocks away");
                    near.discard();
                    far.discard();
                    unit.discard();
                    remove(helper, owner);
                });
            });
        } catch (RuntimeException e) {
            remove(helper, owner);
            throw e;
        }
    }

    // ------------------------------------------------------------------ halt and ready

    @GameTest(template = "npc_arena", timeoutTicks = 400)
    public static void haltDropsTargetAndStopsReadyResumes(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 24);
        try {
            NpcEntity unit = hiredUnit(helper, owner, 8, 8);
            Zombie zombie = still(helper, EntityType.ZOMBIE, 8, 12);
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(unit.getTarget() == zombie, "unit should be fighting the zombie before the halt");
                helper.assertTrue(UnitCommands.halt(unit), "halt reaches a following unit");
                helper.assertTrue(unit.getTarget() == null, "halt drops the target");
                Vec3 spot = unit.position();
                helper.runAfterDelay(50, () -> {
                    helper.assertTrue(unit.getTarget() == null, "halted unit seeks nothing");
                    helper.assertTrue(unit.position().distanceTo(spot) < 0.7, "halted unit stands still");
                    helper.assertTrue(UnitAi.status(unit) == UnitAi.Status.HALTED, "status " + UnitAi.status(unit));

                    Zombie biter = still(helper, EntityType.ZOMBIE, 8, 10);
                    unit.setInvulnerable(false);
                    unit.hurt(helper.getLevel().damageSources().mobAttack(biter), 1.0f);
                    unit.setInvulnerable(true);
                    helper.assertTrue(unit.getTarget() == biter, "a halted unit still fights back when hit");

                    biter.discard();
                    zombie.discard();
                    unit.setTarget(null);
                    unit.retaliation().clear();
                    helper.assertTrue(UnitCommands.ready(unit), "ready reaches the unit");
                    helper.succeedWhen(() -> {
                        helper.assertTrue(unit.distanceTo(owner) <= 7.0, "ready resumes following: " + unit.distanceTo(owner));
                        unit.discard();
                        remove(helper, owner);
                    });
                });
            });
        } catch (RuntimeException e) {
            remove(helper, owner);
            throw e;
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void hornCommandsSkipGuardsAndTravelCompanionFollowsState(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 6);
        try {
            NpcEntity unit = hiredUnit(helper, owner, 8, 8);
            helper.assertTrue(unit.travelsWith(owner), "a following unit travels with its owner");
            UnitCommands.setGuard(unit, true, 8);
            helper.assertTrue(!unit.travelsWith(owner), "a guarding unit stays");
            helper.assertTrue(!UnitCommands.halt(unit), "horns do not reach a guard");
            helper.assertTrue(!UnitCommands.ready(unit), "horns do not reach a guard (ready)");
            helper.assertTrue(!UnitCommands.summon(unit), "horns do not reach a guard (summon)");
            UnitCommands.setGuard(unit, false, 8);
            helper.assertTrue(unit.travelsWith(owner), "guard off follows again");
            UnitCommands.halt(unit);
            helper.assertTrue(!unit.travelsWith(owner), "a halted unit stays");
            UnitCommands.ready(unit);
            helper.assertTrue(unit.travelsWith(owner), "ready follows again");
            helper.assertTrue(UnitCommands.setGuardRange(unit, 500), "range change");
            helper.assertTrue(
                    HiredUnits.get(unit).command().guardRange() == UnitCommands.MAX_GUARD_RANGE, "range is clamped");
            unit.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void summonMovesUnitNextToOwner(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 30);
        try {
            NpcEntity unit = hiredUnit(helper, owner, 8, 4);
            UnitCommands.halt(unit);
            helper.assertTrue(UnitCommands.summon(unit), "summon moves the unit");
            double dist = flat(unit, owner);
            helper.assertTrue(dist <= 6.5, "summoned within 6 blocks, is " + dist);
            unit.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    // ------------------------------------------------------------------ owner-aware targeting

    @GameTest(template = "npc_arena", timeoutTicks = 200)
    public static void unitFightsOwnersAttackerAndTargetNeverOwner(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 6);
        try {
            NpcEntity unit = hiredUnit(helper, owner, 8, 8);
            Cow struck = still(helper, EntityType.COW, 4, 12);
            Cow attacker = still(helper, EntityType.COW, 12, 12);
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(unit.getTarget() == null, "peaceful cows are not targets, has " + unit.getTarget());
                owner.setLastHurtMob(struck);
                helper.runAfterDelay(30, () -> {
                    helper.assertTrue(unit.getTarget() == struck, "unit should attack what its owner hit");
                    struck.discard();
                    unit.setTarget(null);
                    helper.assertTrue(
                            UnitTargeting.judge(unit, attacker) == UnitTargeting.Verdict.NORMAL, "cow is ordinary");
                    owner.setLastHurtByMob(attacker);
                    helper.runAfterDelay(30, () -> {
                        helper.assertTrue(unit.getTarget() == attacker, "unit should attack what hurt its owner");
                        Alignment.set(owner, Factions.HOBBIT, -100, AlignmentSource.command());
                        helper.assertTrue(
                                UnitTargeting.judge(unit, owner) == UnitTargeting.Verdict.REJECT,
                                "never the owner, even at hostile alignment");
                        helper.assertTrue(
                                !NpcCombat.isHostilePlayer(unit, owner), "owner is not a hostile player to its unit");
                        attacker.discard();
                        unit.discard();
                        remove(helper, owner);
                        helper.succeed();
                    });
                });
            });
        } catch (RuntimeException e) {
            remove(helper, owner);
            throw e;
        }
    }

    @GameTest(template = "npc_arena", timeoutTicks = 120)
    public static void unitNeverTargetsOwnersMountOrSiblingUnit(GameTestHelper helper) {
        ServerPlayer owner = mockPlayer(helper, 8, 6);
        try {
            NpcEntity unit = hiredUnit(helper, owner, 8, 8);
            NpcEntity sibling = hiredUnit(helper, owner, 10, 8);
            helper.assertTrue(
                    UnitTargeting.judge(unit, sibling) == UnitTargeting.Verdict.REJECT, "sibling units never fight");
            Cow mount = still(helper, EntityType.COW, 8, 7);
            owner.setLastHurtMob(mount);
            helper.assertTrue(
                    UnitTargeting.judge(unit, mount) == UnitTargeting.Verdict.OWNER_TARGET, "control: cow is a target");
            owner.startRiding(mount, true);
            helper.assertTrue(UnitTargeting.judge(unit, mount) == UnitTargeting.Verdict.REJECT, "owner's mount");
            owner.stopRiding();
            mount.discard();
            sibling.discard();
            unit.discard();
            helper.succeed();
        } finally {
            remove(helper, owner);
        }
    }

    // ------------------------------------------------------------------ witnesses

    @GameTest(template = "npc_arena", timeoutTicks = 100)
    public static void killersOwnUnitDoesNotJoinWitnesses(GameTestHelper helper) {
        helper.assertTrue(UnitWitnesses.excludes(true, -100.0f), "own unit always sits out");
        helper.assertTrue(UnitWitnesses.excludes(false, 10.0f), "positive standing keeps units out");
        helper.assertTrue(!UnitWitnesses.excludes(false, 0.0f), "zero standing: unit may join");
        helper.assertTrue(!UnitWitnesses.excludes(false, -5.0f), "negative standing: unit may join");

        ServerPlayer killer = mockPlayer(helper, 8, 6);
        try {
            NpcEntity own = hiredUnit(helper, killer, 6, 20);
            NpcEntity control = helper.spawn(NpcEntityTypes.HOBBIT_BOUNDER.get(), new BlockPos(10, 0, 20));
            control.setPersistenceRequired();
            NpcEntity victim = helper.spawn(NpcEntityTypes.HOBBIT_BOUNDER.get(), new BlockPos(8, 0, 20));
            victim.setPersistenceRequired();
            helper.assertTrue(own.getTarget() == null && control.getTarget() == null, "idle before the kill");
            // Friendly fire would swallow the hit at positive standing; no tick passes before the kill, so no desertion.
            Alignment.set(killer, Factions.HOBBIT, 0, AlignmentSource.command());
            victim.hurt(helper.getLevel().damageSources().playerAttack(killer), 1000.0f);
            helper.assertTrue(!victim.isAlive(), "victim died");
            helper.assertTrue(control.getTarget() == killer, "an unhired same-faction witness turns on the killer");
            helper.assertTrue(own.getTarget() == null, "the killer's own unit stays out of it");
            own.discard();
            control.discard();
            helper.succeed();
        } finally {
            remove(helper, killer);
        }
    }

    // ------------------------------------------------------------------ owner away

    @GameTest(template = "npc_arena", timeoutTicks = 260)
    public static void ownerAwayUnitStaysThenFollowsAgain(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        ServerPlayer first = mockPlayer(helper, id, 8, 30);
        ServerPlayer[] current = {first};
        try {
            NpcEntity unit = hiredUnit(helper, first, 8, 10);
            Vec3 spot = unit.position();
            remove(helper, first);
            current[0] = null;
            helper.runAfterDelay(80, () -> {
                try {
                    helper.assertTrue(UnitAi.status(unit) == UnitAi.Status.OWNER_AWAY, "status " + UnitAi.status(unit));
                    helper.assertTrue(
                            unit.position().distanceTo(spot) <= UnitAi.AWAY_RANGE,
                            "unit stays within 8 of its spot, is " + unit.position().distanceTo(spot));
                    ServerPlayer back = mockPlayer(helper, id, 8, 30);
                    current[0] = back;
                    helper.succeedWhen(() -> {
                        helper.assertTrue(
                                UnitAi.status(unit) != UnitAi.Status.OWNER_AWAY, "owner is back: " + UnitAi.status(unit));
                        helper.assertTrue(flat(unit, back) <= 7.0, "follows the returned owner: " + flat(unit, back));
                        unit.discard();
                        remove(helper, back);
                        current[0] = null;
                    });
                } catch (RuntimeException e) {
                    if (current[0] != null) {
                        remove(helper, current[0]);
                    }
                    throw e;
                }
            });
        } catch (RuntimeException e) {
            if (current[0] != null) {
                remove(helper, current[0]);
            }
            throw e;
        }
    }
}
