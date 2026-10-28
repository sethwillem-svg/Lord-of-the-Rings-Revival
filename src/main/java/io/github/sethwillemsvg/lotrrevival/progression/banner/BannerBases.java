package io.github.sethwillemsvg.lotrrevival.progression.banner;

import net.minecraft.world.level.block.state.BlockState;

/** Protection range by the block a free-standing banner stands on. */
public final class BannerBases {
    public static final int BRONZE_RANGE = 8;
    public static final int SILVER_RANGE = 16;
    public static final int GOLD_RANGE = 32;
    /** Largest range any banner can have; also the search distance of protection queries. */
    public static final int MAX_RANGE = 64;

    private BannerBases() {}

    /** Bronze 8, silver 16, gold 32, anything else 0. The largest matching tag wins. */
    public static int rangeOf(BlockState base) {
        if (base.is(BannerTags.BASE_GOLD)) {
            return GOLD_RANGE;
        }
        if (base.is(BannerTags.BASE_SILVER)) {
            return SILVER_RANGE;
        }
        if (base.is(BannerTags.BASE_BRONZE)) {
            return BRONZE_RANGE;
        }
        return 0;
    }
}
