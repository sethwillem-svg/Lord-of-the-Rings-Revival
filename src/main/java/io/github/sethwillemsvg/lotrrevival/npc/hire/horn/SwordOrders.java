package io.github.sethwillemsvg.lotrrevival.npc.hire.horn;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.ai.combat.NpcCombat;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnit;
import io.github.sethwillemsvg.lotrrevival.npc.hire.HiredUnits;
import io.github.sethwillemsvg.lotrrevival.npc.hire.ai.UnitTargeting;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Command sword orders: which units attack what, and when an order ends. An order lasts until its target is dead,
 * the unit's target changes, the owner cancels it or {@value #ORDER_TICKS} ticks pass. While it lasts the unit carries
 * the sword-order flag (the self-defence rule reads it) and the combat target goal leaves its target alone.
 */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class SwordOrders {
    /** How far the sword ray-casts. */
    public static final double RANGE = 64.0;
    /** Units this close to the player take part. */
    public static final double UNIT_RADIUS = 12.0;
    /** Targets this close to the aimed point are candidates. */
    public static final double TARGET_RADIUS = 6.0;
    /** Ticks after which an order lapses. */
    public static final int ORDER_TICKS = 600;

    private record Order(LivingEntity target, long endsAt) {}

    /** Live orders by unit (server thread only). */
    private static final Map<NpcEntity, Order> ORDERS = new IdentityHashMap<>();

    private SwordOrders() {}

    /** Whether the unit is attacking on a sword order right now; the target goal then keeps hands off its target. */
    public static boolean isActive(NpcEntity unit) {
        return ORDERS.containsKey(unit) && unit.getTarget() != null;
    }

    /** The point the player aims at within {@value #RANGE} blocks (block or living entity), if any. */
    public static Optional<Vec3> aim(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(RANGE));
        HitResult block = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 best = block.getType() == HitResult.Type.MISS ? null : block.getLocation();
        double limit = best == null ? RANGE * RANGE : best.distanceToSqr(eye);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(
                player.level(),
                player,
                eye,
                end,
                player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0),
                e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator());
        if (entity != null && entity.getLocation().distanceToSqr(eye) <= limit) {
            best = entity.getLocation();
        }
        return Optional.ofNullable(best);
    }

    /** Right-click with the sword: order on what the player aims at, or cancel when aiming at nothing. */
    public static int use(ServerPlayer player, String squadron) {
        return use(player, squadron, aim(player));
    }

    /** As {@link #use(ServerPlayer, String)} with the aim already resolved (empty: aiming at nothing). */
    public static int use(ServerPlayer player, String squadron, Optional<Vec3> aimed) {
        if (aimed.isEmpty()) {
            return cancel(player, squadron);
        }
        return order(player, squadron, aimed.get());
    }

    /**
     * Sends every matching unit within {@value #UNIT_RADIUS} blocks of the player onto a target within
     * {@value #TARGET_RADIUS} blocks of the point. A target's score is the unit's squared distance over 144 plus the
     * square of the units already sent onto it; the lowest score wins. Halted units do not take orders.
     *
     * @return the number of units that got an order
     */
    public static int order(ServerPlayer player, String squadron, Vec3 point) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(point, point).inflate(TARGET_RADIUS),
                e -> NpcCombat.isAttackable(e) && e.position().distanceToSqr(point) <= TARGET_RADIUS * TARGET_RADIUS);
        Map<LivingEntity, Integer> taken = new IdentityHashMap<>();
        int ordered = 0;
        for (NpcEntity unit : Squadrons.unitsIn(level, player, squadron)) {
            if (unit.distanceToSqr(player) > UNIT_RADIUS * UNIT_RADIUS || HiredUnits.get(unit).command().halted()) {
                continue;
            }
            LivingEntity best = null;
            double bestScore = Double.MAX_VALUE;
            for (LivingEntity candidate : candidates) {
                if (candidate == unit || UnitTargeting.judge(unit, candidate) == UnitTargeting.Verdict.REJECT) {
                    continue;
                }
                int already = taken.getOrDefault(candidate, 0);
                double score = unit.distanceToSqr(candidate) / 144.0 + (double) already * already;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best != null) {
                taken.merge(best, 1, Integer::sum);
                begin(unit, best);
                ordered++;
            }
        }
        return ordered;
    }

    /** Ends the sword orders of every matching unit of the player in this level. @return how many were ending */
    public static int cancel(ServerPlayer player, String squadron) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        int cancelled = 0;
        for (NpcEntity unit : Squadrons.unitsIn(level, player, squadron)) {
            if (HiredUnits.get(unit).command().swordOrder() || ORDERS.containsKey(unit)) {
                end(unit, true);
                cancelled++;
            }
        }
        return cancelled;
    }

    private static void begin(NpcEntity unit, LivingEntity target) {
        setFlag(unit, true);
        ORDERS.put(unit, new Order(target, unit.level().getGameTime() + ORDER_TICKS));
        unit.getNavigation().stop();
        unit.setTarget(target);
    }

    private static void end(NpcEntity unit, boolean dropTarget) {
        Order order = ORDERS.remove(unit);
        setFlag(unit, false);
        if (dropTarget && order != null && unit.getTarget() == order.target()) {
            unit.setTarget(null);
            unit.getNavigation().stop();
        }
    }

    private static void setFlag(NpcEntity unit, boolean on) {
        HiredUnit state = HiredUnits.get(unit);
        if (!state.isHired() || state.command().swordOrder() == on) {
            return;
        }
        HiredUnit.Command old = state.command();
        HiredUnits.set(
                unit,
                state.withCommand(new HiredUnit.Command(
                        old.state(), old.guardPos(), old.guardRange(), old.teleport(), old.halted(), on)));
    }

    /** Ends orders whose target died or was replaced, whose unit is gone, or that ran out. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ORDERS.isEmpty()) {
            return;
        }
        List<NpcEntity> finished = new ArrayList<>();
        Iterator<Map.Entry<NpcEntity, Order>> it = ORDERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<NpcEntity, Order> entry = it.next();
            NpcEntity unit = entry.getKey();
            Order order = entry.getValue();
            if (unit.isRemoved() || !unit.isAlive() || !unit.isHired()) {
                it.remove();
                continue;
            }
            LivingEntity target = unit.getTarget();
            if (target != order.target()
                    || !order.target().isAlive()
                    || unit.level().getGameTime() >= order.endsAt()) {
                finished.add(unit);
            }
        }
        for (NpcEntity unit : finished) {
            Order order = ORDERS.get(unit);
            boolean timedOut = order != null && order.target().isAlive() && unit.getTarget() == order.target();
            end(unit, timedOut);
        }
    }

    /** A unit that loads with a sword-order flag lost its order with the restart: clear the flag. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof NpcEntity unit)) {
            return;
        }
        if (!ORDERS.containsKey(unit) && HiredUnits.get(unit).command().swordOrder()) {
            setFlag(unit, false);
        }
    }
}
