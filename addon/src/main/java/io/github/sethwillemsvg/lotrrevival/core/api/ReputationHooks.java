package io.github.sethwillemsvg.lotrrevival.core.api;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seam between the nemesis and world-event code and the hiring project's reputation (specs/addon/hiring-reputation.md). The
 * hiring code is not merged yet: until it calls {@link #set}, a fallback keeps reputation in memory (so tests and the enemy
 * check work), logs every call, and knows the sworn-enemy table from the hiring spec.
 */
public final class ReputationHooks {
    public interface Delegate {
        /** Hiring's kill rule: {@code Reputation.onKill(killer, victim, multiplier)}. */
        void onKill(LivingEntity killer, LivingEntity victim, int multiplier);

        /** The player's add-on reputation with a faction (0 when none). */
        int reputation(ServerPlayer player, ResourceLocation faction);

        /** Plain add (world-event win bonuses; spec open question 1). */
        void add(ServerPlayer player, ResourceLocation faction, int amount, String source);

        /** The faction's sworn enemy from hiring's {@code sworn_enemies} data, or null. */
        @Nullable
        ResourceLocation swornEnemy(ResourceLocation faction);
    }

    private static volatile Delegate delegate = new Fallback();

    private ReputationHooks() {}

    public static Delegate get() {
        return delegate;
    }

    /** Shortcut for readers that only need a score (node map). */
    public static int reputation(ServerPlayer player, ResourceLocation faction) {
        return delegate.reputation(player, faction);
    }

    public static void set(Delegate d) {
        delegate = Objects.requireNonNull(d);
        LotrLog.info("core", "reputation hooks set to {}", d.getClass().getName());
    }

    public static boolean isFallback() {
        return delegate instanceof Fallback;
    }

    /** In-memory stand-in until hiring lands; also what game tests use to set a player's reputation. */
    public static final class Fallback implements Delegate {
        private static final Map<UUID, Map<ResourceLocation, Integer>> REP = new ConcurrentHashMap<>();
        private static final Map<String, String> SWORN = Map.ofEntries(
                Map.entry("gondor", "mordor"), Map.entry("mordor", "gondor"),
                Map.entry("rohan", "isengard"), Map.entry("isengard", "rohan"),
                Map.entry("longbeards.erebor", "hobgoblin_tribes.gundabad"), Map.entry("longbeards", "hobgoblin_tribes.gundabad"),
                Map.entry("hobgoblin_tribes.gundabad", "longbeards.erebor"), Map.entry("hobgoblin_tribes", "longbeards.erebor"),
                Map.entry("moria", "longbeards.erebor"), Map.entry("lothlorien", "moria"),
                Map.entry("woodland_realm", "goblin_town"), Map.entry("goblin_town", "woodland_realm"),
                Map.entry("dale", "hobgoblin_tribes.gundabad"), Map.entry("shire", "mordor"));

        /** Kill calls seen (game tests count them). */
        public static final java.util.concurrent.atomic.AtomicInteger KILL_CALLS = new java.util.concurrent.atomic.AtomicInteger();
        public static volatile int lastMultiplier;

        @Override
        public void onKill(LivingEntity killer, LivingEntity victim, int multiplier) {
            KILL_CALLS.incrementAndGet();
            lastMultiplier = multiplier;
            LotrLog.dbg("reputation", "onKill (fallback, hiring not wired): killer={} victim={} x{}",
                    LotrLog.id(killer == null ? null : killer.getUUID()), LotrLog.id(victim.getUUID()), multiplier);
        }

        @Override
        public int reputation(ServerPlayer player, ResourceLocation faction) {
            return REP.getOrDefault(player.getUUID(), Map.of()).getOrDefault(faction, 0);
        }

        @Override
        public void add(ServerPlayer player, ResourceLocation faction, int amount, String source) {
            int now = REP.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>()).merge(faction, amount, Integer::sum);
            LotrLog.dbg("reputation", "add (fallback): player={} faction={} {}{} -> {} source={}",
                    LotrLog.id(player.getUUID()), faction, amount >= 0 ? "+" : "", amount, now, source);
        }

        @Override
        public @Nullable ResourceLocation swornEnemy(ResourceLocation faction) {
            String e = SWORN.get(faction.getPath());
            return e == null ? null : ResourceLocation.fromNamespaceAndPath(faction.getNamespace(), e);
        }

        /** Test helper: set a reputation value outright. */
        public static void setForTest(UUID player, ResourceLocation faction, int value) {
            REP.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(faction, value);
        }

        public static void clearForTest(UUID player) {
            REP.remove(player);
        }
    }
}
