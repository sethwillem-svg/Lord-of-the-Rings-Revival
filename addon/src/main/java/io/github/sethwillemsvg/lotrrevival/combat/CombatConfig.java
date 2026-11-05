package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side combat numbers (specs/addon/combat-parry-warning.md, "Rules and numbers"): lotr_revival-common.toml, section
 * [combat]. Every getter falls back to the default when the config is not loaded yet, so pure code and early calls work.
 */
public final class CombatConfig {
    private CombatConfig() {}

    public static final ModConfigSpec SPEC;

    // wind-up
    static final ModConfigSpec.IntValue WINDUP_NORMAL, WINDUP_HEAVY;
    static final ModConfigSpec.DoubleValue REACH_SLACK, MOUNTED_REACH;
    // heavy
    static final ModConfigSpec.DoubleValue HEAVY_MULT, HEAVY_SOLDIER, HEAVY_KNIGHT, HEAVY_VETERAN, HEAVY_LEADER, HEAVY_TWO_HANDER,
            HEAVY_SHIELD_SHARE;
    // parry windows (ticks after the press)
    static final ModConfigSpec.IntValue WIN_LIGHT_SHIELD, WIN_MEDIUM_SHIELD, WIN_HEAVY_SHIELD, WIN_DAGGER, WIN_SWORD, WIN_LONGSWORD,
            WIN_AXE, WIN_SPEAR, GUARD_TICKS, GRACE_CAP, LOCK_TICKS, STAGGER_TICKS, COUNTER_TICKS;
    static final ModConfigSpec.DoubleValue GLANCE_MULT, STAGGER_LEADER_MULT, COUNTER_MULT, PARRY_KNOCKBACK, STAGGER_SPEED;
    // NPC parry
    static final ModConfigSpec.DoubleValue NPC_SOLDIER, NPC_KNIGHT, NPC_VETERAN, NPC_LEADER, NPC_SHIELD_BONUS, NPC_LEVEL_BONUS, NPC_MAX;
    static final ModConfigSpec.IntValue NPC_COOLDOWN;
    static final ModConfigSpec.BooleanValue NPC_VS_NPCS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("combat");
        b.comment("Wind-up: ticks from the start of an NPC melee swing to its hit frame").push("windup");
        WINDUP_NORMAL = b.defineInRange("normal", 10, 1, 19);
        WINDUP_HEAVY = b.defineInRange("heavy", 18, 1, 19);
        REACH_SLACK = b.comment("Extra reach (blocks) at the hit frame: the target may have moved a little").defineInRange("reach_slack", 0.75, 0.0, 4.0);
        MOUNTED_REACH = b.comment("Extra reach when the attacker or the target rides (Middle-earth's own melee range adds this)")
                .defineInRange("mounted_reach", 0.5, 0.0, 4.0);
        b.pop();

        b.comment("Heavy (unblockable) swings").push("heavy");
        HEAVY_MULT = b.defineInRange("damage_multiplier", 1.4, 1.0, 10.0);
        HEAVY_SOLDIER = b.defineInRange("chance_soldier", 0.05, 0.0, 1.0);
        HEAVY_KNIGHT = b.defineInRange("chance_knight", 0.10, 0.0, 1.0);
        HEAVY_VETERAN = b.defineInRange("chance_veteran", 0.15, 0.0, 1.0);
        HEAVY_LEADER = b.defineInRange("chance_leader", 0.25, 0.0, 1.0);
        HEAVY_TWO_HANDER = b.comment("Added for longswords and axes").defineInRange("two_hander_bonus", 0.05, 0.0, 1.0);
        HEAVY_SHIELD_SHARE = b.comment("Share of a heavy hit a raised shield still absorbs (0: fully unblockable)")
                .defineInRange("shield_share", 0.0, 0.0, 1.0);
        b.pop();

