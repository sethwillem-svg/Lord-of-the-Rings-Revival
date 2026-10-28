package io.github.sethwillemsvg.lotrrevival.npc.spawn;

import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.core.api.LandProtection;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.npc.NpcConfig;
import io.github.sethwillemsvg.lotrrevival.npc.data.NpcLoadoutData;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The wild NPC spawner: its own system, not a vanilla mob category. Runs only in the Middle-earth dimension. The
 * rules and numbers are in {@link NpcSpawnRules}; this class gathers the world facts and spawns.
 */
public final class NpcSpawner {
    /** Wild NPCs are recounted at most this often; spawns in between adjust the count directly. */
    private static final long RECOUNT_TICKS = 20L;

    private static ServerLevel countedLevel;
    private static long countedAt;
    private static int wildCount;

    private NpcSpawner() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ModDimensions.MIDDLE_EARTH) {
            return;
        }
        if (!NpcConfig.get(NpcConfig.WILD_SPAWNING_ENABLED) || NpcSpawnData.isEmpty()) {
            return;
        }
        int interval = NpcConfig.get(NpcConfig.SPAWN_INTERVAL);
        if (level.getGameTime() % interval != 0) {
            return;
        }
        runPass(level, interval);
    }

    /** One spawner pass: {@code attemptsPerChunk} rounds over every eligible chunk, stopping at the cap. */
    static void runPass(ServerLevel level, int attemptsPerChunk) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.isAlive()) {
                players.add(player);
            }
        }
        if (players.isEmpty()) {
            return;
        }
        List<ChunkPos> chunks = eligibleChunks(level, players);
        int cap = NpcSpawnRules.cap(chunks.size(), players.size(), NpcConfig.get(NpcConfig.SPAWN_DAMPING));
        if (currentWildCount(level) >= cap) {
            return;
        }
        RandomSource random = level.random;
        for (ChunkPos chunk : chunks) {
            for (int round = 0; round < attemptsPerChunk; round++) {
                if (wildCount >= cap) {
                    return;
                }
                attemptChunk(level, chunk, players, cap, random);
            }
        }
    }

    /** The 15 x 15 chunk squares around each player, overlaps counted once, loaded and ticking chunks only. */
    static List<ChunkPos> eligibleChunks(ServerLevel level, List<ServerPlayer> players) {
        Set<Long> seen = new HashSet<>();
        List<ChunkPos> result = new ArrayList<>();
        int r = NpcSpawnRules.AREA_RADIUS_CHUNKS;
        for (ServerPlayer player : players) {
            ChunkPos center = player.chunkPosition();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                    if (seen.add(pos.toLong())
                            && level.getChunkSource().getChunkNow(pos.x, pos.z) != null
                            && level.isNaturalSpawningAllowed(pos)) {
                        result.add(pos);
                    }
                }
            }
        }
        return result;
    }

    private static int currentWildCount(ServerLevel level) {
        if (countedLevel != level || level.getGameTime() - countedAt >= RECOUNT_TICKS) {
            countedLevel = level;
            countedAt = level.getGameTime();
            wildCount = level.getEntities(
                            net.minecraft.world.level.entity.EntityTypeTest.forClass(NpcEntity.class), NpcEntity::isWild)
                    .size();
        }
        return wildCount;
    }

    private static void attemptChunk(
            ServerLevel level, ChunkPos chunk, List<ServerPlayer> players, int cap, RandomSource random) {
        int startX = chunk.getMinBlockX() + random.nextInt(16);
        int startZ = chunk.getMinBlockZ() + random.nextInt(16);
        for (int attempt = 0; attempt < NpcSpawnRules.GROUP_ATTEMPTS; attempt++) {
            int spread = NpcSpawnRules.GROUP_SPREAD;
            int x = startX + random.nextInt(spread * 2 + 1) - spread;
            int z = startZ + random.nextInt(spread * 2 + 1) - spread;
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos feet = surfaceAbove(level, x, z);
            Optional<ResourceLocation> biome = level.getBiome(feet).unwrapKey().map(key -> key.location());
            if (biome.isEmpty()) {
                continue;
            }
            Optional<NpcSpawnData.Entry> picked = NpcSpawnData.pick(NpcSpawnData.forBiome(biome.get()), random);
            if (picked.isEmpty() || !picked.get().rollChance(random)) {
                continue;
            }
            spawnGroup(level, picked.get(), feet, players, cap, random);
        }
    }

    private static BlockPos surfaceAbove(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
    }

    private static void spawnGroup(
            ServerLevel level,
            NpcSpawnData.Entry entry,
            BlockPos origin,
            List<ServerPlayer> players,
            int cap,
            RandomSource random) {
        int size = entry.rollGroupSize(random);
        for (int member = 0; member < size && wildCount < cap; member++) {
            int x = origin.getX() + (member == 0 ? 0 : random.nextInt(5) - 2);
            int z = origin.getZ() + (member == 0 ? 0 : random.nextInt(5) - 2);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos feet = surfaceAbove(level, x, z);
            if (trySpawn(level, entry, feet, players, random)) {
                wildCount++;
            }
        }
    }

    /** Tries one spawn at the spot; false if any rule, including banner protection, rejects it. */
    public static boolean trySpawn(
            ServerLevel level, NpcSpawnData.Entry entry, BlockPos feet, List<ServerPlayer> players, RandomSource random) {
        if (!(entry.type().create(level) instanceof NpcEntity npc)) {
            return false;
        }
        // An enemy-faction banner covering the spot keeps this faction's NPCs from spawning there.
        if (LandProtection.blocksFactionSpawn(level, feet, npc.faction())) {
            return false;
        }
        if (!NpcSpawnRules.timeAllowed(npc.npcClass(), level.getDayTime())) {
            return false;
        }
        BlockPos ground = feet.below();
        BlockState groundState = level.getBlockState(ground);
        if (npc.race() == io.github.sethwillemsvg.lotrrevival.npc.race.NpcRace.HOBBIT
                && !NpcSpawnRules.hobbitGroundOk(groundState.getBlock(), ground.getY(), level.getSeaLevel())) {
            return false;
        }
        Vec3 pos = Vec3.atBottomCenterOf(feet);
        if (NpcSpawnRules.check(spotAt(level, entry.type(), feet, groundState, pos, players, false))
                != NpcSpawnRules.Reject.NONE) {
            return false;
        }
        // The structure lookup is the costly part of the spot, so it only runs for spots that passed the rest.
        if (insideStructure(level, feet)) {
            return false;
        }
        Set<NpcRole> allowed = allowedRoles(level, npc, entry, feet);
        if (npc.isCivilian() && allowed.isEmpty()) {
            return false;
        }
        npc.moveTo(pos.x, pos.y, pos.z, random.nextFloat() * 360.0f, 0.0f);
        npc.setWild(true);
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), MobSpawnType.NATURAL, null);
        if (npc.isCivilian() && !allowed.contains(npc.getRole())) {
            reroll(npc, allowed, random);
        }
        if (!level.noCollision(npc)) {
            return false;
        }
        level.addFreshEntityWithPassengers(npc);
        return true;
    }

    private static NpcSpawnRules.Spot spotAt(
            ServerLevel level,
            EntityType<?> type,
            BlockPos feet,
            BlockState groundState,
            Vec3 pos,
            List<ServerPlayer> players,
            boolean inStructure) {
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer player : players) {
            nearest = Math.min(nearest, player.distanceToSqr(pos));
        }
        BlockPos spawn = level.getSharedSpawnPos();
        int light = level.getBrightness(LightLayer.SKY, feet) + level.getBrightness(LightLayer.BLOCK, feet);
        boolean liquid = !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty();
        return new NpcSpawnRules.Spot(
                NpcSpawnRules.isSpawnableSurface(groundState.getBlock()),
                liquid,
                level.canSeeSky(feet),
                light,
                nearest,
                Vec3.atCenterOf(spawn).distanceToSqr(pos),
                inStructure,
                level.noCollision(type.getSpawnAABB(pos.x, pos.y, pos.z)));
    }

    private static boolean insideStructure(ServerLevel level, BlockPos pos) {
        for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(pos), s -> true)) {
            if (start.getBoundingBox().isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Roles a civilian may spawn with: the type's own rules, the entry's role list, and the traveller road rule. */
    private static Set<NpcRole> allowedRoles(ServerLevel level, NpcEntity npc, NpcSpawnData.Entry entry, BlockPos feet) {
        Set<NpcRole> allowed = new HashSet<>();
        if (!npc.isCivilian()) {
            return allowed;
        }
        boolean roadTagHasMembers = roadTagHasMembers();
        Boolean nearRoad = null;
        for (NpcRole role : NpcRole.values()) {
            if (role.spawnWeight() <= 0 || !npc.canSpawnWithRole(role)) {
                continue;
            }
            if (!entry.roles().isEmpty() && !entry.roles().contains(role)) {
                continue;
            }
            if (role == NpcRole.TRAVELLER && roadTagHasMembers) {
                if (nearRoad == null) {
                    nearRoad = nearRoad(level, feet);
                }
                if (!NpcSpawnRules.travellerAllowed(true, nearRoad)) {
                    continue;
                }
            }
            allowed.add(role);
        }
        return allowed;
    }

    static boolean roadTagHasMembers() {
        return BuiltInRegistries.BLOCK
                .getTag(ModTags.Blocks.ROAD_SURFACES)
                .map(set -> set.size() > 0)
                .orElse(false);
    }

    static boolean nearRoad(ServerLevel level, BlockPos pos) {
        int r = NpcSpawnRules.TRAVELLER_ROAD_RADIUS;
        for (BlockPos check : BlockPos.betweenClosed(pos.offset(-r, -4, -r), pos.offset(r, 4, r))) {
            if (level.getBlockState(check).is(ModTags.Blocks.ROAD_SURFACES)) {
                return true;
            }
        }
        return false;
    }

    /** Swaps a rolled role the spot does not allow for an allowed one by weight, then redoes the loadout. */
    private static void reroll(NpcEntity npc, Set<NpcRole> allowed, RandomSource random) {
        int total = allowed.stream().mapToInt(NpcRole::spawnWeight).sum();
        int pick = random.nextInt(total);
        NpcRole chosen = NpcRole.VILLAGER;
        for (NpcRole role : allowed) {
            pick -= role.spawnWeight();
            if (pick < 0) {
                chosen = role;
                break;
            }
        }
        npc.setRole(chosen);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            npc.setItemSlot(slot, ItemStack.EMPTY);
        }
        NpcLoadoutData.apply(npc, random);
    }
}
