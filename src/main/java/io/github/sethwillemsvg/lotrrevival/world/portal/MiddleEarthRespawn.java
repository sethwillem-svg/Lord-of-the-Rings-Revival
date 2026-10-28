package io.github.sethwillemsvg.lotrrevival.world.portal;

import java.util.Optional;
import net.minecraft.util.RandomSource;

/**
 * Pure decision logic for Middle-earth respawning, with every input injectable so GameTests do not need a
 * Middle-earth level. Coordinates are block-space x/z (horizontal distances only).
 */
public final class MiddleEarthRespawn {
    /** Random-spot attempts before falling back to the normal spot. */
    public static final int MAX_TRIES = 16;

    private MiddleEarthRespawn() {}

    /** Config numbers; built from {@code WorldConfig} in production. */
    public record Settings(boolean enabled, double bedDistance, double spawnDistance, double minOffset, double maxOffset) {}

    /** A horizontal point. */
    public record Spot(double x, double z) {
        public double distanceTo(Spot other) {
            return Math.hypot(x - other.x, z - other.z);
        }
    }

    /** Where to respawn. {@code spot} is the bed or spawn (normal) or the random offset spot (far). */
    public record Choice(Kind kind, Spot spot) {}

    public enum Kind {
        /** The valid Middle-earth bed. */
        BED,
        /** Middle-earth's spawn point. */
        SPAWN,
        /** A random spot near the death point after a far death. */
        FAR_OFFSET
    }

    /** Whether a random spot is dry land on the painted map. */
    @FunctionalInterface
    public interface LandCheck {
        boolean isLand(int x, int z);
    }

    /**
     * @param bed valid Middle-earth bed position, empty when the player has none there (an overworld bed counts as none)
     * @param spawn Middle-earth's spawn arrival point
     */
    public static Choice choose(
            Settings settings, Spot death, Optional<Spot> bed, Spot spawn, RandomSource random, LandCheck land) {
        Choice normal = bed.map(b -> new Choice(Kind.BED, b)).orElseGet(() -> new Choice(Kind.SPAWN, spawn));
        if (!settings.enabled()) {
            return normal;
        }
        boolean far = bed.isPresent()
                ? death.distanceTo(bed.get()) > settings.bedDistance()
                : death.distanceTo(spawn) > settings.spawnDistance();
        if (!far) {
            return normal;
        }
        double min = Math.max(0, settings.minOffset());
        double max = Math.max(min, settings.maxOffset());
        for (int i = 0; i < MAX_TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = min + random.nextDouble() * (max - min);
            int x = (int) Math.floor(death.x() + Math.cos(angle) * distance);
            int z = (int) Math.floor(death.z() + Math.sin(angle) * distance);
            if (land.isLand(x, z)) {
                return new Choice(Kind.FAR_OFFSET, new Spot(x + 0.5, z + 0.5));
            }
        }
        return normal;
    }
}
