package io.github.sethwillemsvg.lotrrevival.core.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * Makes the space around a GameTest entity ticking before the test body runs.
 *
 * <p>The {@code empty} template is 1x1x1, and the GameTest framework only force-loads the chunks its box touches.
 * A test that puts entities a few blocks away can land in the next chunk, which is then only block ticking (or not
 * yet loaded): entities there do not tick and may not even be found by id. Whether that happens depends on where the
 * random test grid falls, which made several tests fail at random. This helper adds a short-lived ticket to every
 * chunk within {@code radius} blocks of the test origin and waits until they are all entity ticking.
 */
public final class TestArea {
    /** Expires on its own, so nothing has to be released and other tests' chunks are never unforced. */
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("lotrrevival_gametest_area", Comparator.comparingLong(ChunkPos::toLong), 600);

    /** Ticks to wait for the chunks before failing the test. */
    private static final int MAX_WAIT = 60;

    private TestArea() {}

    /**
     * Keeps every chunk within {@code radius} blocks (horizontally) of the test origin entity ticking, then runs
     * {@code body}: at once when they already are, otherwise on the first tick they all are.
     */
    public static void whenTicking(GameTestHelper helper, int radius, Runnable body) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        List<ChunkPos> chunks = new ArrayList<>();
        for (int cx = (origin.getX() - radius) >> 4; cx <= (origin.getX() + radius) >> 4; cx++) {
            for (int cz = (origin.getZ() - radius) >> 4; cz <= (origin.getZ() + radius) >> 4; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                chunks.add(pos);
                level.getChunkSource().addRegionTicket(TICKET, pos, 2, pos);
            }
        }
        await(helper, level, chunks, MAX_WAIT, body);
    }

    private static void await(
            GameTestHelper helper, ServerLevel level, List<ChunkPos> chunks, int ticksLeft, Runnable body) {
        boolean ready = true;
        for (ChunkPos pos : chunks) {
            if (!level.isPositionEntityTicking(pos.getWorldPosition())) {
                ready = false;
                break;
            }
        }
        if (ready) {
            body.run();
            return;
        }
        if (ticksLeft <= 0) {
            helper.fail("Test area chunks never became entity ticking");
            return;
        }
        helper.runAfterDelay(1, () -> await(helper, level, chunks, ticksLeft - 1, body));
    }
}
