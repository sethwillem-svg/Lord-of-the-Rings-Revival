package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.api.CombatHooks;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.resources.datas.common.NpcRank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Effect code for every starting strength, weakness and scar (spec Traits). Data picks and tunes; the effects live here. The
 * parry-side effects (Parry master, Crushing blow, Telegraphs) are set through {@link CombatHooks} when the body spawns.
 */
public final class TraitEffects {
    public static int applied;   // counted into the summary, not logged per hit unless debug

    private TraitEffects() {}

    public static List<NemesisTraits.Trait> traits(NemesisRecord r) {
        List<NemesisTraits.Trait> out = new ArrayList<>();
        for (var id : r.strengths) {
            var t = NemesisTraits.get(id);
            if (t != null) out.add(t);
        }
        var w = NemesisTraits.get(r.weakness);
        if (w != null) out.add(w);
        return out;
    }

    /** Traits of a nemesis body or a sergeant; empty for anything else. */
    public static List<NemesisTraits.Trait> traitsOf(Entity e) {
        NemesisRecord r = NemesisApi.bodyOf(e);
        if (r != null) return traits(r);
        if (e.hasData(NemesisLink.SERGEANT)) {
            var t = NemesisTraits.get(e.getData(NemesisLink.SERGEANT).trait());
            if (t != null) return List.of(t);
        }
        return List.of();
    }

    public static @Nullable NemesisTraits.Trait find(NemesisRecord r, String effect) {
        for (var t : traits(r)) if (t.effect().equals(effect)) return t;
        return null;
    }

    public static boolean has(NemesisRecord r, String effect) {
        return find(r, effect) != null;
    }

    private static @Nullable NemesisTraits.Trait find(List<NemesisTraits.Trait> traits, String effect) {
        for (var t : traits) if (t.effect().equals(effect)) return t;
        return null;
    }

    // ---------------------------------------------------------------- pure rules (game tests call these)

    /** Damage multiplier for hits taken by a nemesis or sergeant with these traits and scars. */
    public static double takenMultiplier(LivingEntity victim, List<NemesisTraits.Trait> traits, List<DamageKind> scars, DamageSource src) {
        double m = 1;
        DamageKind kind = DamageKind.of(src);
        var ironHide = find(traits, "iron_hide");
        if (ironHide != null && kind == DamageKind.PROJECTILE) m *= ironHide.num("projectile_taken", 0.6);
        var arrowShy = find(traits, "arrow_shy");
        if (arrowShy != null && kind == DamageKind.PROJECTILE) m *= arrowShy.num("projectile_taken", 1.4);
        var fire = find(traits, "fears_fire");
        if (fire != null && kind == DamageKind.FIRE) m *= fire.num("fire_taken", 1.5);
        var open = find(traits, "open_guard");
        if (open != null && src.getEntity() != null && isBehind(victim, src.getEntity(), open.num("angle", 90))) m *= open.num("behind_taken", 1.5);
        for (DamageKind s : scars) {
            if (s != kind) continue;
            var scar = NemesisTraits.scarFor(s);
            m *= scar == null ? 0.5 : scar.num("taken", 0.5);
        }
        return m;
    }

    /** Damage multiplier for hits dealt by a nemesis or sergeant with these traits. */
    public static double dealtMultiplier(LivingEntity attacker, List<NemesisTraits.Trait> traits, @Nullable NemesisRecord r,
                                         LivingEntity target, DamageSource src) {
        double m = 1;
        boolean melee = DamageKind.of(src) == DamageKind.MELEE;
        var breaker = find(traits, "armour_breaker");
        if (breaker != null && melee) m *= armourIgnoredFactor(target, breaker.num("armour_ignored", 0.25));
        var grudge = find(traits, "grudge_damage");
        if (grudge != null && r != null && target instanceof Player p && r.activeGrudge(p.getUUID()) > 0) m *= grudge.num("damage", 1.25);
        var sun = find(traits, "sun_sick");
        if (sun != null && inDaylight(attacker)) m *= sun.num("damage_dealt", 0.75);
        return m;
    }

    /** True when the attacker stands more than {@code angle} degrees off the victim's facing. */
    public static boolean isBehind(LivingEntity victim, Entity attacker, double angle) {
        Vec3 facing = Vec3.directionFromRotation(0, victim.getYRot()).normalize();
        Vec3 to = new Vec3(attacker.getX() - victim.getX(), 0, attacker.getZ() - victim.getZ());
        if (to.lengthSqr() < 1e-6) return false;
        double cos = facing.dot(to.normalize());
        return cos < Math.cos(Math.toRadians(angle));
    }

    public static boolean inDaylight(LivingEntity e) {
        return e.level().isDay() && !e.level().isRaining() && e.level().canSeeSky(BlockPos.containing(e.getEyePosition()));
    }

    public static boolean sunSick(NemesisRecord r, LivingEntity body) {
        return has(r, "sun_sick") && inDaylight(body);
    }

    /** Vanilla armour formula with a share of the armour ignored: factor on the incoming damage. */
    static double armourIgnoredFactor(LivingEntity target, double share) {
        double armour = target.getArmorValue(), tough = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if (armour <= 0) return 1;
        double full = 1 - reduction(armour, tough, 6), part = 1 - reduction(armour * (1 - share), tough, 6);
        return full <= 0 ? 1 : part / full;
    }

    private static double reduction(double armour, double tough, double dmg) {
        double a = Math.max(armour * 0.2, Math.min(20, armour - dmg / (2 + tough / 4)));
        return a / 25.0;
    }

