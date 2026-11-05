package io.github.sethwillemsvg.lotrrevival.nodemap.travel;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.Msg;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapContext;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapStats;
import io.github.sethwillemsvg.lotrrevival.nodemap.api.NodeTravelEvent;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapAttachment;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapData;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Fast travel (spec Fast travel 1-4): a request runs every check, then a charge of {@code charge_ticks} during which the
 * player must stand still; at the end every check runs again, {@link NodeTravelEvent.Depart} may veto, the arrival spot
 * is searched, the party is moved, and the cost applied. One charge per player. Nothing is spent until the trip happens.
 */
public final class TravelService {
    public static final double STILL = 0.05;

    /** Status updates for the HUD (live: travel_status packets; tests: nothing). */
    @FunctionalInterface
    public interface StatusSink {
        void send(ServerPlayer p, String state, @Nullable NodeRef target, int ticksLeft, int total, Component reason);

        StatusSink NONE = (p, s, t, l, tot, r) -> {};
    }

    public static final class Charge {
        public final NodeRef target;
        public final Component targetName;
        public final Vec3 start;
        @Nullable public final Vec3 mountStart;
        @Nullable public final Entity mount;
        public final ResourceKey<Level> dimension;
        public final int total;
        public int left;

        Charge(NodeRef target, Component targetName, ServerPlayer p, int total) {
            this.target = target;
            this.targetName = targetName;
            this.start = p.position();
            this.mount = p.getVehicle();
            this.mountStart = mount == null ? null : mount.position();
            this.dimension = p.level().dimension();
            this.total = total;
            this.left = total;
        }
    }

    private final NodeMapContext ctx;
    private final Map<UUID, Charge> charges = new ConcurrentHashMap<>();
    public StatusSink status = StatusSink.NONE;

    public TravelService(NodeMapContext ctx) {
        this.ctx = ctx;
    }

    @Nullable
    public Charge charge(ServerPlayer p) {
        return charges.get(p.getUUID());
    }

    /** Travel button / command. Returns true when a charge started. */
    public boolean request(ServerPlayer p, NodeRef dest) {
        NodeMapStats.travel("requests");
        if (charges.containsKey(p.getUUID())) cancel(p, "new_request", false);
        TravelChecks.Outcome o = TravelChecks.check(ctx, p, dest);
        if (!o.ok()) {
            NodeMapStats.travel("refused." + o.refusal().code());
            LotrLog.dbg("travel", "request: player={} to={} at={} -> refused({}) tick={}", LotrLog.id(p.getUUID()), dest,
                    CompanionMover.fmt(p.position()), o.refusal().code(), p.level().getGameTime());
            p.displayClientMessage(o.refusal().message(), false);
            status.send(p, "refused", dest, 0, 0, o.refusal().message());
            return false;
        }
        TravelChecks.Plan plan = o.plan();
        int total = Math.max(1, ctx.settings.get().chargeTicks());
        Charge c = new Charge(dest, plan.to().name(), p, total);
        charges.put(p.getUUID(), c);
        LotrLog.dbg("travel", "request: player={} to={} at={} -> charging({} ticks) from={} {} legs={} cooldown={} exhaustion={} coins={} exempt={} tick={}",
                LotrLog.id(p.getUUID()), dest, CompanionMover.fmt(p.position()), total, plan.from().ref().shortName(), plan.path().describe(),
                plan.path().legs().stream().map(Math::round).toList(), plan.cooldown(), plan.exhaustion(), plan.coins(), plan.exempt(),
                p.level().getGameTime());
        p.displayClientMessage(Msg.t("charging", "Travelling to %s. Stand still.", plan.to().name()), false);
        status.send(p, "charging", dest, total, total, Component.empty());
        return true;
    }

    /** Cancels a running charge (nothing spent); {@code reason} is a code: moved, damage, attack, use_item, bed, mount, ... */
    public boolean cancel(ServerPlayer p, String reason, boolean tell) {
        Charge c = charges.remove(p.getUUID());
        if (c == null) return false;
        NodeMapStats.travel("cancel." + reason);
        LotrLog.dbg("travel", "cancel: player={} to={} reason={} left={}/{} at={} tick={}", LotrLog.id(p.getUUID()), c.target, reason,
                c.left, c.total, CompanionMover.fmt(p.position()), p.level().getGameTime());
        if (tell) {
            Component msg = Msg.t("cancelled", "Fast travel cancelled.");
            p.displayClientMessage(msg, false);
            status.send(p, "cancelled", c.target, 0, c.total, msg);
        }
        return true;
    }

    public boolean cancel(ServerPlayer p, String reason) {
        return cancel(p, reason, true);
    }

