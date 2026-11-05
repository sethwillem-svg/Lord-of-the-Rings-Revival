package io.github.sethwillemsvg.lotrrevival.combat.test;

import io.github.sethwillemsvg.lotrrevival.combat.AttackTiming;
import io.github.sethwillemsvg.lotrrevival.combat.NpcParry;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.entity.npcs.initializer.NpcEntityBuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared setup for the combat GameTests: a real Middle-earth gondor.soldier (no AI, iron sword, heavy and NPC parry chance
 * forced to 0 unless a test sets them), mock players in survival, and a Run that logs START / PASS / FAIL through the test
 * logger and always removes its mock players and overrides, whatever happens. Checks are collected and judged once, in the
 * last scheduled step, so an early failure never skips the cleanup.
 *
 * Yaw: 0 faces +Z, 180 faces -Z. The template is 3x3x3; everything stays inside it.
 */
final class CombatTestKit {
    private CombatTestKit() {}

    static final ResourceLocation SOLDIER = ResourceLocation.fromNamespaceAndPath("middle-earth", "gondor.soldier");

    static final class Run {
        final GameTestHelper h;
        final String name;
        private final List<ServerPlayer> players = new ArrayList<>();
        private final List<NpcEntity> npcs = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        private boolean done;

        Run(GameTestHelper h, String name) {
            this.h = h;
            this.name = name;
            LotrLog.info("test", "START {}", name);
        }

        long now() {
            return h.getLevel().getGameTime();
        }

        NpcEntity soldier(int x, int y, int z, float yaw) {
            NpcEntity npc = new NpcEntityBuilder(h.getLevel(), h.absolutePos(new BlockPos(x, y, z))).withNpcType(SOLDIER).build();
            if (npc == null) throw new IllegalStateException("Middle-earth could not build " + SOLDIER);
            npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            npc.setNoAi(true);
            npc.setYRot(yaw);
            npc.setYHeadRot(yaw);
            npc.setYBodyRot(yaw);
            h.getLevel().addFreshEntity(npc);
            AttackTiming.heavyChanceOverride(npc, 0.0);
            NpcParry.chanceOverride(npc, 0.0);
            npcs.add(npc);
            return npc;
        }

        ServerPlayer player(int x, int y, int z, float yaw) {
            ServerPlayer p = io.github.sethwillemsvg.lotrrevival.core.test.TestPlayers.mock(h);
            players.add(p);
            p.setGameMode(GameType.SURVIVAL);
            Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(x, y, z)));
            p.moveTo(at.x, at.y, at.z, yaw, 0f);
            p.setYHeadRot(yaw);
            p.setHealth(p.getMaxHealth());
            p.invulnerableTime = 0;
            clearSpawnInvulnerability(p);
            return p;
        }

        /** Ticks to wait before a mock player can be hurt (spawn invulnerability), see clearSpawnInvulnerability. */
        int warmup() {
            return spawnInvulnerabilityCleared ? 1 : 61;
        }

        void check(boolean ok, String what) {
            if (!ok) failures.add(what);
        }

        void note(String s) {
            notes.add(s);
        }

        /** Runs a step later; an exception becomes a failure instead of ending the test before its cleanup. */
        void at(long delay, Runnable step) {
            h.runAfterDelay(Math.max(0, delay), () -> {
                if (done) return;
                try {
                    step.run();
                } catch (Throwable t) {
                    failures.add("step threw " + t);
                }
            });
        }

        /** The last step: judges the checks, cleans up, logs PASS or FAIL and ends the test. */
        void finish(long delay, Runnable lastChecks) {
            h.runAfterDelay(Math.max(0, delay), this::finishNow);
            this.lastChecks = lastChecks;
        }

        private Runnable lastChecks = () -> {};

        void finishNow() {
            if (done) return;
            try {
                lastChecks.run();
            } catch (Throwable t) {
                failures.add("checks threw " + t);
            } finally {
                cleanup();
            }
            if (failures.isEmpty()) {
                LotrLog.info("test", "PASS {}: {}", name, String.join("; ", notes));
                h.succeed();
            } else {
                String why = String.join("; ", failures);
                LotrLog.info("test", "FAIL {}: {} (notes: {})", name, why, String.join("; ", notes));
                h.fail(name + ": " + why);
            }
        }

        private void cleanup() {
            done = true;
            for (ServerPlayer p : players) {
                try {
                    h.getLevel().getServer().getPlayerList().remove(p);
                } catch (RuntimeException e) {
                    LotrLog.warn("test", "{}: could not remove a mock player: {}", name, e.toString());
                }
            }
            players.clear();
            for (NpcEntity npc : npcs) {
                AttackTiming.heavyChanceOverride(npc, null);
                NpcParry.chanceOverride(npc, null);
            }
        }
    }

    private static boolean spawnInvulnerabilityCleared;

    /**
     * A fresh ServerPlayer ignores damage for 60 ticks; mock players are no exception. NeoForge runs Mojang names, so the
     * private field is set by name; if that fails the tests wait it out (Run.warmup).
     */
    static void clearSpawnInvulnerability(ServerPlayer p) {
        try {
            Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(p, 0);
            spawnInvulnerabilityCleared = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            spawnInvulnerabilityCleared = false;
            LotrLog.info("test", "spawn invulnerability not cleared ({}); waiting 61 ticks", e.toString());
        }
    }

    static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.01f;
    }
}
