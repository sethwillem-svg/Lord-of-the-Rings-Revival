package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.ToIntFunction;

/**
 * NPCs parrying the melee hits they take (spec "NPC parry"): by rank, +shield, +hired level, capped; needs the attacker in
 * front, a shield or blade, not staggered, not in its own wind-up, and the cooldown ready. Heavy swings and counter-bonus
 * hits never reach here (callers skip them); arrows are never rolled (only direct melee calls this).
 */
public final class NpcParry {
    private static final Map<NpcEntity, Double> CHANCE_OVERRIDE = new WeakHashMap<>();

    /**
     * Hired unit level of an NPC (1 for unhired). The hiring work sets this hook; we never touch hiring code.
     * TODO(spec "NPC parry", hiring-reputation.md): wire to the hiring session's unit level once it lands.
     */
    public static volatile ToIntFunction<NpcEntity> hiredLevel = npc -> 1;

    private NpcParry() {}

    /** Sets (or with null clears) one NPC's parry chance: tests. */
    public static void chanceOverride(NpcEntity npc, @Nullable Double chance) {
        if (chance == null) CHANCE_OVERRIDE.remove(npc);
        else CHANCE_OVERRIDE.put(npc, chance);
    }

    /** The chance this NPC parries a melee hit, before the gates. */
    public static double chance(NpcEntity npc) {
        Double o = CHANCE_OVERRIDE.get(npc);
        if (o != null) return o;
        double c = CombatConfig.npcParryChance(NpcRanks.of(npc));
        if (c <= 0) return 0;   // civilians and militia never parry, shield or not
        if (hasShield(npc)) c += CombatConfig.npcShieldBonus();
        int level = Math.max(1, hiredLevel.applyAsInt(npc));
        c += (level - 1) * CombatConfig.npcLevelBonus();
        return Math.min(c, CombatConfig.npcMaxChance());
    }

    private static boolean hasShield(NpcEntity npc) {
        return "shield".equals(WeaponClass.of(npc.getMainHandItem())) || "shield".equals(WeaponClass.of(npc.getOffhandItem()));
    }

    private static boolean canParry(NpcEntity npc) {
        return hasShield(npc) || Parry.bladeClass(npc) != null;
    }

    /**
     * Rolls a parry of a hit from attacker (a player's melee hit or another NPC's hit frame). On success: cooldown, sound
     * and sparks, the fx payload; a player attacker's attack strength resets, an NPC attacker is staggered.
     */
    static boolean tryParry(ServerLevel level, NpcEntity npc, LivingEntity attacker, long now) {
        ParryState.NpcCombat st = ParryState.combat(npc);
        String gate = null;
        if (st.staggerUntil > now) gate = "staggered";
        else if (AttackTiming.isWindingUp(npc)) gate = "winding up";
        else if (now < st.parryCooldownUntil) gate = "cooldown until " + st.parryCooldownUntil;
        else if (!canParry(npc)) gate = "no shield or blade";
        else if (!Parry.inFront(npc, attacker)) gate = "behind";
        double chance = chance(npc);
        if (gate == null && chance <= 0) gate = "chance 0";
        String rank = CHANCE_OVERRIDE.containsKey(npc) ? "override" : NpcRanks.of(npc).name();
        if (gate != null) {
            LotrLog.dbg("combat", "npc parry: npc={} rank={} skipped ({}) attacker={} tick={}", LotrLog.id(npc.getUUID()), rank, gate,
                    LotrLog.id(attacker.getUUID()), now);
            return false;
        }
        float roll = npc.getRandom().nextFloat();
        boolean ok = roll < chance;
        LotrLog.dbg("combat", "npc parry: npc={} rank={} chance={} roll={} {} attacker={} tick={}", LotrLog.id(npc.getUUID()), rank,
                String.format("%.2f", chance), String.format("%.2f", roll), ok ? "ok" : "fail", LotrLog.id(attacker.getUUID()), now);
        if (!ok) return false;
        st.parryCooldownUntil = now + CombatConfig.npcCooldown();
        CombatStats.npcParries++;
        CombatFx.npcParry(level, npc, attacker);
        if (attacker instanceof Player p) p.resetAttackStrengthTicker();
        else if (attacker instanceof NpcEntity other) Parry.stagger(level, other, now, "parried by npc " + LotrLog.id(npc.getUUID()));
        return true;
    }
}