        b.comment("Player parry: perfect windows in ticks after the press").push("parry");
        WIN_LIGHT_SHIELD = b.defineInRange("window_light_shield", 7, 0, 20);
        WIN_MEDIUM_SHIELD = b.defineInRange("window_medium_shield", 6, 0, 20);
        WIN_HEAVY_SHIELD = b.defineInRange("window_heavy_shield", 5, 0, 20);
        WIN_DAGGER = b.defineInRange("window_dagger", 4, 0, 20);
        WIN_SWORD = b.defineInRange("window_sword", 5, 0, 20);
        WIN_LONGSWORD = b.defineInRange("window_longsword", 6, 0, 20);
        WIN_AXE = b.defineInRange("window_axe", 4, 0, 20);
        WIN_SPEAR = b.defineInRange("window_spear", 4, 0, 20);
        GUARD_TICKS = b.comment("Blade guard length; after the perfect window a hit glances until this tick").defineInRange("guard_ticks", 12, 1, 40);
        GLANCE_MULT = b.defineInRange("glance_multiplier", 0.5, 0.0, 1.0);
        GRACE_CAP = b.comment("Most latency grace ticks a player gets").defineInRange("grace_cap", 4, 0, 10);
        LOCK_TICKS = b.comment("A press that does not parry locks perfect windows for this long").defineInRange("lock_ticks", 20, 0, 100);
        STAGGER_TICKS = b.defineInRange("stagger_ticks", 20, 0, 100);
        STAGGER_LEADER_MULT = b.defineInRange("stagger_leader_multiplier", 0.5, 0.0, 1.0);
        STAGGER_SPEED = b.comment("Movement speed change while staggered").defineInRange("stagger_speed", -0.5, -1.0, 0.0);
        PARRY_KNOCKBACK = b.defineInRange("knockback", 0.4, 0.0, 4.0);
        COUNTER_TICKS = b.defineInRange("counter_ticks", 30, 0, 200);
        COUNTER_MULT = b.defineInRange("counter_multiplier", 1.5, 1.0, 10.0);
        b.pop();

