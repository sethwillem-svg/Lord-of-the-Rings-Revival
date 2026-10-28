package io.github.sethwillemsvg.lotrrevival.progression.banner;

import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRelations;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The banner protection rules: which banners cover a position, who is allowed to act there, and the warnings.
 *
 * <p>All methods answer for the server side; on a client level every query is empty and every action is allowed.
 */
public final class BannerProtection {
    /** Right-click classification order; the first tag that holds the block wins, {@link BannerPermission#FULL} is the rest. */
    private static final List<BannerPermission> CLASSIFICATION = List.of(
            BannerPermission.PERSONAL_CONTAINERS,
            BannerPermission.DOORS,
            BannerPermission.BEDS,
            BannerPermission.SWITCHES,
            BannerPermission.FOOD,
            BannerPermission.TABLES,
            BannerPermission.CONTAINERS);

    private static final Map<UUID, Long> LAST_WARNING = new ConcurrentHashMap<>();
    private static volatile FellowshipMembership fellowships = FellowshipMembership.NONE;

    private BannerProtection() {}

    // ------------------------------------------------------------------ configuration

    /** Installs how fellowship whitelist entries are resolved (the fellowship package does this). */
    public static void setFellowships(FellowshipMembership resolver) {
        fellowships = resolver == null ? FellowshipMembership.NONE : resolver;
    }

    public static FellowshipMembership fellowships() {
        return fellowships;
    }

    /** The master switch, {@code progression.bannerProtectionEnabled}. */
    public static boolean enabled() {
        return ProgressionConfig.get(ProgressionConfig.BANNER_PROTECTION_ENABLED);
    }

    // ------------------------------------------------------------------ queries

