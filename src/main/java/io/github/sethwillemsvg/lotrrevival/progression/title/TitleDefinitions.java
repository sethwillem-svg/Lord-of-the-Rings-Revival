package io.github.sethwillemsvg.lotrrevival.progression.title;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

/**
 * Looks up title definitions by id. Reads the synced {@code lotrrevival:title} registry; GameTests may layer
 * extra definitions on top (ids that only exist in a test) through {@link #withOverrides}.
 */
public final class TitleDefinitions {
    private static final Map<ResourceLocation, TitleDefinition> OVERRIDES = new HashMap<>();

    private TitleDefinitions() {}

    public static Optional<TitleDefinition> find(RegistryAccess access, ResourceLocation id) {
        TitleDefinition override = OVERRIDES.get(id);
        if (override != null) {
            return Optional.of(override);
        }
        return access.registry(TitleDefinition.REGISTRY).map(registry -> registry.get(id));
    }

    /** Every definition by id: the registry first, then overrides on top. */
    public static Map<ResourceLocation, TitleDefinition> all(RegistryAccess access) {
        Map<ResourceLocation, TitleDefinition> out = new LinkedHashMap<>();
        Registry<TitleDefinition> registry =
                access.registry(TitleDefinition.REGISTRY).orElse(null);
        if (registry != null) {
            for (ResourceLocation id : registry.keySet()) {
                out.put(id, registry.get(id));
            }
        }
        out.putAll(OVERRIDES);
        return out;
    }

    /** Test seam: adds definitions that take precedence over the registry until {@link #clearOverrides()}. */
    public static void withOverrides(Map<ResourceLocation, TitleDefinition> definitions) {
        OVERRIDES.putAll(definitions);
    }

    public static void clearOverrides() {
        OVERRIDES.clear();
    }
}
