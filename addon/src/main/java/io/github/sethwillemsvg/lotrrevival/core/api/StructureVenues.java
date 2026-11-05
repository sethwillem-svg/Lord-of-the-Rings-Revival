package io.github.sethwillemsvg.lotrrevival.core.api;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * World-event venues from structure tags (specs/addon/structures.md, being written). The structures code will plug a lookup
 * over loaded chunks in here; until then every lookup finds nothing and events fall back to open ground (and raids, which
 * need a {@code village}, are never picked).
 */
public final class StructureVenues {
    /** Kinds this first set of events uses. */
    public static final List<String> KINDS = List.of("outpost", "camp", "ruin", "bridge", "village");

    /** A structure start usable as a stage: id, centre, bounds, owning faction (null when unowned), kind. */
    public record Venue(ResourceLocation structure, BlockPos centre, BoundingBox bounds, @Nullable ResourceLocation owner,
                        String kind) {}

    public interface Lookup {
        /**
         * Structure starts of this kind in already loaded chunks between {@code minDist} and {@code maxDist} blocks of
         * {@code from}. Must never load or generate chunks and must stop after {@code maxChunkChecks} chunks.
         */
        List<Venue> find(ServerLevel level, BlockPos from, int minDist, int maxDist, String kind, int maxChunkChecks);
    }

    private static volatile boolean warned;
    private static volatile Lookup lookup = (level, from, min, max, kind, budget) -> {
        if (!warned) {
            warned = true;
            LotrLog.warn("events", "no structure venue lookup registered (structures.md not merged): open-ground events only");
        }
        return List.of();
    };

    private StructureVenues() {}

    public static Lookup get() {
        return lookup;
    }

    public static void set(Lookup l) {
        lookup = Objects.requireNonNull(l);
        LotrLog.info("core", "structure venue lookup set to {}", l.getClass().getName());
    }
}
