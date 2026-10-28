package io.github.sethwillemsvg.lotrrevival.progression.banner;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The free-standing faction banner: a pole three blocks tall (drawn by the block entity renderer), with collision on
 * its own block only. Only this form can protect land; the block under it sets the range.
 */
public class FactionBannerBlock extends BaseEntityBlock {
    public static final MapCodec<FactionBannerBlock> CODEC = simpleCodec(FactionBannerBlock::new);
    private static final VoxelShape SHAPE = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

    public FactionBannerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FactionBannerBlockEntity(pos, state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        if (direction == Direction.DOWN) {
            if (!canSurvive(state, level, pos)) {
                return Blocks.AIR.defaultBlockState();
            }
            // The base block changed but still holds the banner up: its range may have changed.
            level.scheduleTick(pos, this, 1);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof FactionBannerBlockEntity banner) {
            banner.reindex();
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Only the owner takes the settings along; anyone else (allowed to break it) gets a plain banner.
        if (level.getBlockEntity(pos) instanceof FactionBannerBlockEntity banner) {
            banner.setDropSettings(banner.settings().isOwner(player.getUUID()));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            BannerIndex.get(serverLevel).remove(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel) || !(level.getBlockEntity(pos) instanceof FactionBannerBlockEntity banner)) {
            return;
        }
        if (placer instanceof Player player) {
            BannerSettings carried = stack.get(BannerComponents.PROTECTION.get());
            BannerSettings settings;
            if (carried != null && carried.isOwner(player.getUUID())) {
                settings = carried;
            } else {
                // A fresh banner, or one made by another player: the placer becomes the owner of default settings.
                settings = BannerSettings.forOwner(player);
            }
            banner.initialise(banner.design(), settings);
            if (player instanceof ServerPlayer serverPlayer && banner.effectiveRange() > 0) {
                BannerAchievements.onProtectingBannerPlaced(serverPlayer);
            }
        } else {
            banner.reindex();
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(BannerBlocks.ITEM.get());
        if (level.getBlockEntity(pos) instanceof FactionBannerBlockEntity banner) {
            stack.set(BannerComponents.DESIGN.get(), banner.design());
        }
        return stack;
    }
}
