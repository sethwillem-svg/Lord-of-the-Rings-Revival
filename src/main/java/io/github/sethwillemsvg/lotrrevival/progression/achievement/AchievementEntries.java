package io.github.sethwillemsvg.lotrrevival.progression.achievement;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

/**
 * Looks up display entries by id. Reads the synced {@code lotrrevival:achievement} registry; GameTests may
 * layer extra entries on top (ids that only exist in a test) through {@link #withOverrides}.
 */
public final class AchievementEntries {
    private static final Map<ResourceLocation, AchievementEntry> OVERRIDES = new HashMap<>();

    private AchievementEntries() {}

    public static Optional<AchievementEntry> find(RegistryAccess access, ResourceLocation id) {
        AchievementEntry override = OVERRIDES.get(id);
        if (override != null) {
            return Optional.of(override);
        }
        return access.registry(AchievementEntry.REGISTRY).map(registry -> registry.get(id));
    }

    /** Test seam: adds entries that take precedence over the registry until {@link #clearOverrides()}. */
    public static void withOverrides(Map<ResourceLocation, AchievementEntry> entries) {
        OVERRIDES.putAll(entries);
    }

    public static void clearOverrides() {
        OVERRIDES.clear();
    }
}
