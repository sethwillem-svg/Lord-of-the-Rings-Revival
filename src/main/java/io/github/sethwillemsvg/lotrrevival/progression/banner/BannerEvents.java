package io.github.sethwillemsvg.lotrrevival.progression.banner;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Game-bus listeners that enforce banner protection on players, spawns and explosions. */
@EventBusSubscriber(modid = LOTRRevival.MOD_ID)
public final class BannerEvents {
    private BannerEvents() {}

    // ------------------------------------------------------------------ players: blocks

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        BlockPos pos = event.getPos();
        if (BannerProtection.selfProtected(level, pos, player)) {
            event.setCanceled(true);
            BannerProtection.warn(player, Optional.ofNullable(BannerProtection.banner(level, pos)));
            return;
        }
        Optional<BannerEntry> denied = BannerProtection.deniedBy(level, pos, player, BannerPermission.FULL);
        if (denied.isPresent()) {
            event.setCanceled(true);
            BannerProtection.warn(player, denied);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !(event.getLevel() instanceof Level level)
                || level.isClientSide()) {
            return;
        }
        Optional<BannerEntry> denied = BannerProtection.deniedBy(level, event.getPos(), player, BannerPermission.FULL);
        if (denied.isPresent()) {
            event.setCanceled(true);
            BannerProtection.warn(player, denied);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (level.isClientSide() || player.isCreative()) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockPos adjacent = pos.relative(event.getFace() == null ? net.minecraft.core.Direction.UP : event.getFace());
        ItemStack held = event.getItemStack();
        boolean sneakingWithItem =
                player.isSecondaryUseActive() && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty());

        // Using the block: sneaking with an item needs Full, otherwise the permission of the block class.
        BannerPermission needed = sneakingWithItem
                ? BannerPermission.FULL
                : BannerProtection.classify(level, pos, level.getBlockState(pos));
        Optional<BannerEntry> blockDenied = BannerProtection.deniedBy(level, pos, player, needed);
        // Using the item (placing, tilling, bone meal, fire): needs Full where it lands.
        Optional<BannerEntry> itemDenied = Optional.empty();
        if (!held.isEmpty() && !held.has(DataComponents.FOOD)) {
            itemDenied = BannerProtection.deniedBy(level, pos, player, BannerPermission.FULL);
            if (itemDenied.isEmpty()) {
                itemDenied = BannerProtection.deniedBy(level, adjacent, player, BannerPermission.FULL);
            }
        }
        if (blockDenied.isPresent()) {
            event.setUseBlock(TriState.FALSE);
        }
        if (itemDenied.isPresent() || (sneakingWithItem && blockDenied.isPresent())) {
            event.setUseItem(TriState.FALSE);
        }
        if (blockDenied.isPresent() || itemDenied.isPresent()) {
            BannerProtection.warn(player, blockDenied.isPresent() ? blockDenied : itemDenied);
        }
    }

    /** Buckets are used on the targeted block or fluid, not through the block interaction. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (level.isClientSide() || player.isCreative() || !(event.getItemStack().getItem() instanceof BucketItem)) {
            return;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, true);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Optional<BannerEntry> denied =
                BannerProtection.deniedBy(level, blockHit.getBlockPos(), player, BannerPermission.FULL);
        if (denied.isEmpty()) {
            denied = BannerProtection.deniedBy(
                    level, blockHit.getBlockPos().relative(blockHit.getDirection()), player, BannerPermission.FULL);
        }
        if (denied.isPresent()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            BannerProtection.warn(player, denied);
        }
    }

    // ------------------------------------------------------------------ players: frames and stands

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !isProtectedDecoration(event.getTarget())) {
            return;
        }
        Optional<BannerEntry> denied = BannerProtection.deniedBy(
                player.level(), event.getTarget().blockPosition(), player, BannerPermission.FULL);
        if (denied.isPresent()) {
            event.setCanceled(true);
            BannerProtection.warn(player, denied);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        interactEntity(event, event.getTarget());
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        interactEntity(event, event.getTarget());
    }

    private static void interactEntity(PlayerInteractEvent event, Entity target) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !isProtectedDecoration(target)) {
            return;
        }
        // Taking from an armour stand or frame needs Containers; changing it or the other decorations needs Full.
        boolean takeOnly = (target instanceof ArmorStand || target instanceof ItemFrame)
                && player.getItemInHand(event.getHand()).isEmpty();
        BannerPermission needed = takeOnly ? BannerPermission.CONTAINERS : BannerPermission.FULL;
        Optional<BannerEntry> denied =
                BannerProtection.deniedBy(player.level(), target.blockPosition(), player, needed);
        if (denied.isPresent() && event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
            if (event instanceof PlayerInteractEvent.EntityInteract interact) {
                interact.setCancellationResult(InteractionResult.FAIL);
            } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific specific) {
                specific.setCancellationResult(InteractionResult.FAIL);
            }
            BannerProtection.warn(player, denied);
        }
    }

    /** Item frames, armour stands, paintings and leash knots. */
    static boolean isProtectedDecoration(Entity entity) {
        return entity instanceof ArmorStand || entity instanceof HangingEntity;
    }

    // ------------------------------------------------------------------ explosions

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !BannerProtection.enabled()
                || !ProgressionConfig.get(ProgressionConfig.BANNER_EXPLOSION_PROTECTION)) {
            return;
        }
        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) {
            return;
        }
        BannerIndex index = BannerIndex.get(level);
        if (index.isEmpty()) {
            return;
        }
        Explosion explosion = event.getExplosion();
        LivingEntity source = explosion.getIndirectSourceEntity();
        Player responsible = source instanceof Player player ? player : null;
        affected.removeIf(pos -> {
            if (index.get(pos) != null) {
                return true; // a protecting banner is never blown up
            }
            if (BannerProtection.query(level, pos).isEmpty()) {
                return false;
            }
            return responsible == null || !BannerProtection.allows(level, pos, responsible, BannerPermission.FULL);
        });
    }

    // ------------------------------------------------------------------ spawns

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        Mob mob = event.getEntity();
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (mob.getType().getCategory() == MobCategory.MONSTER && BannerProtection.blocksMonsterSpawn(level, pos)) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            return;
        }
        Optional<ResourceKey<Faction>> faction = FactionRelations.factionOf(mob);
        if (faction.isPresent() && BannerProtection.blocksFactionSpawn(level, pos, faction.get())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    // ------------------------------------------------------------------ housekeeping

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        BannerProtection.forgetWarnings(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BannerCommands.register(event.getDispatcher());
    }
}
