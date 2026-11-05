package io.github.sethwillemsvg.lotrrevival.core.api;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Seam to the parry and attack-warning code (specs/addon/combat-parry-warning.md), written in parallel: the nemesis traits set
 * per-NPC overrides through here. Until combat calls {@link #set}, the fallback only logs (the trait still applies its own
 * damage effects; the parry-side effects wait for combat).
 */
public final class CombatHooks {
    public interface Delegate {
        /** {@code AttackTiming.heavyChanceOverride} for this NPC; null clears it. */
        void heavyChance(NpcEntity npc, @Nullable Float chance);

        /** Stagger duration multiplier for this NPC (nemesis x0.5, Telegraphs x2). */
        void staggerMultiplier(NpcEntity npc, float multiplier);

        /** NPC parry chance bonus, maximum and cooldown (Parry master). */
        void npcParry(NpcEntity npc, float bonus, float max, int cooldownTicks);

        /** True when this damage was the counter-bonus hit after a perfect parry against {@code target}. */
        boolean isCounterHit(LivingEntity target, DamageSource source);
    }

    private static volatile Delegate delegate = new Delegate() {
        @Override
        public void heavyChance(NpcEntity npc, @Nullable Float chance) {
            LotrLog.dbg("combat", "heavyChance (no combat hook yet): npc={} chance={}", LotrLog.id(npc.getUUID()), chance);
        }

        @Override
        public void staggerMultiplier(NpcEntity npc, float multiplier) {
            LotrLog.dbg("combat", "staggerMultiplier (no combat hook yet): npc={} x{}", LotrLog.id(npc.getUUID()), multiplier);
        }

        @Override
        public void npcParry(NpcEntity npc, float bonus, float max, int cooldownTicks) {
            LotrLog.dbg("combat", "npcParry (no combat hook yet): npc={} +{} max={} cd={}", LotrLog.id(npc.getUUID()), bonus, max, cooldownTicks);
        }

        @Override
        public boolean isCounterHit(LivingEntity target, DamageSource source) {
            return false;
        }
    };

    private CombatHooks() {}

    public static Delegate get() {
        return delegate;
    }

    public static void set(Delegate d) {
        delegate = Objects.requireNonNull(d);
        LotrLog.info("core", "combat hooks set to {}", d.getClass().getName());
    }
}
