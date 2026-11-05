package io.github.sethwillemsvg.lotrrevival.nodemap.travel;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Who comes along on a trip (spec Fast travel 5): a living mount (the player arrives in the saddle), mobs on the player's
 * lead within 16 blocks, tamed pets not sitting within 32 blocks. Non-living vehicles and other players stay behind.
 * Hired units are moved by the hiring system on {@code NodeTravelEvent.Arrive}.
 */
public final class CompanionMover {
    public static final double LEASH_RANGE = 16;
    public static final double PET_RANGE = 32;

    private CompanionMover() {}

    public record Party(@Nullable LivingEntity mount, @Nullable Entity leftVehicle, List<Mob> leashed, List<TamableAnimal> pets) {
        public String describe() {
            return "mount=" + (mount == null ? "none" : LotrLog.id(mount.getUUID())) + " left_vehicle="
                    + (leftVehicle == null ? "none" : leftVehicle.getType().toShortString()) + " leashed=" + leashed.size()
                    + " pets=" + pets.size();
        }
    }

    /** Gathers the party from loaded entities around the player (no chunks are loaded to find them). */
    public static Party gather(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        LivingEntity mount = null;
        Entity left = null;
        Entity v = p.getVehicle();
        if (v instanceof LivingEntity le) mount = le;
        else if (v != null) left = v;
        List<Mob> leashed = new ArrayList<>(level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(LEASH_RANGE),
                m -> m.isAlive() && m.getLeashHolder() == p && m != p.getVehicle()));
        List<TamableAnimal> pets = new ArrayList<>(level.getEntitiesOfClass(TamableAnimal.class, p.getBoundingBox().inflate(PET_RANGE),
                a -> a.isAlive() && a.isTame() && p.getUUID().equals(a.getOwnerUUID()) && !a.isOrderedToSit() && !a.isPassenger()
                        && a != p.getVehicle() && !leashed.contains(a) && a.distanceToSqr(p) <= PET_RANGE * PET_RANGE));
        return new Party(mount, left, leashed, pets);
    }

    /**
     * Moves the player and the party to {@code to} in the same level. The player is dismounted first; a living mount is
     * moved and the player put back in the saddle; other players riding it are left behind. Returns how many companions
     * could not be placed (they stay where they were).
     */
    public static int move(ServerPlayer p, Party party, Vec3 to) {
        ServerLevel level = p.serverLevel();
        float yRot = p.getYRot(), xRot = p.getXRot();
        if (p.isPassenger()) p.stopRiding();
        ticket(level, to, p);
        int failed = 0;

        if (party.mount() != null) {
            LivingEntity m = party.mount();
            for (Entity rider : List.copyOf(m.getPassengers())) {
                if (rider instanceof Player && rider != p) rider.stopRiding();   // other players never come along
            }
            m.teleportTo(level, to.x, to.y, to.z, Set.of(), m.getYRot(), m.getXRot());
            m.resetFallDistance();
        }
        p.teleportTo(level, to.x, to.y, to.z, Set.of(), yRot, xRot);
        p.resetFallDistance();
        if (party.mount() != null && !p.startRiding(party.mount(), true)) {
            LotrLog.warn("travel", "could not put player={} back on mount={} at {}", LotrLog.id(p.getUUID()),
                    LotrLog.id(party.mount().getUUID()), fmt(to));
        }

        RandomSource rnd = p.getRandom();
        List<Mob> rest = new ArrayList<>(party.leashed());
        rest.addAll(party.pets());
        for (Mob mob : rest) {
            Vec3 spot = freeSpot(level, mob, to, rnd);
            if (spot == null) {
                failed++;
                LotrLog.warn("travel", "companion {} ({}) could not be placed near {}; left behind", LotrLog.id(mob.getUUID()),
                        mob.getType().toShortString(), fmt(to));
                continue;
            }
            mob.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), mob.getYRot(), mob.getXRot());
            mob.resetFallDistance();
            mob.getNavigation().stop();
        }
        return failed;
    }

    /** A random free spot within 3 blocks horizontally of {@code at}, up to 8 tries; else the arrival spot itself. */
    @Nullable
    private static Vec3 freeSpot(ServerLevel level, Entity e, Vec3 at, RandomSource rnd) {
        for (int i = 0; i < 8; i++) {
            Vec3 c = at.add(rnd.nextInt(7) - 3, 0, rnd.nextInt(7) - 3);
            BlockPos feet = BlockPos.containing(c);
            for (int dy : new int[] {0, 1, -1}) {
                BlockPos f = feet.above(dy);
                if (ArrivalFinder.standable(level, f) && level.noCollision(e, e.getType().getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(f)))) {
                    return Vec3.atBottomCenterOf(f);
                }
            }
        }
        return at;
    }

    private static void ticket(ServerLevel level, Vec3 to, Entity e) {
        level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, new ChunkPos(BlockPos.containing(to)), 1, e.getId());
    }

    public static String fmt(Vec3 v) {
        return Math.round(v.x) + "," + Math.round(v.y) + "," + Math.round(v.z);
    }
}
