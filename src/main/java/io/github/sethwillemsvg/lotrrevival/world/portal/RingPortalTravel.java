package io.github.sethwillemsvg.lotrrevival.world.portal;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.ModDimensions;
import io.github.sethwillemsvg.lotrrevival.world.WorldHeights;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthLanding;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** What a ring portal does with the entities touching it: contact timing, the re-entry guard and the crossing. */
public final class RingPortalTravel {
    /** Contact ticks a survival player needs (4 seconds). */
    public static final int PLAYER_CONTACT_TICKS = 80;

    /** A stored twin this many blocks below the ground counts as buried and is rebuilt on the surface. */
    static final int BURIED_MARGIN = 4;

    private static final ReentryGuard GUARD = new ReentryGuard(ReentryGuard.DEFAULT_TICKS);

    /** Tests replace the crossing; the GameTest server has no Middle-earth level. */
    private static volatile BiConsumer<RingPortalEntity, Entity> override;

    private RingPortalTravel() {}

    public static void setOverride(BiConsumer<RingPortalEntity, Entity> replacement) {
        override = replacement;
    }

    public static ReentryGuard guard() {
        return GUARD;
    }

    /** Contact ticks {@code entity} needs: 80 for a survival player, 1 for creative or spectator players and others. */
    public static int requiredTicks(Entity entity) {
        if (entity instanceof Player player && !player.isCreative() && !player.isSpectator()) {
            return PLAYER_CONTACT_TICKS;
        }
        return 1;
    }

    /** Players, mobs and items pass; passengers, vehicles and decoration do not. */
    static boolean eligible(Entity entity) {
        if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity)) {
            return false;
        }
        return entity.isAlive() && !(entity instanceof ArmorStand) && !entity.isPassenger() && !entity.isVehicle();
    }

    /** One tick of a full-size portal. */
    static void tickContact(RingPortalEntity portal, ServerLevel level) {
        long now = level.getServer().getTickCount();
        Map<UUID, Integer> touching = new HashMap<>();
        Map<UUID, Entity> byId = new HashMap<>();
        for (Entity entity : level.getEntities(portal, portal.getBoundingBox(), RingPortalTravel::eligible)) {
            if (GUARD.blocks(entity.getUUID(), now)) {
                continue;
            }
            touching.put(entity.getUUID(), requiredTicks(entity));
            byId.put(entity.getUUID(), entity);
        }
        for (UUID id : portal.contact().tick(touching)) {
            send(portal, byId.get(id));
        }
        if (now % 200 == 0) {
            GUARD.purge(now);
        }
    }

    /** Sends {@code entity} through {@code portal} to the other realm. */
    static void send(RingPortalEntity portal, Entity entity) {
        BiConsumer<RingPortalEntity, Entity> replacement = override;
        if (replacement != null) {
            replacement.accept(portal, entity);
            return;
        }
        ServerLevel from = (ServerLevel) portal.level();
        MinecraftServer server = from.getServer();
        ServerLevel overworld = server.overworld();
        ServerLevel target;
        RingPortalPlan.Arrival arrival;
        if (from.dimension() == Level.OVERWORLD) {
            target = server.getLevel(ModDimensions.MIDDLE_EARTH);
            if (target == null) {
                return;
            }
            MiddleEarthLanding.Point landing = MiddleEarthLanding.load(server.getResourceManager());
            RingPortalData data = ensureTwin(target, landing);
            arrival = RingPortalPlan.middleEarthArrival(data.middleEarthPortal(), landing);
        } else {
            target = overworld;
            arrival = RingPortalPlan.overworldReturn(
                    RingPortals.data(overworld).overworldPortal(), overworld.getSharedSpawnPos());
        }
        int y = arrival.resolveHeight()
                ? WorldHeights.surface(target, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, arrival.x(), arrival.z())
                : arrival.y();
        teleport(entity, target, new Vec3(arrival.x() + 0.5, y, arrival.z() + 0.5), server.getTickCount());
    }

    private static void teleport(Entity entity, ServerLevel target, Vec3 pos, long now) {
        GUARD.mark(entity.getUUID(), now);
        // Arrivals stand south of the portal and look north at it.
        float yaw = 180.0f;
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(target, pos.x, pos.y, pos.z, yaw, 0.0f);
        } else {
            entity.changeDimension(new DimensionTransition(
                    target, pos, Vec3.ZERO, yaw, 0.0f, DimensionTransition.DO_NOTHING));
        }
    }

    /**
     * Makes sure the twin portal stands in Middle-earth: builds it (full size at once) on the first arrival, or
     * again at the stored position when it is missing.
     */
    static RingPortalData ensureTwin(ServerLevel middleEarth, MiddleEarthLanding.Point landing) {
        RingPortalData data = RingPortals.data(middleEarth);
        Optional<BlockPos> stored = data.middleEarthPortal();
        BlockPos column = RingPortalPlan.twinColumn(stored, landing);
        // Loading the chunk (and finding its height) before looking for the entity.
        int ground = WorldHeights.surface(middleEarth, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        if (stored.isPresent() && stored.get().getY() < ground - BURIED_MARGIN) {
            // Built underground by the old unloaded-chunk height bug: remove it and build a new one on the surface.
            AABB old = new AABB(stored.get()).inflate(4.0, 8.0, 4.0);
            middleEarth.getEntitiesOfClass(RingPortalEntity.class, old).forEach(Entity::discard);
            LOTRRevival.LOGGER.warn("Twin ring portal at {} was buried (ground {}); rebuilding it", stored.get(), ground);
            data = data.withoutTwin();
            RingPortals.setData(middleEarth, data);
            stored = Optional.empty();
        }
        double bottom = stored.isPresent()
                ? stored.get().getY() - RingPortalEntity.BOTTOM_ABOVE_BASE
                : ground - 1 + RingPortalEntity.BOTTOM_ABOVE_BASE;
        BlockPos centre = new BlockPos(column.getX(), (int) Math.round(bottom + RingPortalEntity.BOTTOM_ABOVE_BASE), column.getZ());

        AABB search = new AABB(centre).inflate(4.0, 8.0, 4.0);
        List<RingPortalEntity> found = middleEarth.getEntitiesOfClass(RingPortalEntity.class, search);
        if (!RingPortalPlan.needsTwin(stored, !found.isEmpty())) {
            return data;
        }
        RingPortalEntity twin;
        if (found.isEmpty()) {
            twin = RingPortalTypes.RING_PORTAL.get().create(middleEarth);
            twin.setPos(column.getX() + 0.5, bottom, column.getZ() + 0.5);
            twin.setGrowth(RingPortalEntity.FULL_GROWTH);
            middleEarth.addFreshEntity(twin);
            LOTRRevival.LOGGER.info("Built the twin ring portal at {}", centre);
        } else {
            twin = found.get(0);
        }
        RingPortalData updated = data.withTwin(centre, twin.getUUID());
        RingPortals.setData(middleEarth, updated);
        return updated;
    }

    /**
     * A second twin can appear when the first one's chunk entities were not loaded yet on arrival. The portal that
     * is not the stored one removes itself.
     */
    static void discardIfStaleTwin(RingPortalEntity portal, ServerLevel level) {
        Optional<UUID> stored = RingPortals.data(level).middleEarthEntity();
        if (stored.isPresent() && !stored.get().equals(portal.getUUID()) && level.getEntity(stored.get()) != null) {
            portal.discard();
        }
    }
}
