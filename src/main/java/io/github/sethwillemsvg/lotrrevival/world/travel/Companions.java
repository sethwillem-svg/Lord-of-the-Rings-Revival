package io.github.sethwillemsvg.lotrrevival.world.travel;

import io.github.sethwillemsvg.lotrrevival.core.api.TravelCompanion;
import io.github.sethwillemsvg.lotrrevival.world.WorldConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;

/** Who comes along on a fast travel. Only loaded entities in the player's level are considered. */
public final class Companions {
    private Companions() {}

    /**
     * Companions within the configured range (3D): entities answering {@link TravelCompanion} true, owned tamed
     * animals not ordered to sit, mobs on a lead held by the player. The player and the player's own vehicle chain are
     * excluded (the caller moves those). Companions that ride another entity are returned too; use {@link #movers}.
     */
    public static List<Entity> gather(ServerPlayer player) {
        double range = WorldConfig.get(WorldConfig.TRAVEL_COMPANION_RANGE);
        AABB area = player.getBoundingBox().inflate(range);
        List<Entity> found = new ArrayList<>();
        for (Entity entity : player.serverLevel().getEntitiesOfClass(Entity.class, area)) {
            if (entity == player || !entity.isAlive() || entity.isRemoved()) {
                continue;
            }
            if (entity.distanceToSqr(player) > range * range) {
                continue;
            }
            if (travelsWith(entity, player)) {
                found.add(entity);
            }
        }
        return found;
    }

    /** Whether one entity qualifies as a companion of this player (range is checked by the caller). */
    public static boolean travelsWith(Entity entity, ServerPlayer player) {
        if (entity instanceof TravelCompanion companion) {
            // A hired unit decides for itself: it may be ownable, but only comes when it follows.
            return companion.travelsWith(player);
        }
        if (entity instanceof TamableAnimal pet && pet.isTame()
                && player.getUUID().equals(pet.getOwnerUUID())
                && !pet.isOrderedToSit()) {
            return true;
        }
        return entity instanceof Mob mob && mob.getLeashHolder() == player;
    }

    /**
     * The entities that actually need moving. A companion riding the player's vehicle chain or another companion
     * travels with that vehicle; one riding an unrelated vehicle is moved on its own (it dismounts).
     */
    public static List<Entity> movers(ServerPlayer player, List<Entity> companions) {
        Entity playerRoot = player.getRootVehicle();
        Set<Entity> roots = new HashSet<>();
        roots.add(playerRoot);
        for (Entity companion : companions) {
            if (companion.getVehicle() == null) {
                roots.add(companion);
            }
        }
        List<Entity> movers = new ArrayList<>();
        for (Entity companion : companions) {
            if (companion == playerRoot) {
                continue;
            }
            if (companion.getVehicle() != null && roots.contains(companion.getRootVehicle())) {
                continue;
            }
            movers.add(companion);
        }
        return movers;
    }
}
