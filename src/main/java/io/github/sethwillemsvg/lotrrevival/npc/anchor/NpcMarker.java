package io.github.sethwillemsvg.lotrrevival.npc.anchor;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An NPC spawn marker as written by structures: structure block DATA metadata of the form
 * {@code lotrrevival:npc <type> [role=<role>] [count=<n>] [home=<radius>]}.
 *
 * <p>Parsing is strict: anything that is not understood logs a warning and rejects the whole marker, so a typo in a
 * structure is found instead of silently spawning the wrong thing.
 */
public record NpcMarker(EntityType<?> type, Optional<NpcRole> role, int count, int homeRadius) {
    /** The metadata keyword that marks an NPC spawn marker. */
    public static final String KEYWORD = "lotrrevival:npc";

    public static final int DEFAULT_COUNT = 1;
    public static final int DEFAULT_HOME_RADIUS = 16;
    /** Upper bound on {@code count}, to keep a typo from spawning a crowd. */
    public static final int MAX_COUNT = 32;

    private static final Logger LOGGER = LoggerFactory.getLogger(NpcMarker.class);

    /** Whether the metadata is an NPC marker at all (other metadata is not ours and is ignored without a warning). */
    public static boolean isNpcMarker(String metadata) {
        String trimmed = metadata.trim();
        return trimmed.equals(KEYWORD) || trimmed.startsWith(KEYWORD + " ");
    }

    /** Parses marker metadata; empty (after a warning) when it is not a valid NPC marker. */
    public static Optional<NpcMarker> parse(String metadata) {
        return parse(metadata, message -> LOGGER.warn("{}", message));
    }

    /** Like {@link #parse(String)}, reporting each rejection reason to {@code warn} instead of the log. */
    public static Optional<NpcMarker> parse(String metadata, Consumer<String> warn) {
        if (!isNpcMarker(metadata)) {
            return Optional.empty();
        }
        String[] tokens = metadata.trim().split("\\s+");
        if (tokens.length < 2) {
            return reject(warn, metadata, "missing entity type");
        }
        ResourceLocation id = ResourceLocation.tryParse(tokens[1].contains(":") ? tokens[1] : LOTRRevival.MOD_ID + ":" + tokens[1]);
        Optional<EntityType<?>> type = id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty() || !LOTRRevival.MOD_ID.equals(id.getNamespace())) {
            return reject(warn, metadata, "unknown NPC type '" + tokens[1] + "'");
        }
        Optional<NpcRole> role = Optional.empty();
        int count = DEFAULT_COUNT;
        int home = DEFAULT_HOME_RADIUS;
        for (int i = 2; i < tokens.length; i++) {
            int eq = tokens[i].indexOf('=');
            if (eq <= 0 || eq == tokens[i].length() - 1) {
                return reject(warn, metadata, "malformed option '" + tokens[i] + "'");
            }
            String key = tokens[i].substring(0, eq);
            String value = tokens[i].substring(eq + 1);
            switch (key) {
                case "role" -> {
                    role = NpcRole.byName(value);
                    if (role.isEmpty()) {
                        return reject(warn, metadata, "unknown role '" + value + "'");
                    }
                }
                case "count" -> {
                    Integer parsed = parseInt(value);
                    if (parsed == null || parsed < 1 || parsed > MAX_COUNT) {
                        return reject(warn, metadata, "count must be 1 to " + MAX_COUNT + ", got '" + value + "'");
                    }
                    count = parsed;
                }
                case "home" -> {
                    Integer parsed = parseInt(value);
                    if (parsed == null || parsed < 1) {
                        return reject(warn, metadata, "home must be a positive number, got '" + value + "'");
                    }
                    home = parsed;
                }
                default -> {
                    return reject(warn, metadata, "unknown option '" + key + "'");
                }
            }
        }
        return Optional.of(new NpcMarker(type.get(), role, count, home));
    }

    private static Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Optional<NpcMarker> reject(Consumer<String> warn, String metadata, String reason) {
        warn.accept("Ignoring NPC spawn marker '" + metadata + "': " + reason);
        return Optional.empty();
    }
}
