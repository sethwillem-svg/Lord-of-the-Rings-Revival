package io.github.sethwillemsvg.lotrrevival.structures.venue;

import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A structure start usable as an event venue. {@code centre}: x and z of the bounds' centre, y = bounds.minY +
 * structure_info ground_offset.
 */
public record Venue(ResourceLocation structureId, Set<VenueKind> kinds, BoundingBox bounds, BlockPos centre,
                    Optional<ResourceLocation> faction, boolean residents, ChunkPos startChunk, boolean complete,
                    Optional<String> buildId) {}
