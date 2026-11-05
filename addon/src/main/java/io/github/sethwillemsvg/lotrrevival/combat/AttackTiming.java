package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * NPC melee hits land on their attack clip's hit frame, after a readable wind-up (specs/addon/combat-parry-warning.md).
 * Middle-earth NPCs deal damage the moment they swing (Minecraft's melee goal); here that damage is held back, the swing is
 * rolled normal or heavy, its clip is picked and sent to nearby players with the wind-up (NpcAttackPayload, also for swings
 * without a clip: the marker), and the NPC's normal attack (damage, knockback, weapon effects, its mount's bite) runs at
 * the hit frame if the target is still in reach and did not parry (Parry, NpcParry). Hits on a player who can parry resolve
 * a few ticks after the hit frame (latency grace); the reach check stays on the hit frame.
 */
public final class AttackTiming {
    /** Weapon classes that never make heavy swings. */
    private static final Set<String> NO_HEAVY = Set.of("bow", "longbow", "crossbow", "banner", "shield");

    /** A held swing. resolveTick is due plus the target's latency grace (due for NPC and itemless targets). */
    public static final class Pending {
        public final LivingEntity target;
        public final long startTick, due, resolveTick;
        public final boolean heavy, mounted;
        @Nullable public final String slot;
        boolean reachChecked;

        Pending(LivingEntity target, long startTick, long due, long resolveTick, boolean heavy, boolean mounted, @Nullable String slot) {
            this.target = target;
            this.startTick = startTick;
            this.due = due;
            this.resolveTick = resolveTick;
            this.heavy = heavy;
            this.mounted = mounted;
            this.slot = slot;
        }
    }

    /** The last replayed NPC hit (tests): damage before and after our multipliers. */
    public record ReplayHit(LivingEntity target, float base, float dealt, boolean heavy, boolean glance) {}

    private static final Map<NpcEntity, Pending> PENDING = new WeakHashMap<>();
    private static final Map<NpcEntity, Integer> NEXT_VARIANT = new WeakHashMap<>();
    private static final Map<NpcEntity, Double> HEAVY_OVERRIDE = new WeakHashMap<>();

    /** Every combat payload the server sends goes here too when set (combat.test). */
    @Nullable public static volatile Consumer<CustomPacketPayload> testSink;
    private static final Map<LivingEntity, ReplayHit> LAST_REPLAY = new WeakHashMap<>();

    private static boolean replaying;
    @Nullable private static NpcEntity replayNpc;
    @Nullable private static Pending replayPending;
    private static boolean replayGlance;

