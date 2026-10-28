package io.github.sethwillemsvg.lotrrevival.progression.banner;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.Fellowship;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipData;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.Fellowships;
import io.github.sethwillemsvg.lotrrevival.progression.network.OpenBannerEditorPayload;
import io.github.sethwillemsvg.lotrrevival.progression.network.UpdateBannerPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of the banner edit screen: who may open it, and the fully validated {@code update_banner}.
 *
 * <p>The editor is for the banner's owner, or a creative operator (never for a structure banner). An update is
 * dropped when the banner is missing or out of reach, the sender may not edit it, requests come too fast, or any
 * value is out of range; a rejected update changes nothing.
 */
public final class BannerEditService {
    /** Extra blocks of reach allowed beyond the player's block interaction range. */
    public static final double REACH_BUFFER = 2.0;
    /** Minimum ticks between two update requests from one player. */
    public static final int MIN_TICKS_BETWEEN_UPDATES = 10;

    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Map<UUID, Long> LAST_UPDATE = new ConcurrentHashMap<>();

    /** What became of an update request. */
    public enum Result {
        APPLIED,
        NO_BANNER,
        TOO_FAR,
        NOT_ALLOWED,
        RATE_LIMITED,
        INVALID
    }

    private BannerEditService() {}

    // ------------------------------------------------------------------ opening

    /** Whether the player may edit this banner: its owner, or a creative operator; never a structure banner. */
    public static boolean canEdit(ServerPlayer player, FactionBannerBlockEntity banner) {
        BannerSettings settings = banner.settings();
        if (settings.structure()) {
            return false;
        }
        return settings.isOwner(player.getUUID()) || (player.isCreative() && player.hasPermissions(2));
    }

    /** Opens the editor for the player if they may edit the banner. Returns whether the editor was offered. */
    public static boolean open(ServerPlayer player, FactionBannerBlockEntity banner) {
        if (!canEdit(player, banner)) {
            return false;
        }
        if (player.connection != null && player.connection.hasChannel(OpenBannerEditorPayload.TYPE)) {
            PacketDistributor.sendToPlayer(
                    player,
                    new OpenBannerEditorPayload(
                            banner.getBlockPos(), banner.design(), banner.settings(), player.hasPermissions(2)));
        }
        return true;
    }

    // ------------------------------------------------------------------ updating