        b.comment("NPCs parrying hits they take").push("npc_parry");
        NPC_SOLDIER = b.defineInRange("chance_soldier", 0.05, 0.0, 1.0);
        NPC_KNIGHT = b.defineInRange("chance_knight", 0.10, 0.0, 1.0);
        NPC_VETERAN = b.defineInRange("chance_veteran", 0.15, 0.0, 1.0);
        NPC_LEADER = b.defineInRange("chance_leader", 0.20, 0.0, 1.0);
        NPC_SHIELD_BONUS = b.defineInRange("shield_bonus", 0.10, 0.0, 1.0);
        NPC_LEVEL_BONUS = b.comment("Per hired-unit level above 1").defineInRange("level_bonus", 0.02, 0.0, 1.0);
        NPC_MAX = b.defineInRange("max_chance", 0.40, 0.0, 1.0);
        NPC_COOLDOWN = b.defineInRange("cooldown_ticks", 40, 0, 400);
        NPC_VS_NPCS = b.comment("NPCs also parry other NPCs' hits").define("vs_npcs", true);
        b.pop();
        b.pop();
        SPEC = b.build();
    }

    static int i(ModConfigSpec.IntValue v) {
        try {
            return v.get();
        } catch (IllegalStateException notLoaded) {
            return v.getDefault();
        }
    }

    static double d(ModConfigSpec.DoubleValue v) {
        try {
            return v.get();
        } catch (IllegalStateException notLoaded) {
            return v.getDefault();
        }
    }

    static boolean b(ModConfigSpec.BooleanValue v) {
        try {
            return v.get();
        } catch (IllegalStateException notLoaded) {
            return v.getDefault();
        }
    }

    public static int windupNormal() { return i(WINDUP_NORMAL); }
    public static int windupHeavy() { return i(WINDUP_HEAVY); }
    public static double reachSlack() { return d(REACH_SLACK); }
    public static double mountedReach() { return d(MOUNTED_REACH); }
    public static double heavyMultiplier() { return d(HEAVY_MULT); }
    public static double heavyTwoHanderBonus() { return d(HEAVY_TWO_HANDER); }
    public static double heavyShieldShare() { return d(HEAVY_SHIELD_SHARE); }
    public static int guardTicks() { return i(GUARD_TICKS); }
    public static double glanceMultiplier() { return d(GLANCE_MULT); }
    public static int graceCap() { return i(GRACE_CAP); }
    public static int lockTicks() { return i(LOCK_TICKS); }
    public static int staggerTicks() { return i(STAGGER_TICKS); }
    public static double staggerLeaderMultiplier() { return d(STAGGER_LEADER_MULT); }
    public static double staggerSpeed() { return d(STAGGER_SPEED); }
    public static double parryKnockback() { return d(PARRY_KNOCKBACK); }
    public static int counterTicks() { return i(COUNTER_TICKS); }
    public static double counterMultiplier() { return d(COUNTER_MULT); }
    public static double npcShieldBonus() { return d(NPC_SHIELD_BONUS); }
    public static double npcLevelBonus() { return d(NPC_LEVEL_BONUS); }
    public static double npcMaxChance() { return d(NPC_MAX); }
    public static int npcCooldown() { return i(NPC_COOLDOWN); }
    public static boolean npcVsNpcs() { return b(NPC_VS_NPCS); }

    /** Heavy chance per swing by attacker rank (CIVILIAN and MILITIA never). */
    public static double heavyChance(NpcRanks.Rank rank) {
        return switch (rank) {
            case CIVILIAN, MILITIA -> 0.0;
            case SOLDIER -> d(HEAVY_SOLDIER);
            case KNIGHT -> d(HEAVY_KNIGHT);
            case VETERAN -> d(HEAVY_VETERAN);
            case LEADER -> d(HEAVY_LEADER);
        };
    }

    /** NPC parry chance per melee hit taken by defender rank, before the shield and level bonuses. */
    public static double npcParryChance(NpcRanks.Rank rank) {
        return switch (rank) {
            case CIVILIAN, MILITIA -> 0.0;
            case SOLDIER -> d(NPC_SOLDIER);
            case KNIGHT -> d(NPC_KNIGHT);
            case VETERAN -> d(NPC_VETERAN);
            case LEADER -> d(NPC_LEADER);
        };
    }

    /** Perfect window in ticks for a parry kind: a shield type ("light_shield", "medium_shield", "heavy_shield") or a blade class. */
    public static int window(String kind) {
        return switch (kind) {
            case "light_shield" -> i(WIN_LIGHT_SHIELD);
            case "heavy_shield" -> i(WIN_HEAVY_SHIELD);
            case "medium_shield", "shield" -> i(WIN_MEDIUM_SHIELD);
            case "dagger" -> i(WIN_DAGGER);
            case "sword" -> i(WIN_SWORD);
            case "longsword" -> i(WIN_LONGSWORD);
            case "axe" -> i(WIN_AXE);
            case "spear" -> i(WIN_SPEAR);
            default -> 0;
        };
    }

    /** The startup INFO line (spec, Logging): every number in use. */
    static void logInUse() {
        LotrLog.info("combat", "config: windup normal={} heavy={} reach_slack={} mounted_reach={}", windupNormal(), windupHeavy(),
                reachSlack(), mountedReach());
        LotrLog.info("combat", "config: heavy x{} chances soldier={} knight={} veteran={} leader={} two_hander=+{} shield_share={}",
                heavyMultiplier(), d(HEAVY_SOLDIER), d(HEAVY_KNIGHT), d(HEAVY_VETERAN), d(HEAVY_LEADER), heavyTwoHanderBonus(),
                heavyShieldShare());
        LotrLog.info("combat", "config: parry windows light={} medium={} heavy={} dagger={} sword={} longsword={} axe={} spear={} guard={} "
                        + "glance=x{} grace_cap={} lock={} stagger={} (leader x{}) counter={} ticks x{} knockback={}",
                i(WIN_LIGHT_SHIELD), i(WIN_MEDIUM_SHIELD), i(WIN_HEAVY_SHIELD), i(WIN_DAGGER), i(WIN_SWORD), i(WIN_LONGSWORD),
                i(WIN_AXE), i(WIN_SPEAR), guardTicks(), glanceMultiplier(), graceCap(), lockTicks(), staggerTicks(),
                staggerLeaderMultiplier(), counterTicks(), counterMultiplier(), parryKnockback());
        LotrLog.info("combat", "config: npc parry soldier={} knight={} veteran={} leader={} shield=+{} level=+{} max={} cooldown={} vs_npcs={}",
                d(NPC_SOLDIER), d(NPC_KNIGHT), d(NPC_VETERAN), d(NPC_LEADER), npcShieldBonus(), npcLevelBonus(), npcMaxChance(),
                npcCooldown(), npcVsNpcs());
    }
}
