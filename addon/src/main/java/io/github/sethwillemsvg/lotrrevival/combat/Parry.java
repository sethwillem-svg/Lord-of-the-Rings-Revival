package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.item.items.shields.CustomShieldItem;
import net.sevenstars.middleearth.item.utils.ShieldTypesME;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player parry (specs/addon/combat-parry-warning.md, "Parry"): presses are stamped with the server tick (shield use start,
 * ParryPayload for a blade guard), judged at a held NPC swing's resolution against the item's perfect window plus the
 * player's latency grace, then: perfect (no damage, attacker staggered, counter armed), glance (blade guard, half damage),
 * vanilla block (shield raised early), or nothing. Heavy swings and attackers behind the player ignore all of it.
 */
public final class Parry {
    public enum Outcome { NONE, PERFECT, GLANCE }

    private static final Set<String> BLADES = Set.of("dagger", "sword", "longsword", "axe", "spear");
    private static final ResourceLocation STAGGER_SLOW = LotrRevival.id("stagger");
    private static final Set<ResourceLocation> WARNED_SHIELDS = ConcurrentHashMap.newKeySet();
    /** NPCs staggered right now, to lift the slow when it ends. */
    private static final Map<NpcEntity, Boolean> STAGGERED = new WeakHashMap<>();
    /** The last swing that landed on a player without a parry: {due, resolveTick} (to log presses that came too late). */
    private static final Map<Player, long[]> LAST_UNPARRIED = new WeakHashMap<>();

    /** The damage of the last player melee hit on each NPC after our changes (tests: counter bonus, NPC parry). */
    private static final Map<NpcEntity, Float> LAST_PLAYER_HIT = new WeakHashMap<>();
    /** What the last resolution against each player decided, as its log word (tests). */
    private static final Map<Player, String> LAST_RESULT = new WeakHashMap<>();

    private Parry() {}

    /** The last player melee hit on this NPC after our changes (0 when it parried), or -1 (tests). */
    public static float lastPlayerHit(NpcEntity npc) {
        return LAST_PLAYER_HIT.getOrDefault(npc, -1f);
    }

