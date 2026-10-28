package io.github.sethwillemsvg.lotrrevival.world.travel;

import io.github.sethwillemsvg.lotrrevival.core.api.TravelStatus;
import io.github.sethwillemsvg.lotrrevival.core.event.FastTravelEvent;
import io.github.sethwillemsvg.lotrrevival.world.WorldConfig;
import io.github.sethwillemsvg.lotrrevival.world.network.TravelStatusPayload;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.TravelData;
import io.github.sethwillemsvg.lotrrevival.world.waypoint.WaypointRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Fast travel: requests, the per-player countdown and the arrival (specs/world/waypoints-travel.md, "Fast travel").
 * Server side only; everything runs on the server thread.
 */
public final class FastTravel {
    /** The countdown stops when the player is further than this from where it started, on any axis. */
    public static final double MOVE_TOLERANCE = 0.05;

    /** Companions are placed within this many blocks (horizontally) of the traveller. */
    public static final int PLACEMENT_RADIUS = 3;

    public static final int PLACEMENT_TRIES = 8;

    private static final Map<UUID, Countdown> COUNTDOWNS = new HashMap<>();
    private static final List<Consumer<ServerPlayer>> RESYNC_LISTENERS = new CopyOnWriteArrayList<>();

    private FastTravel() {}

    /** The result of a request: started, or refused with the reason that was also shown to the player. */
    public record Result(boolean started, Optional<Component> reason) {}

    private static final class Countdown {
        final WaypointRef ref;
        final Component name;
        final ServerLevel level;
        final Vec3 start;
        @Nullable
        final Entity vehicle;
        int ticksLeft;

        Countdown(WaypointRef ref, Component name, ServerLevel level, Vec3 start, @Nullable Entity vehicle, int ticks) {
            this.ref = ref;
            this.name = name;
            this.level = level;
            this.start = start;
            this.vehicle = vehicle;
            this.ticksLeft = ticks;
        }
    }

    /**
     * Registers a hook that runs after every completed trip, once the travel data has changed. The sync ticket uses it
     * to send the full travel sync. Hooks run on the server thread.
     */
    public static void addResyncListener(Consumer<ServerPlayer> listener) {
        RESYNC_LISTENERS.add(listener);
    }

    public static void removeResyncListener(Consumer<ServerPlayer> listener) {
        RESYNC_LISTENERS.remove(listener);
    }

    public static boolean isCountingDown(ServerPlayer player) {
        return COUNTDOWNS.containsKey(player.getUUID());
    }

    /** Ticks left on this player's countdown, or -1 when none runs. */
    public static int ticksLeft(ServerPlayer player) {
        Countdown countdown = COUNTDOWNS.get(player.getUUID());
        return countdown == null ? -1 : countdown.ticksLeft;
    }

    /** Handles a travel request: every refusal check, then starts a countdown (replacing a running one). */
    public static Result request(ServerPlayer player, WaypointRef ref) {
        TravelChecks.Outcome outcome = TravelChecks.check(player, ref);
        if (outcome.refusal().isPresent()) {
            Component reason = outcome.refusal().get();
            notify(player, reason);
            sendStatus(player, TravelStatus.State.CANCELLED, ref.wire(), 0, Optional.of(reason));
            return new Result(false, outcome.refusal());
        }
        TravelTarget target = outcome.target().orElseThrow();
        if (COUNTDOWNS.containsKey(player.getUUID())) {
            cancel(player, Component.translatable("message.lotrrevival.travel.cancelled"));
        }
        int ticks = WorldConfig.get(WorldConfig.TRAVEL_COUNTDOWN_TICKS);
        COUNTDOWNS.put(
                player.getUUID(),
                new Countdown(ref, target.name(), player.serverLevel(), player.position(), player.getVehicle(), ticks));
        notify(player, Component.translatable("message.lotrrevival.travel.start", target.name(), (ticks + 19) / 20));
        sendStatus(player, TravelStatus.State.COUNTDOWN, ref.wire(), ticks, Optional.empty());
        return new Result(true, Optional.empty());
    }

    /** Cancels a running countdown, printing "Fast travel cancelled." No time is spent. */
    public static boolean cancel(ServerPlayer player) {
        return cancel(player, Component.translatable("message.lotrrevival.travel.cancelled"));
    }

    private static boolean cancel(ServerPlayer player, Component reason) {
        Countdown countdown = COUNTDOWNS.remove(player.getUUID());
        if (countdown == null) {
            return false;
        }
        notify(player, reason);
        sendStatus(player, TravelStatus.State.CANCELLED, countdown.ref.wire(), 0, Optional.of(reason));
        return true;
    }

    /** Forgets a countdown without telling the player (they logged out). */
    static void forget(ServerPlayer player) {
        COUNTDOWNS.remove(player.getUUID());
    }

    /** One server tick of this player's countdown, if any. Called from the player tick event. */
    public static void tick(ServerPlayer player) {
        Countdown countdown = COUNTDOWNS.get(player.getUUID());
        if (countdown == null) {
            return;
        }
        if (!player.isAlive()
                || player.serverLevel() != countdown.level
                || player.isSleeping()
                || player.getVehicle() != countdown.vehicle
                || moved(player.position(), countdown.start)) {
            cancel(player);
            return;
        }
        countdown.ticksLeft--;
        if (countdown.ticksLeft > 0) {
            if (countdown.ticksLeft % 20 == 0) {
                int seconds = countdown.ticksLeft / 20;
                if (seconds <= 5) {
                    notify(player, Component.translatable("message.lotrrevival.travel.tick", seconds));
                }
                sendStatus(player, TravelStatus.State.COUNTDOWN, countdown.ref.wire(), countdown.ticksLeft, Optional.empty());
            }
            return;
        }
        COUNTDOWNS.remove(player.getUUID());
        complete(player, countdown);
    }

