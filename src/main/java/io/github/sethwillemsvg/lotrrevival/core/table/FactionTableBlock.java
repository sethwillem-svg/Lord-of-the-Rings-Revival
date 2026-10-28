package io.github.sethwillemsvg.lotrrevival.core.table;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A crafting table that only opens for players with enough alignment with its faction (or in creative/spectator).
 * The check is on the server; the client just reports success so the swing animation plays.
 */
public class FactionTableBlock extends Block {
    public static final MapCodec<FactionTableBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    propertiesCodec(),
                    ResourceKey.codec(ModDataRegistries.FACTION).fieldOf("faction").forGetter(b -> b.faction))
            .apply(i, FactionTableBlock::new));

    /** How a menu is shown to a player. Tests swap this because mock connections cannot take the open-screen packet. */
    @FunctionalInterface
    public interface MenuOpener {
        void open(ServerPlayer player, MenuProvider provider, BlockPos pos);
    }

    private static volatile MenuOpener opener = (player, provider, pos) -> player.openMenu(provider, pos);

    private final ResourceKey<Faction> faction;

    /** Test seam: replaces the menu opener, returns the previous one so it can be restored. */
    public static MenuOpener setOpener(MenuOpener replacement) {
        MenuOpener previous = opener;
        opener = replacement;
        return previous;
    }

    public FactionTableBlock(Properties properties, ResourceKey<Faction> faction) {
        super(properties);
        this.faction = faction;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public ResourceKey<Faction> faction() {
        return faction;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (FactionTableAccess.canUse(serverPlayer, faction)) {
                opener.open(
                        serverPlayer,
                        new SimpleMenuProvider(
                                (id, inventory, p) ->
                                        new FactionTableMenu(id, inventory, ContainerLevelAccess.create(level, pos), this),
                                getName()),
                        pos);
            } else {
                refuse((ServerLevel) level, pos, serverPlayer);
            }
        }
        return InteractionResult.CONSUME;
    }

    private void refuse(ServerLevel level, BlockPos pos, ServerPlayer player) {
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.25, 0.1, 0.25, 0.01);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.2F);
        player.displayClientMessage(
                Component.translatable(
                        "message.lotrrevival.faction_table.refused",
                        Component.translatable("faction.lotrrevival." + faction.location().getPath())),
                true);
    }
}
