package io.github.sethwillemsvg.lotrrevival.progression.banner;

import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The rules for placing a free-standing banner. */
public final class BannerPlacement {
    /** Alignment with the banner's faction a survival player needs to raise a protecting banner. */
    public static final float REQUIRED_ALIGNMENT = 1.0f;

    private BannerPlacement() {}

    /**
     * The range the banner would have at {@code pos}: the operator range carried by the item (only when the placer
     * owns those settings), else the range of the block under it.
     */
    public static int rangeFor(Level level, BlockPos pos, ItemStack stack, @Nullable Player placer) {
        BannerSettings carried = stack.get(BannerComponents.PROTECTION.get());
        if (carried != null && carried.customRange() > 0 && placer != null && carried.isOwner(placer.getUUID())) {
            return carried.customRange();
        }
        return BannerBases.rangeOf(level.getBlockState(pos.below()));
    }

    /**
     * Why a standing banner cannot be placed at {@code pos}, or empty if it can: the two blocks above must be
     * replaceable; a protecting banner needs +1 alignment (survival and adventure) and its cube must not overlap
     * land where an existing banner denies the placer Full permission.
     */
    public static Optional<Component> refusal(
            Level level, BlockPos pos, ResourceLocation design, int range, @Nullable Player placer) {
        for (int up = 1; up <= 2; up++) {
            BlockPos above = pos.above(up);
            if (level.isOutsideBuildHeight(above.getY()) || !level.getBlockState(above).canBeReplaced()) {
                return Optional.of(Component.translatable("banner.lotrrevival.need_space"));
            }
        }
        if (range <= 0 || !BannerProtection.enabled() || !(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        Optional<ResourceKey<Faction>> faction = BannerProtection.factionOfDesign(level, design);
        if (faction.isEmpty() || (placer != null && placer.isCreative())) {
            return Optional.empty();
        }
        if (placer == null) {
            // A dispenser or other automation: it has no alignment, so it may only claim land nobody holds.
            return BannerProtection.active(serverLevel, BannerIndex.get(serverLevel).overlapping(pos, range)).isEmpty()
                    ? Optional.empty()
                    : Optional.of(Component.translatable("banner.lotrrevival.already_protected"));
        }
        if (Alignment.get(placer, faction.get()) < REQUIRED_ALIGNMENT) {
            return Optional.of(Component.translatable(
                    "banner.lotrrevival.need_alignment", Component.translatable(FactionBannerItem.factionKey(design))));
        }
        for (BannerEntry other : BannerProtection.active(
                serverLevel, BannerIndex.get(serverLevel).overlapping(pos, range))) {
            if (!BannerProtection.entryAllows(level, other, placer, BannerPermission.FULL)) {
                return Optional.of(Component.translatable("banner.lotrrevival.already_protected"));
            }
        }
        return Optional.empty();
    }
}
