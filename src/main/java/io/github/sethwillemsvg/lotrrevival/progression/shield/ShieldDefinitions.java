package io.github.sethwillemsvg.lotrrevival.progression.shield;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;

/** Looks up shield definitions in the synced {@code lotrrevival:shield} registry (both sides). */
public final class ShieldDefinitions {
    private ShieldDefinitions() {}

    public static Optional<ShieldDefinition> find(RegistryAccess access, ResourceLocation id) {
        return access.registry(ShieldDefinition.REGISTRY).map(registry -> registry.get(id));
    }

    /** Every definition by id, ordered by {@code sort} and then id. */
    public static Map<ResourceLocation, ShieldDefinition> all(RegistryAccess access) {
        Map<ResourceLocation, ShieldDefinition> out = new LinkedHashMap<>();
        Registry<ShieldDefinition> registry =
                access.registry(ShieldDefinition.REGISTRY).orElse(null);
        if (registry != null) {
            registry.keySet().stream()
                    .sorted(Comparator.<ResourceLocation>comparingInt(id -> registry.get(id).sort())
                            .thenComparing(Comparator.naturalOrder()))
                    .forEach(id -> out.put(id, registry.get(id)));
        }
        return out;
    }
}
