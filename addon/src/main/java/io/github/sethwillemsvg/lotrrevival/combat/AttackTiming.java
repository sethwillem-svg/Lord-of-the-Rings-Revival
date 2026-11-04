package io.github.sethwillemsvg.lotrrevival.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;

import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * NPC melee hits land on their attack clip's hit frame. Middle-earth NPCs deal damage the moment they swing (Minecraft's melee
 * goal, about once a second); here that first damage is held back, the NPC's attack clip for its race and weapon class is
 * picked and sent to nearby players with the speed to play it at, and the NPC's normal attack (damage, knockback, weapon
 * effects) runs at the hit frame if the target is still in reach. Stepping back out of a swing you see coming avoids it,
 * which the attack warning and parry (nemesis project) build on.
 */
public final class AttackTiming {
    /** The attack clip has to fit the melee goal's 20-tick rhythm: played faster when longer, hitting within MAX_WINDUP. */
    private static final float WINDOW_TICKS = 18f, MAX_WINDUP_TICKS = 12f, MIN_WINDUP_TICKS = 3f;
    /** Reach slack at the hit frame (blocks): the target may have moved a little. */
    private static final double REACH_SLACK = 0.75;

    /** A held attack: its target, the tick it lands on, and the clip played for it (null when the race has none). */
    private record Pending(LivingEntity target, long due, @Nullable String slot) {}

    private static final Map<NpcEntity, Pending> PENDING = new WeakHashMap<>();
    private static final Map<NpcEntity, Integer> LAST_STOP = new WeakHashMap<>();
    private static final Map<NpcEntity, Integer> HITS_SENT = new WeakHashMap<>();
    private static final Map<NpcEntity, Integer> NEXT_VARIANT = new WeakHashMap<>();
    private static boolean replaying;

    private AttackTiming() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, AttackTiming::onDamage);
        NeoForge.EVENT_BUS.addListener(AttackTiming::onLevelTick);
    }

    /** The game tick a held attack of this NPC lands on, or -1 when none is pending (combat.test.AttackTimingTests). */
    public static long dueTick(NpcEntity npc) {
        Pending p = PENDING.get(npc);
        return p == null ? -1 : p.due();
    }

    /** Hitstop ticks sent with this NPC's last landed hit, or -1 when its latest attack missed or none landed (combat.test.AttackTimingTests). */
    public static int lastStop(NpcEntity npc) {
        return LAST_STOP.getOrDefault(npc, -1);
    }

    /** How many NpcHitPayloads this NPC's landed hits have sent (combat.test.AttackTimingTests). */
    public static int hitsSent(NpcEntity npc) {
        return HITS_SENT.getOrDefault(npc, 0);
    }

    private static void onDamage(LivingIncomingDamageEvent e) {
        if (replaying) return;
        DamageSource src = e.getSource();
        if (!(src.getDirectEntity() instanceof NpcEntity npc) || src.getEntity() != npc || !src.is(DamageTypes.MOB_ATTACK)
                || src.is(DamageTypeTags.IS_PROJECTILE) || !(npc.level() instanceof ServerLevel level)) return;
        e.setCanceled(true);   // dealt at the hit frame instead
        if (PENDING.containsKey(npc)) return;
        LAST_STOP.remove(npc);
        var hand = Hands.weapon(npc);
        String cls = hand.isEmpty() ? null : Objects.requireNonNullElse(WeaponClass.of(hand), "sword");
        List<AttackClips.Clip> clips = AttackClips.of(NpcRaces.of(npc), cls);
        float windup = MIN_WINDUP_TICKS, speed = 1f;
        String slot = null;
        if (!clips.isEmpty()) {
            int v = NEXT_VARIANT.merge(npc, 1, Integer::sum) % clips.size();
            AttackClips.Clip clip = clips.get(v);
            float lengthTicks = clip.length() * 20f, hitTicks = clip.hit() * 20f;
            speed = Math.max(1f, Math.max(lengthTicks / WINDOW_TICKS, hitTicks / MAX_WINDUP_TICKS));
            windup = Math.max(MIN_WINDUP_TICKS, hitTicks / speed);
            slot = clip.slot();
        }
        PENDING.put(npc, new Pending(e.getEntity(), level.getGameTime() + Math.round(windup), slot));
        if (slot != null) PacketDistributor.sendToPlayersTrackingEntity(npc, new NpcAttackPayload(npc.getId(), slot, speed));
    }

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || PENDING.isEmpty()) return;
        long now = level.getGameTime();
        for (Iterator<Map.Entry<NpcEntity, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<NpcEntity, Pending> en = it.next();
            NpcEntity npc = en.getKey();
            Pending p = en.getValue();
            if (npc.level() != level || now < p.due()) continue;
            it.remove();
            LivingEntity target = p.target();
            if (!npc.isAlive() || !target.isAlive() || target.level() != level || npc.distanceTo(target) > reach(npc) + REACH_SLACK) continue;
            replaying = true;
            try {
                // a hit with no clip played has no swing to freeze
                if (npc.doHurtTarget(target) && p.slot() != null) {
                    int ticks = Hitstop.ticks(NpcRaces.of(npc), AttackClips.classOf(p.slot()));
                    LAST_STOP.put(npc, ticks);
                    HITS_SENT.merge(npc, 1, Integer::sum);
                    PacketDistributor.sendToPlayersTrackingEntity(npc, new NpcHitPayload(npc.getId(), target.getId(), ticks));
                }
            } finally {
                replaying = false;
            }
        }
    }

    private static double reach(NpcEntity npc) {
        var range = npc.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        return range != null ? range.getValue() : 1.75;
    }
}
