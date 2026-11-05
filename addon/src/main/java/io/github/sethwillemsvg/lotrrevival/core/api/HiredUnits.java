package io.github.sethwillemsvg.lotrrevival.core.api;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Read-only view of the hiring project's {@code lotr_revival:unit} attachment (owner, level). Hiring is not merged yet; until
 * it calls {@link #set}, a fallback treats nothing as a hired unit except entities a game test marks with {@link #markForTest}.
 */
public final class HiredUnits {
    public interface Delegate {
        /** The owner of this hired unit, empty when it is not one. */
        Optional<UUID> owner(Entity entity);

        /** The unit's level, 1 to 5 (0 when not a unit). */
        int level(Entity entity);
    }

    public record Unit(UUID owner, int level) {}

    private static final Map<Entity, Unit> TEST_UNITS = new WeakHashMap<>();
    private static volatile Delegate delegate = new Delegate() {
        @Override
        public Optional<UUID> owner(Entity entity) {
            synchronized (TEST_UNITS) {
                Unit u = TEST_UNITS.get(entity);
                return u == null ? Optional.empty() : Optional.of(u.owner());
            }
        }

        @Override
        public int level(Entity entity) {
            synchronized (TEST_UNITS) {
                Unit u = TEST_UNITS.get(entity);
                return u == null ? 0 : u.level();
            }
        }
    };

    private HiredUnits() {}

    public static void set(Delegate d) {
        delegate = Objects.requireNonNull(d);
        LotrLog.info("core", "hired-unit hooks set to {}", d.getClass().getName());
    }

    public static boolean isUnit(Entity e) {
        return e != null && delegate.owner(e).isPresent();
    }

    public static Optional<UUID> owner(Entity e) {
        return e == null ? Optional.empty() : delegate.owner(e);
    }

    public static int level(Entity e) {
        return e == null ? 0 : delegate.level(e);
    }

    /** Game tests only (works with the fallback delegate): pretend this entity is a hired unit. */
    public static void markForTest(Entity e, UUID owner, int level) {
        synchronized (TEST_UNITS) {
            TEST_UNITS.put(e, new Unit(owner, level));
        }
    }
}
