package io.github.sethwillemsvg.lotrrevival.world.portal;

import io.github.sethwillemsvg.lotrrevival.core.building.GoldRingItems;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import io.github.sethwillemsvg.lotrrevival.world.WorldConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Entry point of the ring portal: the catalyst check (a gold ring item touching fire), the once-per-world data on
 * the overworld, and the English strings.
 */
public final class RingPortals {
    /** A gold ring must have a player this close (blocks) when it touches fire. */
    public static final double PLAYER_RANGE = 16.0;
    /** Power of the blast that opens the portal. */
    public static final float BLAST_POWER = 3.0f;

    private RingPortals() {}

    /** Called once from {@code WorldMod.init}. */
    public static void init() {
        RingPortalTypes.init();
    }

    /** The ring portal data; always read from and written to the overworld. */
    public static RingPortalData data(ServerLevel anyLevel) {
        return anyLevel.getServer().overworld().getData(RingPortalTypes.DATA);
    }

    public static void setData(ServerLevel anyLevel, RingPortalData data) {
        anyLevel.getServer().overworld().setData(RingPortalTypes.DATA, data);
    }

    /**
     * Checks one item entity. Cheap on the common path: the item is looked at first. When it is a gold ring
     * touching fire with a player near and no portal ever made, the ring is consumed and the portal opens.
     *
     * @return true when a portal was made
     */
    public static boolean tryOpen(ItemEntity item) {
        if (!item.getItem().is(GoldRingItems.GOLD_RING.get())) {
            return false;
        }
        if (!(item.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD || !item.isAlive()) {
            return false;
        }
        if (data(level).made()) {
            return false;
        }
        Optional<BlockPos> fire = findFire(level, item);
        if (fire.isEmpty()) {
            return false;
        }
        if (level.getNearestPlayer(item.getX(), item.getY(), item.getZ(), PLAYER_RANGE, false) == null) {
            return false;
        }

        // Consume one ring, set the flag first so nothing can open a second portal this tick.
        item.getItem().shrink(1);
        if (item.getItem().isEmpty()) {
            item.discard();
        }
        BlockPos base = fire.get();
        RingPortalEntity portal = RingPortalTypes.RING_PORTAL.get().create(level);
        portal.setPos(base.getX() + 0.5, base.getY() + RingPortalEntity.BOTTOM_ABOVE_BASE, base.getZ() + 0.5);
        BlockPos centre = new BlockPos(base.getX(), base.getY() + 3, base.getZ());
        setData(level, data(level).withOverworld(centre, portal.getUUID()));

        Level.ExplosionInteraction interaction = WorldConfig.get(WorldConfig.PORTAL_BLAST_BREAKS_BLOCKS)
                ? Level.ExplosionInteraction.BLOCK
                : Level.ExplosionInteraction.NONE;
        level.explode(null, item.getX(), item.getY(), item.getZ(), BLAST_POWER, interaction);
        level.addFreshEntity(portal);
        return true;
    }

    /** The fire, soul fire, lit campfire or lava block the item is touching. */
    static Optional<BlockPos> findFire(Level level, ItemEntity item) {
        var box = item.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(
                (int) Math.floor(box.minX),
                (int) Math.floor(box.minY),
                (int) Math.floor(box.minZ),
                (int) Math.floor(box.maxX),
                (int) Math.floor(box.maxY),
                (int) Math.floor(box.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.is(Blocks.FIRE)
                    || state.is(Blocks.SOUL_FIRE)
                    || (state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT))
                    || state.getFluidState().is(FluidTags.LAVA)) {
                return Optional.of(pos.immutable());
            }
        }
        return Optional.empty();
    }

    /** English strings; called from datagen. */
    public static void contributeLang() {
        ModLangProvider.contribute(lang -> {
            lang.addEntityType(RingPortalTypes.RING_PORTAL, "Ring Portal");
            lang.add("commands.lotrrevival.portal.reset", "The ring portal can be made again");
            lang.add("commands.lotrrevival.portal.info.flag", "Ring portal made: %s");
            lang.add("commands.lotrrevival.portal.info.overworld", "Overworld portal: %s");
            lang.add("commands.lotrrevival.portal.info.middle_earth", "Middle-earth portal: %s");
            lang.add("commands.lotrrevival.portal.info.none", "none");
        });
    }
}