    private AttackTiming() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, AttackTiming::onDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, AttackTiming::onShieldBlock);
        NeoForge.EVENT_BUS.addListener(AttackTiming::onLevelTick);
    }

    /** The game tick a held attack of this NPC lands on, or -1 when none is pending (combat.test.AttackTimingTests). */
    public static long dueTick(NpcEntity npc) {
        Pending p = PENDING.get(npc);
        return p == null ? -1 : p.due;
    }

    /** This NPC's held swing, or null. */
    @Nullable
    public static Pending pending(NpcEntity npc) {
        return PENDING.get(npc);
    }

    /** The NPC is in its own wind-up (it cannot parry then). */
    public static boolean isWindingUp(NpcEntity npc) {
        return PENDING.containsKey(npc);
    }

    /** Sets (or with null clears) the heavy chance for one NPC: tests, and the nemesis project. Bypasses "no two in a row". */
    public static void heavyChanceOverride(NpcEntity npc, @Nullable Double chance) {
        if (chance == null) HEAVY_OVERRIDE.remove(npc);
        else HEAVY_OVERRIDE.put(npc, chance);
    }

    /** The last replayed NPC hit on this target (tests), or null. */
    @Nullable
    public static ReplayHit lastReplay(LivingEntity target) {
        return LAST_REPLAY.get(target);
    }

    /** Drops an NPC's pending swing (stagger, death) and clears its marker. */
    static void drop(NpcEntity npc, NpcAttackEndPayload.Reason reason) {
        if (PENDING.remove(npc) != null) end(npc, reason);
    }

    static void sink(CustomPacketPayload p) {
        Consumer<CustomPacketPayload> s = testSink;
        if (s != null) s.accept(p);
    }

    private static void onDamage(LivingIncomingDamageEvent e) {
        DamageSource src = e.getSource();
        if (replaying) {
            if (replayPending != null && e.getEntity() == replayPending.target && src.getEntity() == replayNpc) applyReplay(e);
            return;
        }
        if (!(src.getDirectEntity() instanceof NpcEntity npc) || src.getEntity() != npc || !src.is(DamageTypes.MOB_ATTACK)
                || src.is(DamageTypeTags.IS_PROJECTILE) || !(npc.level() instanceof ServerLevel level)) return;
        e.setCanceled(true);   // dealt at the hit frame instead
        long now = level.getGameTime();
        if (PENDING.containsKey(npc)) return;
        ParryState.NpcCombat st = ParryState.combat(npc);
        if (st.staggerUntil > now) {
            CombatStats.swingsRefusedStaggered++;
            LotrLog.dbg("combat", "swing refused: npc={} staggered until {} tick={}", LotrLog.id(npc.getUUID()), st.staggerUntil, now);
            return;
        }
        LivingEntity target = e.getEntity();
        var hand = Hands.weapon(npc);
        String realClass = hand.isEmpty() ? null : WeaponClass.of(hand);
        String cls = hand.isEmpty() ? null : Objects.requireNonNullElse(realClass, "sword");
        String race = NpcRaces.of(npc);
        boolean heavy = rollHeavy(npc, realClass, st, now);
        st.lastSwingHeavy = heavy;
        String slot = null;
        if (heavy) {
            AttackClips.Clip c = AttackClips.heavy(race, cls);
            if (c != null) slot = c.slot();
        } else {
            List<AttackClips.Clip> clips = AttackClips.of(race, cls);
            if (!clips.isEmpty()) slot = clips.get(NEXT_VARIANT.merge(npc, 1, Integer::sum) % clips.size()).slot();
        }
        int windup = heavy ? CombatConfig.windupHeavy() : CombatConfig.windupNormal();
        boolean mounted = npc.isPassenger();
        long due = now + windup;
        int grace = 0;
        if (target instanceof ServerPlayer sp && Parry.canParry(sp)) grace = ParryWindow.grace(Parry.latencyMs(sp));
        PENDING.put(npc, new Pending(target, now, due, due + grace, heavy, mounted, slot));
        CombatStats.swings++;
        if (heavy) CombatStats.heavySwings++;
        LotrLog.dbg("combat", "swing: npc={} type={} target={} clip={} class={} windup={} heavy={} mounted={} grace={} tick={} due={}",
                LotrLog.id(npc.getUUID()), npc.getNpcTypeIdentifier(), LotrLog.id(target.getUUID()), slot == null ? "none" : slot,
                cls == null ? "unarmed" : cls, windup, heavy, mounted, grace, now, due);
        NpcAttackPayload payload = new NpcAttackPayload(npc.getId(), slot == null ? "" : slot, windup, target.getId(),
                NpcAttackPayload.flags(heavy, mounted));
        sink(payload);
        PacketDistributor.sendToPlayersTrackingEntity(npc, payload);
        if (heavy && target instanceof ServerPlayer sp) CombatFx.heavyWarn(sp);
    }

    private static boolean rollHeavy(NpcEntity npc, @Nullable String realClass, ParryState.NpcCombat st, long now) {
        if (realClass == null || NO_HEAVY.contains(realClass)) return false;
        Double override = HEAVY_OVERRIDE.get(npc);
        double chance;
        NpcRanks.Rank rank = null;
        if (override != null) {
            chance = override;
        } else {
            if (st.lastSwingHeavy) return false;   // no two heavy swings in a row
            rank = NpcRanks.of(npc);
            chance = CombatConfig.heavyChance(rank);
            if (chance > 0 && ("longsword".equals(realClass) || "axe".equals(realClass))) chance += CombatConfig.heavyTwoHanderBonus();
        }
        if (chance <= 0) return false;
        float roll = npc.getRandom().nextFloat();
        boolean heavy = roll < chance;
        LotrLog.dbg("combat", "heavy roll: npc={} rank={} class={} chance={} roll={} {} tick={}", LotrLog.id(npc.getUUID()),
                rank == null ? "override" : rank, realClass, String.format("%.2f", chance), String.format("%.2f", roll),
                heavy ? "heavy" : "normal", now);
        return heavy;
    }

    /** Our multipliers on a replayed hit: heavy, glance. */
    private static void applyReplay(LivingIncomingDamageEvent e) {
        Pending p = Objects.requireNonNull(replayPending);
        float base = e.getAmount(), dealt = base;
        if (p.heavy) dealt *= (float) CombatConfig.heavyMultiplier();
        if (replayGlance) dealt *= (float) CombatConfig.glanceMultiplier();
        if (dealt != base) e.setAmount(dealt);
        LAST_REPLAY.put(e.getEntity(), new ReplayHit(e.getEntity(), base, dealt, p.heavy, replayGlance));
        LotrLog.dbg("combat", "hit: npc={} target={} heavy={} glance={} dmg {}->{}", replayNpc == null ? "-" : LotrLog.id(replayNpc.getUUID()),
                LotrLog.id(e.getEntity().getUUID()), p.heavy, replayGlance, base, dealt);
    }

    /** Heavy swings go through raised shields (heavy.shield_share of 0). */
    private static void onShieldBlock(LivingShieldBlockEvent e) {
        if (!replaying || replayPending == null || !replayPending.heavy || e.getEntity() != replayPending.target) return;
        // TODO(spec "Heavy attacks"): heavy.shield_share > 0 should let the shield absorb that share instead of all or nothing
        e.setBlocked(false);   // counted in Parry.resolvePlayer ("heavy bypass")
        LotrLog.dbg("combat", "heavy bypass: vanilla shield block skipped, defender={} attacker={}", LotrLog.id(e.getEntity().getUUID()),
                replayNpc == null ? "-" : LotrLog.id(replayNpc.getUUID()));
    }

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Parry.tickStaggers(level, now);
        if (PENDING.isEmpty()) return;
        List<Map.Entry<NpcEntity, Pending>> ready = new ArrayList<>();
        for (Iterator<Map.Entry<NpcEntity, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<NpcEntity, Pending> en = it.next();
            NpcEntity npc = en.getKey();
            Pending p = en.getValue();
            if (npc.level() != level) {
                if (npc.isRemoved() || !npc.isAlive()) {   // removed or dead while in another level's map
                    it.remove();
                    CombatStats.cancelled++;
                }
                continue;
            }
            LivingEntity target = p.target;
            if (!npc.isAlive() || npc.isRemoved() || !target.isAlive() || target.isRemoved() || target.level() != level) {
                it.remove();
                CombatStats.cancelled++;
                LotrLog.dbg("combat", "swing cancelled: npc={} target={} npc_alive={} target_alive={} tick={}", LotrLog.id(npc.getUUID()),
                        LotrLog.id(target.getUUID()), npc.isAlive(), target.isAlive(), now);
                end(npc, NpcAttackEndPayload.Reason.CANCELLED);
                continue;
            }
            if (now < p.due) continue;
            if (!p.reachChecked) {
                // the reach check is on the hit frame itself (not the deferred resolution): stepping back avoids the hit
                double dist = npc.distanceTo(target), reach = reach(npc, target);
                boolean inReach = dist <= reach;
                LotrLog.dbg("combat", "reach check: npc={} target={} dist={} reach={} (range {} + mounted {} + slack {}) npc={} target_at={} -> {} tick={} due={}",
                        LotrLog.id(npc.getUUID()), LotrLog.id(target.getUUID()), String.format("%.2f", dist), String.format("%.2f", reach),
                        String.format("%.2f", baseRange(npc)), npc.getVehicle() != null || target.getVehicle() != null, CombatConfig.reachSlack(),
                        pos(npc), pos(target), inReach ? "in reach" : "MISS", now, p.due);
                if (!inReach) {
                    it.remove();
                    CombatStats.misses++;
                    LotrLog.dbg("combat", "miss: npc={} target={} dist={} reach={} heavy={} tick={}", LotrLog.id(npc.getUUID()),
                            LotrLog.id(target.getUUID()), String.format("%.2f", dist), String.format("%.2f", reach), p.heavy, now);
                    end(npc, NpcAttackEndPayload.Reason.MISSED);
                    continue;
                }
                p.reachChecked = true;
            }
            if (now < p.resolveTick) continue;
            it.remove();
            ready.add(en);
        }
        for (Map.Entry<NpcEntity, Pending> en : ready) resolve(level, en.getKey(), en.getValue(), now);
    }

    /** The hit frame (plus grace) of a swing in reach: parry checks, then the NPC's own attack. */
    private static void resolve(ServerLevel level, NpcEntity npc, Pending p, long now) {
        LivingEntity target = p.target;
        Parry.Outcome outcome = Parry.Outcome.NONE;
        try {
            if (target instanceof Player player) outcome = Parry.resolvePlayer(level, npc, player, p, now);
            else if (target instanceof NpcEntity defender && !p.heavy && CombatConfig.npcVsNpcs()
                    && NpcParry.tryParry(level, defender, npc, now)) outcome = Parry.Outcome.PERFECT;
        } catch (RuntimeException ex) {
            LotrLog.error("combat", "parry check failed; the hit lands normally", ex);
            outcome = Parry.Outcome.NONE;
        }
        if (outcome == Parry.Outcome.PERFECT) {
            end(npc, NpcAttackEndPayload.Reason.PARRIED);
            return;
        }
        replaying = true;
        replayNpc = npc;
        replayPending = p;
        replayGlance = outcome == Parry.Outcome.GLANCE;
        try {
            npc.doHurtTarget(target);
            CombatStats.hits++;
        } finally {
            replaying = false;
            replayNpc = null;
            replayPending = null;
            replayGlance = false;
        }
        end(npc, NpcAttackEndPayload.Reason.HIT);
    }

    private static void end(NpcEntity npc, NpcAttackEndPayload.Reason reason) {
        NpcAttackEndPayload payload = NpcAttackEndPayload.of(npc.getId(), reason);
        sink(payload);
        if (npc.level() instanceof ServerLevel) PacketDistributor.sendToPlayersTrackingEntity(npc, payload);
    }

    /** Reach at the hit frame: Middle-earth's melee range (+ mounted bonus when either rides) plus the slack. */
    static double reach(NpcEntity npc, LivingEntity target) {
        double r = baseRange(npc);
        if (npc.getVehicle() != null || target.getVehicle() != null) r += CombatConfig.mountedReach();
        return r + CombatConfig.reachSlack();
    }

    /** The reach (with slack) a held swing of this NPC on this target is checked against at the hit frame (tests, logs). */
    public static double reachFor(NpcEntity npc, LivingEntity target) {
        return reach(npc, target);
    }

    /** Middle-earth's melee range for this NPC (its race's ENTITY_INTERACTION_RANGE), 1.75 without the attribute. */
    static double baseRange(NpcEntity npc) {
        var range = npc.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        return range != null ? range.getValue() : 1.75;
    }

    private static String pos(LivingEntity e) {
        return String.format("%.2f,%.2f,%.2f", e.getX(), e.getY(), e.getZ());
    }
}