    /** Every player tick: count the cooldown down (any dimension), and run the charge. */
    public void tick(ServerPlayer p) {
        NodeMapData data = NodeMapAttachment.get(p);
        if (data.tickCooldown()) {
            LotrLog.dbg("travel", "cooldown over: player={} tick={}", LotrLog.id(p.getUUID()), p.level().getGameTime());
            ctx.onChange.accept(p);
        }
        Charge c = charges.get(p.getUUID());
        if (c == null) return;
        if (!p.isAlive()) {
            cancel(p, "death");
            return;
        }
        if (p.level().dimension() != c.dimension) {
            cancel(p, "dimension");
            return;
        }
        if (p.getVehicle() != c.mount) {
            cancel(p, c.mount == null ? "mount" : "dismount");
            return;
        }
        Vec3 now = c.mount != null ? c.mount.position() : p.position();
        Vec3 from = c.mount != null ? c.mountStart : c.start;
        if (moved(from, now)) {
            cancel(p, "moved");
            return;
        }
        c.left--;
        if (c.left > 0) {
            if (c.left % 20 == 0) {
                status.send(p, "charging", c.target, c.left, c.total, Component.empty());
                if (c.left <= 100) p.displayClientMessage(Msg.t("countdown", "Travelling to %s in %s...", c.targetName, c.left / 20), true);
            }
            return;
        }
        charges.remove(p.getUUID());
        complete(p, c);
    }

    public static boolean moved(Vec3 a, Vec3 b) {
        return Math.abs(a.x - b.x) > STILL || Math.abs(a.y - b.y) > STILL || Math.abs(a.z - b.z) > STILL;
    }

    private void fail(ServerPlayer p, Charge c, String code, Component msg) {
        NodeMapStats.travel("cancel." + code);
        LotrLog.dbg("travel", "cancel: player={} to={} reason={} (at end of charge) tick={}", LotrLog.id(p.getUUID()), c.target, code,
                p.level().getGameTime());
        p.displayClientMessage(msg, false);
        status.send(p, "cancelled", c.target, 0, c.total, msg);
    }

    /** End of the charge: re-check, Depart veto, arrival search, move, cost, Arrive event. */
    void complete(ServerPlayer p, Charge c) {
        TravelChecks.Outcome o = TravelChecks.check(ctx, p, c.target);
        if (!o.ok()) {
            fail(p, c, o.refusal().code(), o.refusal().message());
            return;
        }
        TravelChecks.Plan plan = o.plan();
        NodeTravelEvent.Depart depart = NeoForge.EVENT_BUS.post(new NodeTravelEvent.Depart(p, plan.from().ref(), plan.to().ref(),
                plan.path().length()));
        if (depart.isCanceled()) {
            Component why = depart.getReason() != null ? depart.getReason() : Msg.t("vetoed", "Something keeps you from setting out.");
            fail(p, c, "vetoed", why);
            return;
        }
        ServerLevel level = p.serverLevel();
        GraphNode to = plan.to();
        BlockPos exact = null;
        if (to.ref().isCamp()) {
            NodeMapData.Camp camp = NodeMapAttachment.get(p).camp(to.ref().camp());
            if (camp != null) exact = camp.pos();
        }
        Optional<Vec3> landing = ctx.arrival.find(level, to.x(), to.z(), exact, p);
        if (landing.isEmpty()) {
            LotrLog.warn("travel", "arrival search failed: node={} at {},{} player={}", to.ref().shortName(), Math.round(to.x()),
                    Math.round(to.z()), LotrLog.id(p.getUUID()));
            LotrLog.dbg("travel", "arrival search failed: node={}", to.ref().shortName());
            fail(p, c, "no_landing", Msg.t("refuse.no_landing", "No safe place to arrive at %s.", to.name()));
            return;
        }
        Vec3 fromPos = p.position();
        ResourceKey<Level> fromDim = level.dimension();
        CompanionMover.Party party = CompanionMover.gather(p);
        int left = CompanionMover.move(p, party, landing.get());

        NodeMapData data = NodeMapAttachment.get(p);
        if (!plan.exempt()) {
            data.setCooldown(plan.cooldown());
            p.getFoodData().addExhaustion(plan.exhaustion());
        }
        data.addTrip();
        data.setLastNode(to.ref());
        if (!to.ref().isCamp()) data.addKnown(to.ref().fixed());
        NodeMapStats.travel("trips");

        LotrLog.dbg("travel", "arrive: player={} at={} mount={} leashed={} pets={} left_behind={} left_vehicle={} tick={}",
                LotrLog.id(p.getUUID()), CompanionMover.fmt(p.position()),
                party.mount() == null ? "none" : LotrLog.id(party.mount().getUUID()), party.leashed().size(), party.pets().size(), left,
                party.leftVehicle() == null ? "none" : party.leftVehicle().getType().toShortString(), level.getGameTime());
        LotrLog.info("travel", "trip: player={} from={} to={} L={} chain=[{}] cooldown={} exhaustion={} companions: mount={} leashed={} pets={} ({})",
                LotrLog.id(p.getUUID()), plan.from().ref(), to.ref(), Math.round(plan.path().length()), plan.path().chainIds(),
                plan.cooldown(), plan.exhaustion(), party.mount() == null ? 0 : 1, party.leashed().size(), party.pets().size(), ctx.label);

        NeoForge.EVENT_BUS.post(new NodeTravelEvent.Arrive(p, plan.from().ref(), to.ref(), plan.path().length(), fromPos, fromDim,
                p.position()));
        Component msg = Msg.t("arrived", "You arrive at %s.", to.name());
        p.displayClientMessage(msg, false);
        status.send(p, "arrived", to.ref(), 0, c.total, msg);
        ctx.onChange.accept(p);
    }

    /** Logout: drop the charge and the player's combat tag. */
    public void forget(ServerPlayer p) {
        cancel(p, "logout", false);
        ctx.combat.forget(p);
    }
}
