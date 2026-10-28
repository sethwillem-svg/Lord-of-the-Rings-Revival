package io.github.sethwillemsvg.lotrrevival.progression.banner;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags of the banner system. Members come from the banner datagen. */
public final class BannerTags {
    /** {@code #lotrrevival:banner_base/bronze}: bronze storage blocks (filled when Core adds them). */
    public static final TagKey<Block> BASE_BRONZE = tag("banner_base/bronze");
    /** {@code #lotrrevival:banner_base/silver}: silver storage blocks (filled when Core adds them). */
    public static final TagKey<Block> BASE_SILVER = tag("banner_base/silver");
    /** {@code #lotrrevival:banner_base/gold}. */
    public static final TagKey<Block> BASE_GOLD = tag("banner_base/gold");

    private BannerTags() {}

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, LOTRRevival.id(path));
    }
}
