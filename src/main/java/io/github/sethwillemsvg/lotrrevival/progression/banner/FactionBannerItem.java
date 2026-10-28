package io.github.sethwillemsvg.lotrrevival.progression.banner;

import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The faction banner item: places the standing form on top of a block or the wall form on a side face. A standing
 * banner is checked against the placement rules first (space, alignment, overlap).
 */
public class FactionBannerItem extends StandingAndWallBlockItem {
    public FactionBannerItem(Block standing, Block wall, Properties properties) {
        super(standing, wall, properties, Direction.DOWN);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation design = stack.get(BannerComponents.DESIGN.get());
        if (design == null) {
            return super.getName(stack);
        }
        return Component.translatable(
                "item.lotrrevival.faction_banner.named", Component.translatable(factionKey(design)));
    }

    /** {@code faction.<namespace>.<path>}: the design id of a shipped design is its faction id. */
    public static String factionKey(ResourceLocation design) {
        return "faction." + design.getNamespace() + "." + design.getPath();
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!context.getLevel().isClientSide()) {
            BlockState state = getPlacementState(context);
            if (state != null && state.getBlock() instanceof FactionBannerBlock) {
                ItemStack stack = context.getItemInHand();
                ResourceLocation design = BannerComponents.designOf(stack, FactionBannerBlockEntity.FALLBACK_DESIGN);
                int range = BannerPlacement.rangeFor(context.getLevel(), context.getClickedPos(), stack, context.getPlayer());
                Optional<Component> refusal = BannerPlacement.refusal(
                        context.getLevel(), context.getClickedPos(), design, range, context.getPlayer());
                if (refusal.isPresent()) {
                    if (context.getPlayer() != null) {
                        context.getPlayer().displayClientMessage(refusal.get(), true);
                    }
                    return InteractionResult.FAIL;
                }
            }
        }
        return super.place(context);
    }
}