    /** Validates and applies the request. The result says what happened; nothing changes unless it is APPLIED. */
    public static Result update(ServerPlayer player, UpdateBannerPayload payload) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof FactionBannerBlockEntity banner)) {
            return Result.NO_BANNER;
        }
        if (!player.canInteractWithBlock(pos, REACH_BUFFER)) {
            return Result.TOO_FAR;
        }
        if (!canEdit(player, banner)) {
            return Result.NOT_ALLOWED;
        }
        long now = level.getGameTime();
        Long last = LAST_UPDATE.put(player.getUUID(), now);
        if (last != null && now >= last && now - last < MIN_TICKS_BETWEEN_UPDATES) {
            return Result.RATE_LIMITED;
        }

        BannerSettings current = banner.settings();
        Optional<BannerSettings> next = validate(player, current, payload);
        if (next.isEmpty()) {
            player.sendSystemMessage(Component.translatable("banner.lotrrevival.edit.rejected"));
            return Result.INVALID;
        }
        banner.setSettings(next.get());
        player.sendSystemMessage(Component.translatable("banner.lotrrevival.edit.saved"));
        return Result.APPLIED;
    }

    /** The new settings if every value is valid, else empty. */
    private static Optional<BannerSettings> validate(
            ServerPlayer player, BannerSettings current, UpdateBannerPayload payload) {
        if (payload.threshold() < BannerSettings.MIN_THRESHOLD || payload.threshold() > BannerSettings.MAX_THRESHOLD) {
            return Optional.empty();
        }
        if (UpdateBannerPayload.hasUnknownBits(payload.defaultsMask())) {
            return Optional.empty();
        }
        int range = payload.customRange();
        if (range < 0 || range > BannerBases.MAX_RANGE) {
            return Optional.empty();
        }
        if (range != current.customRange() && !player.hasPermissions(2)) {
            return Optional.empty();
        }
        if (payload.entries().size() > current.whitelistSize() - 1) {
            return Optional.empty();
        }
        Optional<List<BannerSettings.Entry>> entries = resolveEntries(player, current, payload.entries());
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(current.withMode(payload.mode())
                .withThreshold(payload.threshold())
                .withEntries(entries.get())
                .withDefaults(UpdateBannerPayload.permissions(payload.defaultsMask()))
                .withSelfProtection(payload.selfProtection())
                .withCustomRange(range));
    }

    private static Optional<List<BannerSettings.Entry>> resolveEntries(
            ServerPlayer player, BannerSettings current, List<UpdateBannerPayload.EntryEdit> edits) {
        var server = player.getServer();
        if (server == null) {
            return Optional.empty();
        }
        FellowshipData fellowships = FellowshipData.get(server);
        Map<UUID, BannerSettings.Entry> existingPlayers = new HashMap<>();
        Map<UUID, BannerSettings.Entry> existingFellowships = new HashMap<>();
        for (BannerSettings.Entry entry : current.entries()) {
            (entry.kind() == BannerSettings.Kind.PLAYER ? existingPlayers : existingFellowships).put(entry.id(), entry);
        }
        Set<UUID> ownersFellowships = new HashSet<>();
        current.owner().ifPresent(owner -> fellowships.of(owner).forEach(f -> ownersFellowships.add(f.id())));

        List<BannerSettings.Entry> result = new ArrayList<>(edits.size());
        Set<UUID> seenPlayers = new HashSet<>();
        Set<UUID> seenFellowships = new HashSet<>();
        for (UpdateBannerPayload.EntryEdit edit : edits) {
            if (UpdateBannerPayload.hasUnknownBits(edit.permissionMask())) {
                return Optional.empty();
            }
            Set<BannerPermission> permissions = UpdateBannerPayload.permissions(edit.permissionMask());
            if (edit.kind() == BannerSettings.Kind.PLAYER) {
                UUID id;
                String name;
                BannerSettings.Entry kept = existingPlayers.get(edit.id());
                if (kept != null) {
                    id = kept.id();
                    name = kept.name();
                } else {
                    String typed = edit.name().trim();
                    if (!PLAYER_NAME.matcher(typed).matches()) {
                        return Optional.empty();
                    }
                    Optional<UUID> resolved = Fellowships.resolvePlayer(server, typed);
                    if (resolved.isEmpty()) {
                        return Optional.empty();
                    }
                    id = resolved.get();
                    ServerPlayer online = server.getPlayerList().getPlayer(id);
                    name = online != null ? online.getGameProfile().getName() : typed;
                }
                // Slot 0 is the owner; a second entry for the same player is a duplicate.
                if (current.isOwner(id) || !seenPlayers.add(id)) {
                    return Optional.empty();
                }
                result.add(new BannerSettings.Entry(BannerSettings.Kind.PLAYER, id, name, permissions));
            } else {
                Optional<Fellowship> fellowship = fellowships.find(edit.id());
                boolean listed = existingFellowships.containsKey(edit.id());
                if (fellowship.isEmpty() || !seenFellowships.add(edit.id())) {
                    return Optional.empty();
                }
                // A new fellowship entry must be one of the owner's; an existing one may stay.
                if (!listed && !ownersFellowships.contains(edit.id())) {
                    return Optional.empty();
                }
                result.add(new BannerSettings.Entry(
                        BannerSettings.Kind.FELLOWSHIP, edit.id(), fellowship.get().name(), permissions));
            }
        }
        return Optional.of(result);
    }

    /** Forgets a player's rate limit timer (logout). */
    public static void forget(UUID player) {
        LAST_UPDATE.remove(player);
    }

    /** Clears every rate limit timer (tests). */
    public static void resetRateLimits() {
        LAST_UPDATE.clear();
        LOTRRevival.LOGGER.debug("Banner edit rate limits cleared");
    }
}
