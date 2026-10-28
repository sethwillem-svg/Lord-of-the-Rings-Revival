package io.github.sethwillemsvg.lotrrevival.npc.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.anchor.NpcAnchors;
import io.github.sethwillemsvg.lotrrevival.npc.anchor.NpcAttachments;
import io.github.sethwillemsvg.lotrrevival.npc.anchor.NpcMarker;
import io.github.sethwillemsvg.lotrrevival.npc.anchor.NpcSpawnAnchors;
import io.github.sethwillemsvg.lotrrevival.npc.anchor.SpawnAnchor;
import io.github.sethwillemsvg.lotrrevival.npc.entity.HobbitEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.entity.StandardNpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.routine.NpcRoutine;
import io.github.sethwillemsvg.lotrrevival.npc.routine.RoutineGoal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Day/night routine, spawn anchors and the structure marker parser. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcRoutineAnchorTest {
    private NpcRoutineAnchorTest() {}

    private static List<Entity> members(ServerLevel level, SpawnAnchor anchor) {
        List<Entity> found = new ArrayList<>();
        for (UUID id : anchor.npcs()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                found.add(entity);
            }
        }
        return found;
    }

    private static SpawnAnchor onlyAnchor(GameTestHelper helper, LevelChunk chunk) {
        List<SpawnAnchor> anchors = NpcAnchors.anchorsIn(chunk);
        helper.assertTrue(anchors.size() == 1, "expected exactly one anchor in the chunk, saw " + anchors.size());
        return anchors.get(0);
    }

    // ------------------------------------------------------------------ anchors

    @GameTest(template = "npc_arena", timeoutTicks = 200)
    @SuppressWarnings("removal")
    public static void anchorRespawnsAfterOneDayWithNoPlayerNear(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 0, 4));
        LevelChunk chunk = level.getChunkAt(pos);
        ServerPlayer player = null;
        try {
            int spawned = NpcAnchors.place(level, pos, NpcEntityTypes.HOBBIT.get(), Optional.of(NpcRole.FARMER), 2, 4);
            helper.assertTrue(spawned == 2, "spawned " + spawned);
            SpawnAnchor anchor = onlyAnchor(helper, chunk);
            helper.assertTrue(anchor.npcs().size() == 2, "members " + anchor.npcs().size());
            for (Entity entity : members(level, anchor)) {
                NpcEntity npc = (NpcEntity) entity;
                helper.assertTrue(npc.getRole() == NpcRole.FARMER, "role " + npc.getRole());
                helper.assertTrue(npc.homePos().filter(pos::equals).isPresent(), "home " + npc.homePos());
                helper.assertTrue(npc.homeRadius() == 4, "radius " + npc.homeRadius());
            }

            // Kill one member: its death is recorded and the anchor is one short.
            Entity victim = level.getEntity(anchor.npcs().get(0));
            victim.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            long died = level.getGameTime();
            anchor = onlyAnchor(helper, chunk);
            helper.assertTrue(anchor.npcs().size() == 1, "one member left, saw " + anchor.npcs().size());
            helper.assertTrue(anchor.deaths().equals(List.of(died)), "death times " + anchor.deaths());

            NpcAnchors.check(level, chunk, died + NpcAnchors.CHECK_INTERVAL);
            helper.assertTrue(onlyAnchor(helper, chunk).npcs().size() == 1, "no respawn after 600 ticks");
            NpcAnchors.check(level, chunk, died + NpcAnchors.RESPAWN_DELAY - 1);
            helper.assertTrue(onlyAnchor(helper, chunk).npcs().size() == 1, "no respawn one tick early");

            // A player within 16 blocks blocks the respawn even once the day is up.
            player = helper.makeMockServerPlayerInLevel();
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 8.5);
            NpcAnchors.check(level, chunk, died + NpcAnchors.RESPAWN_DELAY);
            helper.assertTrue(onlyAnchor(helper, chunk).npcs().size() == 1, "no respawn with a player 8 blocks away");

            // Far away and out of sight: the replacement appears, once.
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 200.5);
            NpcAnchors.check(level, chunk, died + NpcAnchors.RESPAWN_DELAY);
            anchor = onlyAnchor(helper, chunk);
            helper.assertTrue(anchor.npcs().size() == 2, "respawned, members " + anchor.npcs().size());
            helper.assertTrue(anchor.deaths().isEmpty(), "death consumed " + anchor.deaths());
            helper.assertTrue(members(level, anchor).size() == 2, "both members exist in the level");
            NpcAnchors.check(level, chunk, died + NpcAnchors.RESPAWN_DELAY + NpcAnchors.CHECK_INTERVAL);
            helper.assertTrue(onlyAnchor(helper, chunk).npcs().size() == 2, "full anchor spawns nothing more");
        } finally {
            if (player != null) {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
            for (SpawnAnchor anchor : NpcAnchors.anchorsIn(chunk)) {
                members(level, anchor).forEach(Entity::discard);
            }
            NpcAnchors.clear(level, chunk);
        }
        helper.succeed();
    }

    @GameTest(template = "npc_arena", timeoutTicks = 200)
    public static void anchorAtMostOneSpawnPerCheckAndMissingSlotIsImmediate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 0, 4));
        LevelChunk chunk = level.getChunkAt(pos);
        try {
            NpcAnchors.place(level, pos, NpcEntityTypes.HOBBIT.get(), Optional.empty(), 3, 4);
            SpawnAnchor anchor = onlyAnchor(helper, chunk);
            // Two slots that never got an NPC (failed spawns): no death is recorded for them, so they are due at once.
            List<Entity> found = members(level, anchor);
            found.subList(1, 3).forEach(Entity::discard);
            chunk.setData(
                    NpcAttachments.NPC_SPAWN_ANCHORS.get(),
                    new NpcSpawnAnchors(List.of(anchor.withNpcs(List.of(found.get(0).getUUID())))));
            long now = level.getGameTime();
            NpcAnchors.check(level, chunk, now);
            anchor = onlyAnchor(helper, chunk);
            helper.assertTrue(anchor.npcs().size() == 2, "one spawn in one check, members " + anchor.npcs().size());
            helper.assertTrue(anchor.deaths().isEmpty(), "no deaths recorded " + anchor.deaths());
            NpcAnchors.check(level, chunk, now + NpcAnchors.CHECK_INTERVAL);
            helper.assertTrue(onlyAnchor(helper, chunk).npcs().size() == 3, "the next check fills the last slot");
        } finally {
            for (SpawnAnchor anchor : NpcAnchors.anchorsIn(chunk)) {
                members(level, anchor).forEach(Entity::discard);
            }
            NpcAnchors.clear(level, chunk);
        }
        helper.succeed();
    }

    @GameTest(template = "npc_arena", timeoutTicks = 200)
    public static void anchorAttachmentSurvivesChunkNbtRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 0, 4));
        LevelChunk chunk = level.getChunkAt(pos);
        try {
            NpcAnchors.place(level, pos, NpcEntityTypes.BREE_MAN.get(), Optional.of(NpcRole.INNKEEPER), 1, 6);
            SpawnAnchor original = onlyAnchor(helper, chunk);
            // Make the data non-trivial: a pending death as well as a member.
            SpawnAnchor withDeath = original.withDeaths(List.of(12345L));
            chunk.setData(NpcAttachments.NPC_SPAWN_ANCHORS.get(), new NpcSpawnAnchors(List.of(withDeath)));

            // Attachment codec round trip through NBT, which is what the chunk save uses.
            Tag written = NpcSpawnAnchors.CODEC
                    .encodeStart(NbtOps.INSTANCE, new NpcSpawnAnchors(List.of(withDeath)))
                    .getOrThrow();
            NpcSpawnAnchors decoded = NpcSpawnAnchors.CODEC.parse(NbtOps.INSTANCE, written).getOrThrow();
            helper.assertTrue(decoded.anchors().equals(List.of(withDeath)), "codec round trip " + decoded);

            // And the real chunk serializer, with the attachment hanging on the chunk.
            CompoundTag chunkTag = net.minecraft.world.level.chunk.storage.ChunkSerializer.write(level, chunk);
            helper.assertTrue(chunkTag.toString().contains("npc_spawn_anchors"), "attachment key in chunk NBT");
            var proto = net.minecraft.world.level.chunk.storage.ChunkSerializer.read(
                    level,
                    level.getPoiManager(),
                    new net.minecraft.world.level.chunk.storage.RegionStorageInfo(
                            "gametest", level.dimension(), "chunk"),
                    chunk.getPos(),
                    chunkTag);
            LevelChunk reloaded = ((net.minecraft.world.level.chunk.ImposterProtoChunk) proto).getWrapped();
            List<SpawnAnchor> back = NpcAnchors.anchorsIn(reloaded);
            helper.assertTrue(back.equals(List.of(withDeath)), "reloaded chunk anchors " + back);
        } finally {
            for (SpawnAnchor anchor : NpcAnchors.anchorsIn(chunk)) {
                members(level, anchor).forEach(Entity::discard);
            }
            NpcAnchors.clear(level, chunk);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ marker

    @GameTest(template = "empty")
    public static void markerParsesFieldsDefaultsAndRejectsBadInput(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        NpcMarker full = NpcMarker.parse("lotrrevival:npc hobbit role=farmer count=3 home=12", warnings::add)
                .orElseThrow();
        helper.assertTrue(full.type() == NpcEntityTypes.HOBBIT.get(), "type");
        helper.assertTrue(full.role().filter(NpcRole.FARMER::equals).isPresent(), "role " + full.role());
        helper.assertTrue(full.count() == 3, "count " + full.count());
        helper.assertTrue(full.homeRadius() == 12, "home " + full.homeRadius());

        NpcMarker defaults = NpcMarker.parse("lotrrevival:npc lotrrevival:bree_guard", warnings::add).orElseThrow();
        helper.assertTrue(defaults.type() == NpcEntityTypes.BREE_GUARD.get(), "namespaced type");
        helper.assertTrue(defaults.role().isEmpty(), "no role by default");
        helper.assertTrue(defaults.count() == 1, "default count " + defaults.count());
        helper.assertTrue(defaults.homeRadius() == 16, "default home " + defaults.homeRadius());

        NpcMarker reordered = NpcMarker.parse("lotrrevival:npc bree_man home=5 role=innkeeper", warnings::add)
                .orElseThrow();
        helper.assertTrue(reordered.homeRadius() == 5 && reordered.count() == 1, "options in any order");
        helper.assertTrue(warnings.isEmpty(), "valid markers do not warn: " + warnings);

        helper.assertTrue(NpcMarker.parse("lotrrevival:npc dragon", warnings::add).isEmpty(), "unknown type rejected");
        helper.assertTrue(warnings.size() == 1 && warnings.get(0).contains("dragon"), "unknown type warns " + warnings);
        helper.assertTrue(NpcMarker.parse("lotrrevival:npc minecraft:zombie", warnings::add).isEmpty(), "foreign type");
        helper.assertTrue(NpcMarker.parse("lotrrevival:npc hobbit role=wizard", warnings::add).isEmpty(), "bad role");
        helper.assertTrue(NpcMarker.parse("lotrrevival:npc hobbit count=0", warnings::add).isEmpty(), "zero count");
        helper.assertTrue(NpcMarker.parse("lotrrevival:npc hobbit home=abc", warnings::add).isEmpty(), "bad home");
        helper.assertTrue(NpcMarker.parse("lotrrevival:npc hobbit speed=2", warnings::add).isEmpty(), "unknown option");
        helper.assertTrue(NpcMarker.parse("lotrrevival:npc", warnings::add).isEmpty(), "missing type");
        helper.assertTrue(warnings.size() == 7, "every rejection warns, saw " + warnings.size());

        List<String> quiet = new ArrayList<>();
        helper.assertTrue(NpcMarker.parse("some_other_marker", quiet::add).isEmpty(), "foreign metadata ignored");
        helper.assertTrue(quiet.isEmpty(), "foreign metadata does not warn");
        helper.succeed();
    }

    // ------------------------------------------------------------------ routine

    @GameTest(template = "empty")
    public static void routineTableByPhaseAndKind(GameTestHelper helper) {
        helper.assertTrue(NpcRoutine.phaseAt(0) == NpcRoutine.Phase.DAY, "0 is day");
        helper.assertTrue(NpcRoutine.phaseAt(11999) == NpcRoutine.Phase.DAY, "11999 is day");
        helper.assertTrue(NpcRoutine.phaseAt(12000) == NpcRoutine.Phase.DUSK, "12000 is dusk");
        helper.assertTrue(NpcRoutine.phaseAt(12999) == NpcRoutine.Phase.DUSK, "12999 is dusk");
        helper.assertTrue(NpcRoutine.phaseAt(13000) == NpcRoutine.Phase.NIGHT, "13000 is night");
        helper.assertTrue(NpcRoutine.phaseAt(23999 + 24000) == NpcRoutine.Phase.NIGHT, "wraps to later days");
        helper.assertTrue(NpcRoutine.nextDusk(13000) == 36000, "next dusk after night " + NpcRoutine.nextDusk(13000));
        helper.assertTrue(NpcRoutine.nextDusk(5000) == 12000, "next dusk after dawn");

        BlockPos home = helper.absolutePos(new BlockPos(1, 1, 1));
        HobbitEntity villager = NpcEntityTypes.HOBBIT.get().create(helper.getLevel());
        villager.setRole(NpcRole.VILLAGER);
        villager.setHome(home, 0);
        var day = NpcRoutine.plan(villager, NpcRoutine.Phase.DAY);
        helper.assertTrue(day.mode() == NpcRoutine.Mode.AROUND_HOME && day.radius() == 16, "civilian day " + day);
        var dusk = NpcRoutine.plan(villager, NpcRoutine.Phase.DUSK);
        helper.assertTrue(dusk.mode() == NpcRoutine.Mode.AROUND_HOME && dusk.radius() == 4 && dusk.homePhase(), "dusk " + dusk);
        var night = NpcRoutine.plan(villager, NpcRoutine.Phase.NIGHT);
        helper.assertTrue(night.radius() == 4 && night.homePhase(), "night " + night);

        villager.setRole(NpcRole.INNKEEPER);
        for (NpcRoutine.Phase phase : NpcRoutine.Phase.values()) {
            helper.assertTrue(NpcRoutine.plan(villager, phase).radius() == 6, "innkeeper stays within 6 at " + phase);
        }

        StandardNpcEntity guard = NpcEntityTypes.BREE_GUARD.get().create(helper.getLevel());
        helper.assertTrue(NpcRoutine.plan(guard, NpcRoutine.Phase.NIGHT).isFree(), "homeless guard patrols freely");
        guard.setHome(home, 0);
        for (NpcRoutine.Phase phase : NpcRoutine.Phase.values()) {
            var plan = NpcRoutine.plan(guard, phase);
            helper.assertTrue(plan.radius() == 24 && !plan.homePhase(), "guard patrols within 24 at " + phase);
        }

        HobbitEntity wild = NpcEntityTypes.HOBBIT.get().create(helper.getLevel());
        wild.setRole(NpcRole.FARMER);
        helper.assertTrue(NpcRoutine.plan(wild, NpcRoutine.Phase.DAY).isFree(), "wild civilian wanders freely by day");
        helper.assertTrue(NpcRoutine.plan(wild, NpcRoutine.Phase.DUSK).isFree(), "and at dusk");
        var wildNight = NpcRoutine.plan(wild, NpcRoutine.Phase.NIGHT);
        helper.assertTrue(
                wildNight.mode() == NpcRoutine.Mode.AROUND_SPOT && wildNight.radius() == 8, "wild night " + wildNight);
        wild.setRole(NpcRole.TRAVELLER);
        helper.assertTrue(
                NpcRoutine.plan(wild, NpcRoutine.Phase.DAY).mode() == NpcRoutine.Mode.TRAVEL, "traveller travels");
        helper.succeed();
    }

    @GameTest(template = "npc_arena", timeoutTicks = 1100)
    public static void homeBoundHobbitWalksHomeAtDusk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long oldTime = level.getDayTime();
        BlockPos home = helper.absolutePos(new BlockPos(6, 0, 20));
        HobbitEntity hobbit = helper.spawn(NpcEntityTypes.HOBBIT.get(), new BlockPos(6, 0, 8));
        hobbit.setRole(NpcRole.VILLAGER);
        hobbit.setHome(home, 16);
        level.setDayTime(NpcRoutine.DUSK_START);
        Runnable restore = () -> level.setDayTime(oldTime);
        helper.assertTrue(!NpcRoutine.isNearHome(hobbit, 4), "starts far from home");
        helper.runAfterDelay(1050, restore);
        helper.succeedWhen(() -> {
            boolean near = NpcRoutine.isNearHome(hobbit, 4);
            if (near) {
                restore.run();
            }
            helper.assertTrue(near, "hobbit should be within 4 of home, at " + hobbit.position());
        });
    }

    @GameTest(template = "empty")
    public static void summonedShirriffHasNoHomeAndWandersFreely(GameTestHelper helper) {
        StandardNpcEntity shirriff = helper.spawn(NpcEntityTypes.HOBBIT_SHIRRIFF.get(), new BlockPos(2, 2, 2));
        shirriff.finalizeSpawn(
                helper.getLevel(),
                helper.getLevel().getCurrentDifficultyAt(shirriff.blockPosition()),
                MobSpawnType.COMMAND,
                null);
        helper.assertTrue(shirriff.homePos().isEmpty(), "summoned Shirriff has no home");
        helper.assertTrue(shirriff.homeRadius() == 0, "and no radius");
        for (NpcRoutine.Phase phase : NpcRoutine.Phase.values()) {
            helper.assertTrue(NpcRoutine.plan(shirriff, phase).isFree(), "free at " + phase);
        }
        helper.runAfterDelay(30, () -> {
            boolean routineRunning = shirriff.goalSelector.getAvailableGoals().stream()
                    .anyMatch(wrapped -> wrapped.isRunning() && wrapped.getGoal() instanceof RoutineGoal);
            helper.assertTrue(!routineRunning, "routine goal must not restrict a homeless Shirriff");
            helper.succeed();
        });
    }
}