    static boolean moved(Vec3 now, Vec3 start) {
        return Math.abs(now.x - start.x) > MOVE_TOLERANCE
                || Math.abs(now.y - start.y) > MOVE_TOLERANCE
                || Math.abs(now.z - start.z) > MOVE_TOLERANCE;
    }

    private static void complete(ServerPlayer player, Countdown countdown) {
        TravelChecks.Outcome outcome = TravelChecks.check(player, countdown.ref);
        if (outcome.refusal().isPresent()) {
            Component reason = outcome.refusal().get();
            notify(player, reason);
            sendStatus(player, TravelStatus.State.CANCELLED, countdown.ref.wire(), 0, Optional.of(reason));
            return;
        }
        arrive(player, outcome.target().orElseThrow());
    }

    /** Moves the player and companions to the target and records the trip. All checks have passed. */
    static void arrive(ServerPlayer player, TravelTarget target) {
        ServerLevel level = player.serverLevel();
        Vec3 from = player.position();
        Vec3 spot = ArrivalFinder.find(level, target);

        List<Entity> companions = Companions.gather(player);
        List<Entity> movers = Companions.movers(player, companions);
        Entity vehicle = player.getVehicle();
        Entity mount = vehicle == null ? null : player.getRootVehicle();

        // The mount is first so a companion never lands inside it; then the player; then everything else.
        if (mount != null) {
            place(mount, spot);
            repositionPassengers(mount);
        }
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        if (player instanceof FakePlayer) {
            // Test players have no real connection to carry the teleport.
            player.stopRiding();
            player.setPos(spot.x, spot.y, spot.z);
        } else {
            player.teleportTo(level, spot.x, spot.y, spot.z, yRot, xRot);
        }
        player.resetFallDistance();
        if (vehicle != null) {
            player.startRiding(vehicle, true);
        }
        RandomSource random = player.getRandom();
        for (Entity companion : movers) {
            if (companion == mount) {
                continue;
            }
            Vec3 place = freeSpotNear(level, companion, spot, random);
            if (companion.getVehicle() != null) {
                companion.stopRiding();
            }
            place(companion, place);
            repositionPassengers(companion);
        }

        TravelData data = TravelData.get(player);
        TravelData updated = target.fixed().isPresent()
                ? data.withUse(((WaypointRef.Fixed) target.ref()).id())
                : withCustomUse(data, ((WaypointRef.Custom) target.ref()).id());
        TravelData.set(player, updated.withClock(0).withLast(target.ref()));

        double horizontal = Math.hypot(spot.x - from.x, spot.z - from.z);
        NeoForge.EVENT_BUS.post(
                new FastTravelEvent(player, target.ref().wire(), from, spot, horizontal, target.custom()));
        notify(player, Component.translatable("message.lotrrevival.travel.arrived", target.name()));
        sendStatus(player, TravelStatus.State.ARRIVED, target.ref().wire(), 0, Optional.empty());
        for (Consumer<ServerPlayer> listener : RESYNC_LISTENERS) {
            listener.accept(player);
        }
    }

    private static TravelData withCustomUse(TravelData data, int id) {
        List<TravelData.Custom> list = new ArrayList<>(data.custom());
        for (int i = 0; i < list.size(); i++) {
            TravelData.Custom custom = list.get(i);
            if (custom.id() == id) {
                list.set(i, new TravelData.Custom(
                        id, custom.name(), custom.pos(), Math.min(TravelData.MAX_USES, custom.uses() + 1)));
            }
        }
        return new TravelData(
                data.regions(), data.uses(), list, data.nextCustomId(), data.clock(), data.last(), data.lastLogout());
    }

    private static void place(Entity entity, Vec3 pos) {
        entity.moveTo(pos.x, pos.y, pos.z, entity.getYRot(), entity.getXRot());
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    private static void repositionPassengers(Entity vehicle) {
        for (Entity passenger : vehicle.getPassengers()) {
            vehicle.positionRider(passenger);
            passenger.resetFallDistance();
            repositionPassengers(passenger);
        }
    }

    /** A random free spot within the placement radius (up to the configured tries), else the traveller's own spot. */
    static Vec3 freeSpotNear(ServerLevel level, Entity entity, Vec3 centre, RandomSource random) {
        for (int attempt = 0; attempt < PLACEMENT_TRIES; attempt++) {
            double x = centre.x + random.nextIntBetweenInclusive(-PLACEMENT_RADIUS, PLACEMENT_RADIUS);
            double z = centre.z + random.nextIntBetweenInclusive(-PLACEMENT_RADIUS, PLACEMENT_RADIUS);
            for (int dy : new int[] {0, 1, -1, 2, -2}) {
                Vec3 candidate = new Vec3(x, centre.y + dy, z);
                if (isFree(level, entity, candidate)) {
                    return candidate;
                }
            }
        }
        return centre;
    }

    private static boolean isFree(ServerLevel level, Entity entity, Vec3 pos) {
        AABB box = entity.getBoundingBox().move(pos.x - entity.getX(), pos.y - entity.getY(), pos.z - entity.getZ());
        return level.noCollision(entity, box) && !level.noCollision(entity, box.move(0, -0.5, 0));
    }

    private static void notify(ServerPlayer player, Component message) {
        if (!(player instanceof FakePlayer)) {
            player.sendSystemMessage(message);
        }
    }

    private static void sendStatus(
            ServerPlayer player, TravelStatus.State state, String ref, int ticksLeft, Optional<Component> reason) {
        if (!player.isFakePlayer()
                && player.connection != null
                && player.connection.hasChannel(TravelStatusPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new TravelStatusPayload(state, ref, ticksLeft, reason));
        }
    }
}
