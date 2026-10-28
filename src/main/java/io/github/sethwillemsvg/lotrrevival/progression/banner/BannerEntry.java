package io.github.sethwillemsvg.lotrrevival.progression.banner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * One protecting banner in the per-dimension index: where it stands, its design, its effective range and a copy of
 * its protection settings.
 */
public record BannerEntry(BlockPos pos, ResourceLocation design, int range, BannerSettings settings) {
    public static final Codec<BannerEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                    BlockPos.CODEC.fieldOf("pos").forGetter(BannerEntry::pos),
                    ResourceLocation.CODEC.fieldOf("design").forGetter(BannerEntry::design),
                    Codec.INT.fieldOf("range").forGetter(BannerEntry::range),
                    BannerSettings.CODEC.fieldOf("settings").forGetter(BannerEntry::settings))
            .apply(i, BannerEntry::new));

    /** Cube test: {@code |dx|, |dy|, |dz| <= range} from the banner block. */
    public boolean covers(BlockPos other) {
        return Math.abs(other.getX() - pos.getX()) <= range
                && Math.abs(other.getY() - pos.getY()) <= range
                && Math.abs(other.getZ() - pos.getZ()) <= range;
    }

    /** Whether the two cubes share at least one block. */
    public boolean overlaps(BlockPos otherPos, int otherRange) {
        int reach = range + otherRange;
        return Math.abs(otherPos.getX() - pos.getX()) <= reach
                && Math.abs(otherPos.getY() - pos.getY()) <= reach
                && Math.abs(otherPos.getZ() - pos.getZ()) <= reach;
    }
}