    /** Did this hit land on the nemesis's weakness (spec Weakness table, "dies for good when killed by")? */
    public static boolean isWeaknessHit(NemesisRecord r, LivingEntity body, DamageSource src) {
        var w = NemesisTraits.get(r.weakness);
        if (w == null) return false;
        DamageKind kind = DamageKind.of(src);
        return switch (w.effect()) {
            case "fears_fire" -> kind == DamageKind.FIRE;
            case "arrow_shy" -> kind == DamageKind.PROJECTILE;
            case "open_guard" -> src.getEntity() != null && isBehind(body, src.getEntity(), w.num("angle", 90));
            case "telegraphs" -> CombatHooks.get().isCounterHit(body, src);
            case "sun_sick" -> inDaylight(body);
            default -> false;
        };
    }

    // ---------------------------------------------------------------- event listeners

    public static void onIncoming(LivingIncomingDamageEvent e) {
        try {
            LivingEntity victim = e.getEntity();
            if (victim.level().isClientSide) return;
            DamageSource src = e.getSource();
            float before = e.getAmount();
            double m = 1;
            NemesisRecord vr = NemesisApi.bodyOf(victim);
            List<NemesisTraits.Trait> vt = vr != null ? traits(vr) : traitsOf(victim);
            if (!vt.isEmpty() || vr != null) m *= takenMultiplier(victim, vt, vr == null ? List.of() : vr.scars, src);
            Entity att = src.getEntity();
            if (att instanceof LivingEntity la) {
                NemesisRecord ar = NemesisApi.bodyOf(la);
                List<NemesisTraits.Trait> at = ar != null ? traits(ar) : traitsOf(la);
                if (!at.isEmpty()) m *= dealtMultiplier(la, at, ar, victim, src);
            }
            if (m != 1) {
                e.setAmount((float) (before * m));
                applied++;
                LotrLog.dbg("nemesis", "trait damage: victim={} attacker={} kind={} dmg {}->{} (x{})", LotrLog.id(victim.getUUID()),
                        att == null ? "-" : LotrLog.id(att.getUUID()), DamageKind.of(src).key(), before, e.getAmount(), String.format("%.2f", m));
            }
            if (vr != null && att instanceof Player p && isWeaknessHit(vr, victim, src) && vr.knowsWeakness.add(p.getUUID())) {
                NemesisSavedData.get(victim.getServer()).setDirty();
                LotrLog.dbg("nemesis", "weakness revealed: id={} player={} weakness={}", vr.shortId(), LotrLog.id(p.getUUID()), vr.weakness);
            }
        } catch (RuntimeException ex) {
            LotrLog.error("nemesis", "trait damage listener failed", ex);
        }
    }

    public static void onDamagePost(LivingDamageEvent.Post e) {
        try {
            LivingEntity victim = e.getEntity();
            if (victim.level().isClientSide) return;
            float dealt = e.getNewDamage();
            if (e.getSource().getEntity() instanceof LivingEntity att && DamageKind.of(e.getSource()) == DamageKind.MELEE) {
                var blood = find(traitsOf(att), "bloodthirsty");
                if (blood != null && dealt > 0) {
                    float heal = (float) (dealt * blood.num("heal_fraction", 0.2));
                    att.heal(heal);
                    LotrLog.dbg("nemesis", "bloodthirsty: {} healed {} from {} dealt", LotrLog.id(att.getUUID()), heal, dealt);
                }
            }
            NemesisRecord r = NemesisApi.bodyOf(victim);
            if (r != null && victim instanceof NpcEntity npc && victim.isAlive()) {
                var horn = find(r, "war_horn");
                if (horn != null && !r.warHornUsed && npc.getHealth() <= npc.getMaxHealth() * horn.num("health_below", 0.5)) {
                    r.warHornUsed = true;
                    warHorn(npc, r, (int) horn.num("soldiers", 3));
                }
            }
        } catch (RuntimeException ex) {
            LotrLog.error("nemesis", "trait post-damage listener failed", ex);
        }
    }

    private static void warHorn(NpcEntity npc, NemesisRecord r, int soldiers) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        var f = Sides.faction(level, r.faction);
        var pool = MeRanks.pool(f, NpcRank.SOLDIER);
        int n = 0;
        for (int i = 0; i < soldiers && !pool.isEmpty(); i++) {
            NpcEntity s = Manifestation.spawnHelper(level, NemesisRandom.pick(pool), npc.blockPosition());
            if (s != null) {
                n++;
                if (npc.getTarget() != null) s.setTarget(npc.getTarget());
                Manifestation.Body b = Manifestation.body(r.id);
                if (b != null) b.guards.add(s);
            }
        }
        LotrLog.dbg("nemesis", "war-horn: id={} health={}/{} called {} soldiers", r.shortId(), npc.getHealth(), npc.getMaxHealth(), n);
    }

    /** Body check (every 2 s): Fears fire makes a burning nemesis run for 5 s. */
    public static void bodyTick(NpcEntity npc, NemesisRecord r) {
        var fire = find(r, "fears_fire");
        if (fire != null && npc.isOnFire()) {
            Vec3 away = npc.position().add(npc.getLookAngle().scale(-8));
            npc.setTarget(null);
            npc.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
            LotrLog.dbg("nemesis", "fears fire: id={} flees from {},{},{}", r.shortId(), npc.getBlockX(), npc.getBlockY(), npc.getBlockZ());
            // TODO(spec §Traits): hold the flight for flee_ticks instead of re-deciding every body check
        }
    }
}
