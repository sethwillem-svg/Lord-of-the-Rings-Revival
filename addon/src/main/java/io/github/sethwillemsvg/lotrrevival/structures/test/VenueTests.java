package io.github.sethwillemsvg.lotrrevival.structures.test;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.venue.StructureVenues;
import io.github.sethwillemsvg.lotrrevival.structures.venue.Venue;
import io.github.sethwillemsvg.lotrrevival.structures.venue.VenueIndex;
import io.github.sethwillemsvg.lotrrevival.structures.venue.VenueKind;
import io.github.sethwillemsvg.lotrrevival.structures.venue.VenueQuery;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The venue index and lookups on a private index (so the test level need not be a venue dimension). The entry stands for
 * a start of the placeholder structure far from the test area: 2 x 2 chunks, at chunk (6400, 6400).
 */
@GameTestHolder(LotrRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VenueTests {
    private VenueTests() {}

    static final ResourceLocation PLACEHOLDER = LotrRevival.id("placeholder_watchtower");
    static final ChunkPos START = new ChunkPos(6400, 6400);
    static final BoundingBox BOX = new BoundingBox(START.getMinBlockX() + 10, 60, START.getMinBlockZ() + 10,
            START.getMinBlockX() + 20, 70, START.getMinBlockZ() + 20);   // spans chunks 6400..6401 on both axes

    static VenueIndex indexWithPlaceholder() {
        VenueIndex idx = new VenueIndex();
        idx.add(PLACEHOLDER, START, BOX);
        return idx;
    }

    @GameTest(template = "empty")
    public static void ingestAndFind(GameTestHelper h) {
        TestRun.run(h, "ingestAndFind", () -> {
            ServerLevel level = h.getLevel();
            VenueIndex idx = indexWithPlaceholder();
            idx.markChunk(6400, 6400);
            idx.markChunk(6401, 6400);
            BlockPos from = new BlockPos(BOX.getCenter().getX() + 100, 64, BOX.getCenter().getZ());
            TestRun.check(StructureVenues.find(level, idx, from, 50, 150, VenueQuery.of(VenueKind.WATCHTOWER)).isEmpty(),
                    "an incomplete venue was returned");
            TestRun.check(StructureVenues.find(level, idx, from, 50, 150, VenueQuery.of(VenueKind.WATCHTOWER).withIncomplete()).size() == 1,
                    "includeIncomplete did not return the venue");
            idx.markChunk(6400, 6401);
            idx.markChunk(6401, 6401);
            List<Venue> found = StructureVenues.find(level, idx, from, 50, 150, VenueQuery.of(VenueKind.WATCHTOWER));
            TestRun.check(found.size() == 1, "complete venue not found: " + found);
            Venue v = found.get(0);
            TestRun.check(v.kinds().contains(VenueKind.WATCHTOWER), "kinds " + v.kinds());
            TestRun.check(v.faction().equals(Optional.of(ResourceLocation.fromNamespaceAndPath("middle-earth", "gondor"))),
                    "faction " + v.faction());
            TestRun.check(v.residents(), "residents false");
            TestRun.check(v.centre().getY() == BOX.minY() + 2, "centre y " + v.centre().getY() + " (ground offset 2 expected)");
            TestRun.check(StructureVenues.find(level, idx, from, 150, 300, VenueQuery.of(VenueKind.WATCHTOWER)).isEmpty(),
                    "found outside the band");
            TestRun.check(StructureVenues.find(level, idx, from, 50, 150, VenueQuery.of(VenueKind.CAMP)).isEmpty(),
                    "found as a camp");
            TestRun.check(StructureVenues.at(level, idx, BOX.getCenter()).isPresent(), "at(pos) inside the bounds found nothing");
            TestRun.check(StructureVenues.at(level, idx, BOX.getCenter().offset(40, 0, 0)).isEmpty(), "at(pos) outside found one");
            LotrLog.info("test", "ingestAndFind: {} at {}", v.structureId(), v.centre().toShortString());
        });
    }

    @GameTest(template = "empty")
    public static void persistence(GameTestHelper h) {
        TestRun.run(h, "persistence", () -> {
            var registries = h.getLevel().registryAccess();
            VenueIndex idx = indexWithPlaceholder();
            idx.markChunk(6400, 6400);
            idx.add(LotrRevival.id("no_such_structure"), new ChunkPos(6500, 6500),
                    new BoundingBox(104000, 60, 104000, 104010, 70, 104010));
            CompoundTag saved = idx.save(new CompoundTag(), registries);
            VenueIndex back = VenueIndex.load(saved, registries);
            TestRun.check(back.size() == 1, "expected 1 entry after load (unknown dropped), got " + back.size());
            List<Venue> found = StructureVenues.find(h.getLevel(), back, BOX.getCenter(), 0, 64,
                    VenueQuery.of(VenueKind.WATCHTOWER).withIncomplete());
            TestRun.check(found.size() == 1 && !found.get(0).complete(), "the entry's bits did not survive: " + found);
        });
    }

    @GameTest(template = "empty")
    public static void neverLoadsChunks(GameTestHelper h) {
        TestRun.run(h, "neverLoadsChunks", () -> {
            ServerLevel level = h.getLevel();
            VenueIndex idx = indexWithPlaceholder();
            int before = level.getChunkSource().getLoadedChunksCount();
            BlockPos far = new BlockPos(BOX.getCenter().getX() + 300, 64, BOX.getCenter().getZ() + 300);
            StructureVenues.find(level, idx, far, 0, 2000, VenueQuery.of().withIncomplete());
            StructureVenues.at(level, idx, far);
            StructureVenues.find(level, far, 0, 2000, VenueQuery.of());   // the level's own index (empty here)
            int after = level.getChunkSource().getLoadedChunksCount();
            TestRun.check(after == before, "loaded chunks went from " + before + " to " + after);
        });
    }
}
