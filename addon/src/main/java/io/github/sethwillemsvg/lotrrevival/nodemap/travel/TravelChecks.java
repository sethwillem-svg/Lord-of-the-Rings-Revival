package io.github.sethwillemsvg.lotrrevival.nodemap.travel;

import io.github.sethwillemsvg.lotrrevival.nodemap.Msg;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapContext;
import io.github.sethwillemsvg.lotrrevival.nodemap.NodeMapSettings;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.GraphNode;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.PathResult;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeAccess;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.sevenstars.middleearth.registries.DynamicRegistriesME;
import net.sevenstars.middleearth.resources.datas.factions.Faction;

/**
 * Every rule that can refuse a trip (spec Fast travel 1), run at the request and again when the charge completes. A pass
 * gives a {@link Plan}: departure, destination, the route chain, and what it will cost this player.
 */
public final class TravelChecks {
    private TravelChecks() {}

    /** A refusal: a short code for logs and counters, and the message for the player. */
    public record Refusal(String code, Component message) {}

    public record Plan(GraphNode from, GraphNode to, PathResult path, int cooldown, float exhaustion, int coins, boolean exempt) {}

    /** Either a plan or a refusal. */
    public record Outcome(@Nullable Plan plan, @Nullable Refusal refusal) {
        public boolean ok() {
            return plan != null;
        }

        static Outcome no(String code, Component msg) {
            return new Outcome(null, new Refusal(code, msg));
        }
    }

    /** Creative and spectator players have no cooldown, hunger, coin cost or combat check. */
    public static boolean exempt(ServerPlayer p) {
        GameType gt = p.gameMode.getGameModeForPlayer();
        return gt == GameType.CREATIVE || gt == GameType.SPECTATOR;
    }

    public static Outcome check(NodeMapContext ctx, ServerPlayer p, NodeRef dest) {
        NodeMapSettings s = ctx.settings.get();
        if (!s.enabled()) return Outcome.no("disabled", Msg.t("refuse.disabled", "Fast travel is turned off on this server."));
        if (!ctx.activeIn(p.level())) return Outcome.no("not_in_middle_earth", Msg.t("refuse.dimension", "Only in Middle-earth."));
        if (!p.isAlive()) return Outcome.no("dead", Msg.t("refuse.dead", "You cannot travel now."));
        if (p.isSleeping()) return Outcome.no("sleeping", Msg.t("refuse.sleeping", "You cannot travel while sleeping."));
        Entity vehicle = p.getVehicle();
        if (vehicle != null) {
            LivingEntity driver = vehicle.getControllingPassenger();
            if (driver != null && driver != p) return Outcome.no("passenger", Msg.t("refuse.passenger", "Someone else holds the reins."));
        }
        NodeAccess acc = new NodeAccess(ctx, p);

        GraphNode to = acc.node(dest);
        if (to == null) return Outcome.no("unknown_destination", Msg.t("refuse.unknown", "That place is not on your map."));
        if (!acc.isKnown(dest)) {
            return Outcome.no("destination_not_known", Msg.t("refuse.not_known", "You have only heard rumours of %s.", to.name()));
        }
        if (acc.isBarred(to)) return Outcome.no("destination_barred", barredMsg(p, to));

        List<GraphNode> near = acc.usableNear(s.departRadius());
        if (near.isEmpty()) {
            return Outcome.no("no_departure", Msg.t("refuse.no_departure", "You must be within %s blocks of a known place to set out.",
                    s.departRadius()));
        }
        PathResult best = null;
        GraphNode from = null;
        for (GraphNode n : near) {
            if (n.ref().equals(dest)) return Outcome.no("already_here", Msg.t("refuse.already_here", "You are already at %s.", to.name()));
            PathResult r = acc.path(n.ref(), dest);
            if (r.found()) {
                best = r;
                from = n;
                break;
            }
            if (best == null) {
                best = r;
                from = n;
            }
        }
        if (!best.found()) {
            if (best.status() == PathResult.Status.BLOCKED) {
                GraphNode b = acc.node(best.blockedBy());
                return Outcome.no("barred_on_route", Msg.t("refuse.barred_on_route", "Your road passes through %s, where %s will not receive you.",
                        b.name(), factionName(p, b.faction())));
            }
            return Outcome.no("no_route", Msg.t("refuse.no_route", "No known road leads there."));
        }

        boolean exempt = exempt(p);
        double len = best.length();
        if (!exempt) {
            int cd = acc.data.cooldown();
            if (cd > 0) return Outcome.no("cooldown", Msg.t("refuse.cooldown", "You must rest %s more before travelling.", TravelCost.clock(cd)));
            if (p.getFoodData().getFoodLevel() < s.minFood()) {
                return Outcome.no("hungry", Msg.t("refuse.hungry", "You are too hungry to travel."));
            }
            Mob hostile = hostileTargeting(p, s.hostileRange());
            if (hostile != null) return Outcome.no("hostile_nearby", Msg.t("refuse.hostile", "You cannot travel with enemies near."));
            if (ctx.combat.inCombat(p, p.level().getGameTime(), s.combatTagTicks())) {
                return Outcome.no("combat_tag", Msg.t("refuse.combat", "You cannot travel in the middle of a fight."));
            }
            int coins = s.cost().coins(len);
            if (coins > 0 && !CoinPurse.canPay(p, coins)) {
                return Outcome.no("cant_afford", Msg.t("refuse.cant_afford", "This journey costs %s copper, and you cannot pay it.", coins));
            }
        }
        return new Outcome(new Plan(from, to, best, exempt ? 0 : s.cost().cooldown(len), exempt ? 0 : s.cost().exhaustion(len),
                exempt ? 0 : s.cost().coins(len), exempt), null);
    }

    @Nullable
    public static Mob hostileTargeting(ServerPlayer p, double range) {
        if (range <= 0) return null;
        List<Mob> mobs = p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range),
                m -> m.isAlive() && m.getTarget() == p && m.distanceToSqr(p) <= range * range);
        return mobs.isEmpty() ? null : mobs.get(0);
    }

    public static Component barredMsg(ServerPlayer p, GraphNode n) {
        return Msg.t("refuse.barred", "%s will not receive you.", factionName(p, n.faction()));
    }

    public static Component factionName(ServerPlayer p, @Nullable ResourceLocation id) {
        if (id == null) return Component.literal("?");
        return p.level().registryAccess().registry(DynamicRegistriesME.FACTION)
                .flatMap(r -> r.getOptional(id))
                .map(f -> (Component) ((Faction) f).getFullName())
                .orElse(Component.literal(id.getPath()));
    }
}