    /**
     * The protecting banners whose cube contains {@code pos}. Banners whose design is gone from the registry are
     * decorative and left out. Empty when protection is off or on a client level.
     */
    public static List<BannerEntry> query(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || !enabled()) {
            return List.of();
        }
        BannerIndex index = BannerIndex.get(serverLevel);
        if (index.isEmpty()) {
            return List.of();
        }
        return active(serverLevel, index.covering(pos));
    }

    /** Keeps the entries whose design still exists. */
    static List<BannerEntry> active(ServerLevel level, List<BannerEntry> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<BannerEntry> kept = new ArrayList<>(candidates.size());
        for (BannerEntry entry : candidates) {
            if (factionOf(level, entry).isPresent()) {
                kept.add(entry);
            }
        }
        return kept;
    }

    /** The faction of the banner's design, empty if the design is not in the registry. */
    public static Optional<ResourceKey<Faction>> factionOf(Level level, BannerEntry entry) {
        return factionOfDesign(level, entry.design());
    }

    public static Optional<ResourceKey<Faction>> factionOfDesign(Level level, ResourceLocation design) {
        return level.registryAccess()
                .registry(BannerDesign.REGISTRY)
                .map(registry -> registry.get(design))
                .map(BannerDesign::faction);
    }

    // ------------------------------------------------------------------ the "allowed" rule

    /**
     * Whether the player is allowed {@code permission} by this one banner: the banner's owner; or (faction mode) a
     * player with at least the threshold alignment with the banner's faction; or (whitelist mode) a listed player or
     * member of a listed fellowship whose set holds Full or the permission; or the default set holds Full or the
     * permission. Structure banners allow nobody. A whitelist banner with no owner behaves as a faction banner.
     * Creative players are not special here; see {@link #allows}.
     */
    public static boolean entryAllows(Level level, BannerEntry banner, Player player, BannerPermission permission) {
        BannerSettings settings = banner.settings();
        if (settings.structure()) {
            return false;
        }
        UUID id = player.getUUID();
        if (settings.isOwner(id)) {
            return true;
        }
        boolean factionMode = settings.mode() == BannerSettings.Mode.FACTION || settings.owner().isEmpty();
        if (factionMode) {
            Optional<ResourceKey<Faction>> faction = factionOf(level, banner);
            if (faction.isPresent() && Alignment.get(player, faction.get()) >= settings.threshold()) {
                return true;
            }
        } else {
            for (BannerSettings.Entry entry : settings.entries()) {
                if (entry.matches(id, fellowships) && BannerPermission.grants(entry.permissions(), permission)) {
                    return true;
                }
            }
        }
        return BannerPermission.grants(settings.defaults(), permission);
    }

    /** The first covering banner that does not allow the player the permission, if any. Creative players are allowed. */
    public static Optional<BannerEntry> deniedBy(Level level, BlockPos pos, Player player, BannerPermission permission) {
        if (player.isCreative()) {
            return Optional.empty();
        }
        for (BannerEntry banner : query(level, pos)) {
            if (!entryAllows(level, banner, player, permission)) {
                return Optional.of(banner);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the player may act at {@code pos}: for every protecting banner covering it, that banner allows the
     * permission. Creative players bypass.
     */
    public static boolean allows(Level level, BlockPos pos, Player player, BannerPermission permission) {
        return deniedBy(level, pos, player, permission).isEmpty();
    }

    // ------------------------------------------------------------------ right-click classification

    /** The permission needed to right-click the block at {@code pos}. */
    public static BannerPermission classify(Level level, BlockPos pos, BlockState state) {
        for (BannerPermission permission : CLASSIFICATION) {
            if (state.is(permission.tag())) {
                return permission;
            }
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if ((entity instanceof Container || entity instanceof MenuProvider) && !(entity instanceof FactionBannerBlockEntity)) {
            return BannerPermission.CONTAINERS;
        }
        return BannerPermission.FULL;
    }

    // ------------------------------------------------------------------ self-protection

    /**
     * Whether breaking the banner at {@code pos} is refused to this player: it is a protecting banner with
     * self-protection on (the setting and the config), or a structure banner, and the player is not its owner.
     * Creative players are allowed.
     */
    public static boolean selfProtected(Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !enabled() || player.isCreative()) {
            return false;
        }
        BannerEntry banner = BannerIndex.get(serverLevel).get(pos);
        if (banner == null) {
            return false;
        }
        BannerSettings settings = banner.settings();
        if (settings.structure()) {
            return true;
        }
        if (settings.isOwner(player.getUUID())) {
            return false;
        }
        return settings.selfProtection() && ProgressionConfig.get(ProgressionConfig.BANNER_SELF_PROTECTION_ENABLED);
    }

    // ------------------------------------------------------------------ spawns, invasions, NPC griefing

    /** Hostile mobs never spawn naturally inside any protecting banner. */
    public static boolean blocksMonsterSpawn(Level level, BlockPos pos) {
        return !query(level, pos).isEmpty();
    }

    /** Faction NPCs do not spawn inside a banner whose faction is an enemy of theirs. */
    public static boolean blocksFactionSpawn(Level level, BlockPos pos, ResourceKey<Faction> npcFaction) {
        return enemyBannerAt(level, pos, npcFaction);
    }

    /** Invasions of the faction cannot start inside a banner whose faction is an enemy of the invaders. */
    public static boolean blocksInvasion(Level level, BlockPos pos, ResourceKey<Faction> invaders) {
        return enemyBannerAt(level, pos, invaders);
    }

    /** NPCs of the faction cannot break blocks inside a banner whose faction is an enemy of theirs. */
    public static boolean blocksNpcGriefing(Level level, BlockPos pos, ResourceKey<Faction> npcFaction) {
        return enemyBannerAt(level, pos, npcFaction);
    }

    private static boolean enemyBannerAt(Level level, BlockPos pos, ResourceKey<Faction> other) {
        for (BannerEntry banner : query(level, pos)) {
            Optional<ResourceKey<Faction>> faction = factionOf(level, banner);
            if (faction.isPresent()
                    && FactionRelations.relation(level.registryAccess(), faction.get(), other).isBad()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ warnings

    /** The text of "This land is protected by ...": the faction, the owner (whitelist) or a stronghold. */
    public static Component protectorName(Level level, BannerEntry banner) {
        BannerSettings settings = banner.settings();
        if (settings.structure()) {
            return Component.translatable("banner.lotrrevival.stronghold");
        }
        if (settings.mode() == BannerSettings.Mode.WHITELIST && settings.owner().isPresent()) {
            return Component.literal(settings.ownerName());
        }
        return Component.translatable(FactionBannerItem.factionKey(banner.design()));
    }

    /**
     * Tells the player the land is protected, at most once per {@code bannerWarningCooldownTicks}. Returns whether
     * the message was sent.
     */
    public static boolean warn(ServerPlayer player, BannerEntry banner) {
        long now = player.level().getGameTime();
        int cooldown = ProgressionConfig.get(ProgressionConfig.BANNER_WARNING_COOLDOWN_TICKS);
        Long last = LAST_WARNING.get(player.getUUID());
        if (last != null && now - last < cooldown && now >= last) {
            return false;
        }
        LAST_WARNING.put(player.getUUID(), now);
        player.sendSystemMessage(Component.translatable(
                "banner.lotrrevival.protected", protectorName(player.level(), banner)));
        return true;
    }

    /** Warns about whichever banner denied; a no-op for non-server players. */
    public static void warn(Player player, Optional<BannerEntry> banner) {
        if (player instanceof ServerPlayer serverPlayer && banner.isPresent()) {
            warn(serverPlayer, banner.get());
        }
    }

    public static void forgetWarnings(UUID player) {
        LAST_WARNING.remove(player);
    }

    /** Clears every warning timer (tests). */
    public static void resetWarnings() {
        LAST_WARNING.clear();
    }

    /** The banner at {@code pos} in the index, or null. Server levels only. */
    @Nullable
    public static BannerEntry banner(Level level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel ? BannerIndex.get(serverLevel).get(pos) : null;
    }
}