    /** The log word of the last resolution of a swing on this player ("parry ok", "glance", "locked", ...) (tests). */
    public static String lastResult(Player player) {
        return LAST_RESULT.getOrDefault(player, "");
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(Parry::onUseStart);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Parry::onPlayerHitsNpc);
    }

    // ---------------------------------------------------------------- items

    /** The parry class of a held shield: light_shield, medium_shield or heavy_shield (unknown types count as medium). */
    static String shieldClass(ItemStack stack) {
        if (stack.getItem() instanceof CustomShieldItem cs) {
            if (cs.type == ShieldTypesME.LIGHT_SHIELD) return "light_shield";
            if (cs.type == ShieldTypesME.HEAVY_SHIELD) return "heavy_shield";
            return "medium_shield";
        }
        if (stack.getItem() != Items.SHIELD) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (WARNED_SHIELDS.add(id)) LotrLog.warn("combat", "shield {} is of an unknown type, treated as medium", id);
        }
        return "medium_shield";
    }

    @Nullable
    private static ItemStack heldShield(Player player) {
        if (player.getOffhandItem().getItem() instanceof ShieldItem) return player.getOffhandItem();
        if (player.getMainHandItem().getItem() instanceof ShieldItem) return player.getMainHandItem();
        return null;
    }

    /** The main hand's blade class (dagger, sword, longsword, axe, spear), or null. */
    @Nullable
    public static String bladeClass(LivingEntity e) {
        String cls = WeaponClass.of(Hands.weapon(e));
        return cls != null && BLADES.contains(cls) ? cls : null;
    }

    /** Holds something to parry with: a shield in either hand, or a blade. */
    public static boolean canParry(Player player) {
        return heldShield(player) != null || bladeClass(player) != null;
    }

    /** The perfect window (ticks) for what the player holds now: a shield in either hand, else the blade; 0 when it cannot parry. */
    public static int windowFor(Player player) {
        ItemStack shield = heldShield(player);
        if (shield != null) return CombatConfig.window(shieldClass(shield));
        String blade = bladeClass(player);
        return blade == null ? 0 : CombatConfig.window(blade);
    }

    public static int latencyMs(ServerPlayer player) {
        return player.connection == null ? 0 : Math.max(0, player.connection.latency());
    }

    // ---------------------------------------------------------------- presses

    private static void onUseStart(LivingEntityUseItemEvent.Start e) {
        if (!(e.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        ItemStack stack = e.getItem();
        if (!(stack.getItem() instanceof ShieldItem)) return;
        recordPress(player, ParryState.PressKind.SHIELD, shieldClass(stack), player.level().getGameTime());
    }

    /**
     * A blade guard press (the ParryPayload handler; public for combat.test). Ignored without a blade in the main hand, with a
     * shield in either hand (the shield row applies), or while the previous guard is still up.
     */
    public static boolean press(Player player) {
        long now = player.level().getGameTime();
        ParryState.PlayerParry s = ParryState.of(player);
        String blade = bladeClass(player);
        if (blade == null || heldShield(player) != null) {
            badPress(player, s, now, blade == null ? "no blade" : "holds a shield");
            return false;
        }
        if (s.kind == ParryState.PressKind.BLADE && now < s.guardUntil && !s.pressPerfect) {
            LotrLog.dbg("combat", "press ignored: player={} guard up until {} tick={}", LotrLog.id(player.getUUID()), s.guardUntil, now);
            return false;
        }
        recordPress(player, ParryState.PressKind.BLADE, blade, now);
        return true;
    }

    private static void badPress(Player player, ParryState.PlayerParry s, long now, String why) {
        if (now - s.badSecond >= 20) {
            s.badSecond = now;
            s.badCount = 0;
        }
        s.badCount++;
        LotrLog.dbg("combat", "press rejected: player={} {} tick={}", LotrLog.id(player.getUUID()), why, now);
        if (s.badCount == 21)
            LotrLog.warn("combat", "player {} sent more than 20 parry presses in a second without a parry-capable item (bad client?)",
                    player.getGameProfile().getName());
    }

    private static void recordPress(Player player, ParryState.PressKind kind, String itemClass, long now) {
        ParryState.PlayerParry s = ParryState.of(player);
        // a press that did not parry locks perfect windows for lock_ticks from that press; a perfect parry clears it
        boolean locked = s.hasPress() && !s.pressPerfect && now - s.pressTick < CombatConfig.lockTicks();
        s.pressTick = now;
        s.kind = kind;
        s.itemClass = itemClass;
        s.pressLocked = locked;
        s.pressPerfect = false;
        if (kind == ParryState.PressKind.BLADE) s.guardUntil = now + CombatConfig.guardTicks();
        CombatStats.presses++;
        LotrLog.dbg("combat", "press: player={} kind={} item={} tick={} locked={}", LotrLog.id(player.getUUID()),
                kind == ParryState.PressKind.SHIELD ? "shield" : "blade", itemClass, now, locked);
        long[] last = LAST_UNPARRIED.get(player);
        if (last != null && now > last[1] && now - last[0] <= 6) {
            CombatStats.late++;
            LotrLog.dbg("combat", "late: player={} item={} press={} hit={} resolved={} ({} ticks after the hit)", LotrLog.id(player.getUUID()),
                    itemClass, now, last[0], last[1], now - last[0]);
            LAST_UNPARRIED.remove(player);
        }
    }

    // ---------------------------------------------------------------- resolution

    /** Judges a held swing reaching a player (AttackTiming, at resolveTick). Applies the parry's effects; returns what to deal. */
    static Outcome resolvePlayer(ServerLevel level, NpcEntity npc, Player player, AttackTiming.Pending p, long now) {
        ParryState.PlayerParry s = ParryState.of(player);
        int grace = (int) (p.resolveTick - p.due);
        boolean recentPress = s.hasPress() && s.pressTick >= p.startTick - 20;
        String weapon = s.hasPress() ? s.itemClass : "-";
        int window = s.hasPress() ? CombatConfig.window(s.itemClass) : 0;
        long sincePress = s.hasPress() ? p.due - s.pressTick : -1;
        String fields = String.format("defender=%s weapon=%s window=%d/%d ticks grace=%d attacker=%s press=%s hit=%d tick=%d dmg~%.1f",
                LotrLog.id(player.getUUID()), weapon, sincePress, window, grace, LotrLog.id(npc.getUUID()),
                s.hasPress() ? Long.toString(s.pressTick) : "none", p.due, now, attackDamage(npc));
        if (p.heavy) {
            if (recentPress || player.isBlocking()) {
                CombatStats.heavyBypass++;
                result(player, "heavy bypass", fields + "->full x" + CombatConfig.heavyMultiplier());
            } else {
                result(player, "unparried", fields + " heavy");
            }
            LAST_UNPARRIED.put(player, new long[] {p.due, p.resolveTick});
            return Outcome.NONE;
        }
        if (!inFront(player, npc)) {
            if (recentPress || player.isBlocking()) {
                CombatStats.behind++;
                result(player, "behind", fields + "->full");
            } else {
                result(player, "unparried", fields + " behind");
            }
            return Outcome.NONE;
        }
        if (!s.hasPress() || !recentPress) {
            if (player.isBlocking()) {
                CombatStats.vanillaBlocks++;
                result(player, "vanilla block", fields);
            } else {
                result(player, "unparried", fields);
            }
            LAST_UNPARRIED.put(player, new long[] {p.due, p.resolveTick});
            return Outcome.NONE;
        }
        if (!stillHeld(player, s)) {
            result(player, "swapped", fields + " (item at the press no longer held)");
            return Outcome.NONE;
        }
        boolean blade = s.kind == ParryState.PressKind.BLADE;
        int guard = blade ? CombatConfig.guardTicks() : 0;
        ParryWindow.Result r = ParryWindow.classify(s.pressTick, p.due, window, guard, grace);
        if (s.pressLocked && (r == ParryWindow.Result.PERFECT || r == ParryWindow.Result.GLANCE)) {
            CombatStats.locked++;
            if (blade) {   // a locked press still guards: glance only
                result(player, "locked", fields + " -> glance");
                glance(level, npc, player);
                return Outcome.GLANCE;
            }
            result(player, "locked", fields + " -> shield as raised");
            return Outcome.NONE;
        }
        switch (r) {
            case PERFECT -> {
                perfect(level, npc, player, s, now);
                result(player, "parry ok", fields + "->0.0");
                return Outcome.PERFECT;
            }
            case GLANCE -> {
                result(player, "glance", fields + "->x" + CombatConfig.glanceMultiplier());
                glance(level, npc, player);
                return Outcome.GLANCE;
            }
            case EARLY -> {
                if (player.isBlocking()) {
                    CombatStats.vanillaBlocks++;
                    result(player, "vanilla block", fields);
                } else {
                    result(player, "early", fields + "->full");
                }
                LAST_UNPARRIED.put(player, new long[] {p.due, p.resolveTick});
                return Outcome.NONE;
            }
            case LATE -> {
                CombatStats.late++;
                result(player, "late", fields + "->full");
                return Outcome.NONE;
            }
            default -> {
                result(player, "unparried", fields);
                return Outcome.NONE;
            }
        }
    }

    private static void result(Player player, String word, String fields) {
        LAST_RESULT.put(player, word);
        LotrLog.dbg("combat", "{}: {}", word, fields);
    }

    private static boolean stillHeld(Player player, ParryState.PlayerParry s) {
        if (s.kind == ParryState.PressKind.SHIELD) {
            ItemStack shield = heldShield(player);
            return shield != null && shieldClass(shield).equals(s.itemClass);
        }
        return s.itemClass.equals(bladeClass(player));
    }

    private static void perfect(ServerLevel level, NpcEntity npc, Player player, ParryState.PlayerParry s, long now) {
        s.pressPerfect = true;   // clears the lock, and a new guard may start at once
        s.counterTarget = npc.getUUID();
        s.counterUntil = now + CombatConfig.counterTicks();
        CombatStats.perfect++;
        stagger(level, npc, now, "parried by " + LotrLog.id(player.getUUID()));
        double kb = CombatConfig.parryKnockback();
        if (kb > 0) npc.knockback(kb, player.getX() - npc.getX(), player.getZ() - npc.getZ());
        CombatFx.perfect(level, player, npc);
        LotrLog.dbg("combat", "counter armed: player={} target={} until={}", LotrLog.id(player.getUUID()), LotrLog.id(npc.getUUID()),
                s.counterUntil);
    }

    private static void glance(ServerLevel level, NpcEntity npc, Player player) {
        CombatStats.glances++;
        ItemStack weapon = player.getMainHandItem();
        if (!weapon.isEmpty() && weapon.isDamageableItem()) weapon.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        CombatFx.glance(level, player, npc);
    }

    private static double attackDamage(NpcEntity npc) {
        try {
            return npc.getAttributeValue(Attributes.ATTACK_DAMAGE);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** The attacker is in the defender's front half-plane (by the defender's head yaw). */
    public static boolean inFront(LivingEntity defender, Entity attacker) {
        double yaw = Math.toRadians(defender.getYHeadRot());
        double lx = -Math.sin(yaw), lz = Math.cos(yaw);
        double dx = attacker.getX() - defender.getX(), dz = attacker.getZ() - defender.getZ();
        return lx * dx + lz * dz > 0;
    }

    // ---------------------------------------------------------------- stagger

    /** Staggers an NPC: no new swings, slowed, its pending swing dropped. */
    public static void stagger(ServerLevel level, NpcEntity npc, long now, String why) {
        ParryState.NpcCombat st = ParryState.combat(npc);
        double mult = st.staggerMultiplier;
        if (NpcRanks.of(npc) == NpcRanks.Rank.LEADER) mult *= CombatConfig.staggerLeaderMultiplier();
        int ticks = (int) Math.round(CombatConfig.staggerTicks() * mult);
        st.staggerUntil = now + ticks;
        AttackTiming.drop(npc, NpcAttackEndPayload.Reason.PARRIED);
        var speed = npc.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null)
            speed.addOrUpdateTransientModifier(new AttributeModifier(STAGGER_SLOW, CombatConfig.staggerSpeed(), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        STAGGERED.put(npc, Boolean.TRUE);
        CombatStats.staggers++;
        LotrLog.dbg("combat", "stagger start: npc={} ticks={} until={} ({}) tick={}", LotrLog.id(npc.getUUID()), ticks, st.staggerUntil, why, now);
        // TODO(spec ticket 3/8): the client plays the NPC's stagger clip on NpcAttackEndPayload PARRIED; none exist yet
    }

    /** Lifts the slow from NPCs whose stagger ended (AttackTiming's level tick). */
    static void tickStaggers(ServerLevel level, long now) {
        if (STAGGERED.isEmpty()) return;
        for (Iterator<NpcEntity> it = STAGGERED.keySet().iterator(); it.hasNext(); ) {
            NpcEntity npc = it.next();
            if (npc.level() != level) {
                if (npc.isRemoved()) it.remove();
                continue;
            }
            if (ParryState.staggeredUntil(npc) > now && npc.isAlive()) continue;
            it.remove();
            var speed = npc.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(STAGGER_SLOW);
            LotrLog.dbg("combat", "stagger end: npc={} tick={}", LotrLog.id(npc.getUUID()), now);
        }
    }

    // ---------------------------------------------------------------- player hits on NPCs

    /** Counter bonus, then the NPC's parry roll, on a direct player melee hit on an NPC. */
    private static void onPlayerHitsNpc(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof NpcEntity npc) || !(npc.level() instanceof ServerLevel level)) return;
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof Player player) || src.getDirectEntity() != player || !src.is(DamageTypes.PLAYER_ATTACK)) return;
        long now = level.getGameTime();
        ParryState.PlayerParry s = ParryState.of(player);
        float before = e.getAmount();
        if (s.counterTarget != null && now > s.counterUntil) s.clearCounter();
        if (s.counterTarget != null && s.counterTarget.equals(npc.getUUID())) {
            float after = before * (float) CombatConfig.counterMultiplier();
            e.setAmount(after);
            s.clearCounter();
            CombatStats.counters++;
            LAST_PLAYER_HIT.put(npc, after);
            LotrLog.dbg("combat", "counter bonus: player={} npc={} dmg {}->{} tick={}", LotrLog.id(player.getUUID()), LotrLog.id(npc.getUUID()),
                    before, after, now);
            return;   // counter hits are never rolled
        }
        if (NpcParry.tryParry(level, npc, player, now)) {
            e.setCanceled(true);
            LAST_PLAYER_HIT.put(npc, 0f);
            return;
        }
        LAST_PLAYER_HIT.put(npc, before);
    }
}
