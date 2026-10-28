package io.github.sethwillemsvg.lotrrevival.progression.fellowship;

import io.github.sethwillemsvg.lotrrevival.core.api.AchievementCounters;
import io.github.sethwillemsvg.lotrrevival.progression.ProgressionConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fellowship service: every rule of the spec lives here, and commands, payloads and the Fellowships page all call
 * it. Actors are players, targets are UUIDs (names are resolved by callers), so GameTests can drive it without names.
 */
public final class Fellowships {
    private static final Logger LOGGER = LoggerFactory.getLogger(Fellowships.class);

    public static final int NAME_MAX = 24;
    public static final int MAX_PENDING_INVITES = 50;
    public static final int ACHIEVEMENTS_PER_EXTRA_FELLOWSHIP = 20;
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9 '_-]+");

    private static final ToIntFunction<Player> DEFAULT_COUNTER = AchievementCounters::count;
    private static final IntSupplier DEFAULT_MAX_SIZE = () -> ProgressionConfig.get(ProgressionConfig.FELLOWSHIP_MAX_SIZE);
    private static final BooleanSupplier DEFAULT_CREATION =
            () -> ProgressionConfig.get(ProgressionConfig.FELLOWSHIP_CREATION_ENABLED);

    private static volatile ToIntFunction<Player> achievementCounter = DEFAULT_COUNTER;
    private static volatile IntSupplier maxSize = DEFAULT_MAX_SIZE;
    private static volatile BooleanSupplier creationEnabled = DEFAULT_CREATION;

    private Fellowships() {}

    // ------------------------------------------------------------------ hooks for tests

    /** Test hook: replaces how achievements are counted for the lead limit. */
    public static void setAchievementCounter(ToIntFunction<Player> counter) {
        achievementCounter = counter;
    }

    /** Test hook: replaces the max fellowship size (-1 = no limit). */
    public static void setMaxSize(IntSupplier supplier) {
        maxSize = supplier;
    }

    /** Test hook: restores the config-backed defaults. */
    public static void resetHooks() {
        achievementCounter = DEFAULT_COUNTER;
        maxSize = DEFAULT_MAX_SIZE;
        creationEnabled = DEFAULT_CREATION;
    }

    // ------------------------------------------------------------------ numbers

    /** How many fellowships a player with this many achievements may lead. */
    public static int leadLimit(int achievementCount) {
        return 1 + Math.max(0, achievementCount) / ACHIEVEMENTS_PER_EXTRA_FELLOWSHIP;
    }

    /** Maximum members per fellowship, or {@link Integer#MAX_VALUE} for no limit. */
    public static int maxMembers() {
        int configured = maxSize.getAsInt();
        return configured < 0 ? Integer.MAX_VALUE : Math.max(2, configured);
    }

    /** Trimmed name when valid, else empty. */
    public static Optional<String> validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > NAME_MAX || !NAME_PATTERN.matcher(name).matches()) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

    // ------------------------------------------------------------------ lookup helpers

    /** The fellowship of this player with that name (case-insensitive). */
    public static Optional<Fellowship> findOf(MinecraftServer server, UUID player, String name) {
        for (Fellowship fellowship : FellowshipData.get(server).of(player)) {
            if (fellowship.name().equalsIgnoreCase(name.trim())) {
                return Optional.of(fellowship);
            }
        }
        return Optional.empty();
    }

    /** A pending invite of this player by fellowship name. */
    public static Optional<Fellowship> findInvite(MinecraftServer server, UUID player, String name) {
        for (Fellowship fellowship : FellowshipData.get(server).invitesFor(player)) {
            if (fellowship.name().equalsIgnoreCase(name.trim())) {
                return Optional.of(fellowship);
            }
        }
        return Optional.empty();
    }

    /** Resolves a player by name: online players first, then the remembered names. */
    public static Optional<UUID> resolvePlayer(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return Optional.of(online.getUUID());
        }
        return FellowshipData.get(server).findByName(name);
    }

    private static boolean nameClash(FellowshipData data, String name, Iterable<UUID> players, UUID except) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (UUID player : players) {
            for (Fellowship other : data.of(player)) {
                if (!other.id().equals(except) && other.name().toLowerCase(Locale.ROOT).equals(lower)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ actions

    /** Creates a fellowship owned by the player. {@code bypassConfig} lets ops create while creation is disabled. */
    public static FellowshipResult create(ServerPlayer owner, String rawName, boolean bypassConfig) {
        MinecraftServer server = owner.getServer();
        if (!bypassConfig && !creationEnabled.getAsBoolean()) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.creation_disabled");
        }
        Optional<String> name = validName(rawName);
        if (name.isEmpty()) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.bad_name", NAME_MAX);
        }
        FellowshipData data = FellowshipData.get(server);
        int limit = leadLimit(achievementCounter.applyAsInt(owner));
        if (data.ownedCount(owner.getUUID()) >= limit) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.lead_limit", limit);
        }
        if (nameClash(data, name.get(), List.of(owner.getUUID()), null)) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.name_taken", name.get());
        }
        data.rememberName(owner.getUUID(), owner.getGameProfile().getName());
        Fellowship fellowship = new Fellowship(UUID.randomUUID(), name.get(), owner.getUUID(), server.overworld().getGameTime());
        data.add(fellowship);
        touch(server, fellowship);
        return FellowshipResult.ok("fellowship.lotrrevival.created", fellowship.name());
    }

    /** Owner or admin invites a player. */
    public static FellowshipResult invite(ServerPlayer actor, UUID fellowshipId, UUID target) {
        MinecraftServer server = actor.getServer();
        FellowshipData data = FellowshipData.get(server);
        Fellowship fellowship = data.find(fellowshipId).orElse(null);
        if (fellowship == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.unknown");
        }
        if (!manages(fellowship, actor)) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.no_permission");
        }
        String targetName = data.nameOf(target);
        if (fellowship.isMember(target)) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.already_member", targetName);
        }
        if (fellowship.invites().containsKey(target)) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.already_invited", targetName);
        }
        if (fellowship.size() >= maxMembers()) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.full", fellowship.name());
        }
        if (fellowship.invites().size() >= MAX_PENDING_INVITES) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.too_many_invites", MAX_PENDING_INVITES);
        }
        fellowship.invites().put(target, actor.getUUID());
        data.setDirty();
        ServerPlayer invitee = server.getPlayerList().getPlayer(target);
        if (invitee != null) {
            invitee.sendSystemMessage(inviteNotice(fellowship.name(), actor.getGameProfile().getName()));
        }
        touch(server, fellowship);
        return FellowshipResult.ok("fellowship.lotrrevival.invited", targetName, fellowship.name());
    }

    /** The clickable line an invitee receives. */
    public static Component inviteNotice(String fellowshipName, String inviterName) {
        Component accept = Component.translatable("fellowship.lotrrevival.invite.accept_button")
                .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GREEN)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/fellowship accept " + fellowshipName)));
        Component reject = Component.translatable("fellowship.lotrrevival.invite.reject_button")
                .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.RED)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/fellowship reject " + fellowshipName)));
        return Component.translatable("fellowship.lotrrevival.invite.received", inviterName, fellowshipName, accept, reject);
    }

    public static FellowshipResult accept(ServerPlayer player, UUID fellowshipId) {
        MinecraftServer server = player.getServer();
        FellowshipData data = FellowshipData.get(server);
        Fellowship fellowship = data.find(fellowshipId).orElse(null);
        if (fellowship == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.disbanded");
        }
        UUID inviter = fellowship.invites().get(player.getUUID());
        if (inviter == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.no_invite", fellowship.name());
        }
        if (fellowship.size() >= maxMembers()) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.full", fellowship.name());
        }
        if (nameClash(data, fellowship.name(), List.of(player.getUUID()), fellowship.id())) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.name_taken", fellowship.name());
        }
        data.rememberName(player.getUUID(), player.getGameProfile().getName());
        fellowship.invites().remove(player.getUUID());
        fellowship.members().add(player.getUUID());
        data.setDirty();
        String joiner = player.getGameProfile().getName();
        ServerPlayer inviterPlayer = server.getPlayerList().getPlayer(inviter);
        if (inviterPlayer != null) {
            inviterPlayer.sendSystemMessage(Component.translatable("fellowship.lotrrevival.notice.invite_accepted", joiner, fellowship.name()));
        }
        notifyMembers(server, fellowship, Component.translatable("fellowship.lotrrevival.notice.joined", joiner, fellowship.name()), player.getUUID());
        touch(server, fellowship);
        return FellowshipResult.ok("fellowship.lotrrevival.joined", fellowship.name());
    }

    public static FellowshipResult reject(ServerPlayer player, UUID fellowshipId) {
        MinecraftServer server = player.getServer();
        FellowshipData data = FellowshipData.get(server);
        Fellowship fellowship = data.find(fellowshipId).orElse(null);
        if (fellowship == null || fellowship.invites().remove(player.getUUID()) == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.no_invite", fellowship == null ? "?" : fellowship.name());
        }
        data.setDirty();
        touch(server, fellowship);
        return FellowshipResult.ok("fellowship.lotrrevival.rejected", fellowship.name());
    }

    public static FellowshipResult leave(ServerPlayer player, UUID fellowshipId) {
        MinecraftServer server = player.getServer();
        FellowshipData data = FellowshipData.get(server);
        Fellowship fellowship = data.find(fellowshipId).orElse(null);
        if (fellowship == null || !fellowship.isMember(player.getUUID())) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.not_member");
        }
        if (fellowship.owner().equals(player.getUUID())) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.owner_cannot_leave");
        }
        fellowship.admins().remove(player.getUUID());
        fellowship.members().remove(player.getUUID());
        data.setDirty();
        notifyMembers(server, fellowship, Component.translatable("fellowship.lotrrevival.notice.left", player.getGameProfile().getName(), fellowship.name()), null);
        touch(server, fellowship);
        FellowshipSync.markDirty(server, List.of(player.getUUID()));
        return FellowshipResult.ok("fellowship.lotrrevival.left", fellowship.name());
    }

    /** Owner removes anyone but themselves; an admin removes plain members only. */
    public static FellowshipResult remove(ServerPlayer actor, UUID fellowshipId, UUID target) {
        MinecraftServer server = actor.getServer();
        FellowshipData data = FellowshipData.get(server);
        Fellowship fellowship = data.find(fellowshipId).orElse(null);
        if (fellowship == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.unknown");
        }
        FellowshipRole actorRole = fellowship.role(actor.getUUID()).orElse(FellowshipRole.MEMBER);
        if (!manages(fellowship, actor)) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.no_permission");
        }
        FellowshipRole targetRole = fellowship.role(target).orElse(null);
        String targetName = data.nameOf(target);
        if (targetRole == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.not_in_fellowship", targetName);
        }
        if (targetRole == FellowshipRole.OWNER) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.cannot_remove_owner");
        }
        if (actorRole == FellowshipRole.ADMIN && targetRole != FellowshipRole.MEMBER) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.no_permission");
        }
        fellowship.admins().remove(target);
        fellowship.members().remove(target);
        data.setDirty();
        ServerPlayer removed = server.getPlayerList().getPlayer(target);
        if (removed != null) {
            removed.sendSystemMessage(Component.translatable("fellowship.lotrrevival.notice.you_removed", fellowship.name()));
        }
        notifyMembers(server, fellowship, Component.translatable("fellowship.lotrrevival.notice.removed", targetName, fellowship.name()), null);
        touch(server, fellowship);
        FellowshipSync.markDirty(server, List.of(target));
        return FellowshipResult.ok("fellowship.lotrrevival.removed", targetName);
    }

    public static FellowshipResult promote(ServerPlayer actor, UUID fellowshipId, UUID target) {
        return setAdmin(actor, fellowshipId, target, true);
    }

    public static FellowshipResult demote(ServerPlayer actor, UUID fellowshipId, UUID target) {
        return setAdmin(actor, fellowshipId, target, false);
    }

    private static FellowshipResult setAdmin(ServerPlayer actor, UUID fellowshipId, UUID target, boolean admin) {
        MinecraftServer server = actor.getServer();
        FellowshipData data = FellowshipData.get(server);
        Fellowship fellowship = data.find(fellowshipId).orElse(null);
        if (fellowship == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.unknown");
        }
        if (!owns(fellowship, actor)) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.owner_only");
        }
        String targetName = data.nameOf(target);
        FellowshipRole current = fellowship.role(target).orElse(null);
        if (current == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.not_in_fellowship", targetName);
        }
        if (admin && current != FellowshipRole.MEMBER) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.not_plain_member", targetName);
        }
        if (!admin && current != FellowshipRole.ADMIN) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.not_admin", targetName);
        }
        if (admin) {
            fellowship.members().remove(target);
            fellowship.admins().add(target);
        } else {
            fellowship.admins().remove(target);
            fellowship.members().add(target);
        }
        data.setDirty();
        touch(server, fellowship);
        return FellowshipResult.ok(admin ? "fellowship.lotrrevival.promoted" : "fellowship.lotrrevival.demoted", targetName);
    }

    /** The new owner must be an online member who is under their own lead limit. The old owner becomes an admin. */
    public static FellowshipResult transfer(ServerPlayer actor, UUID fellowshipId, UUID target) {
        MinecraftServer server = actor.getServer();
        FellowshipData data = FellowshipData.get(server);
        Fellowship fellowship = data.find(fellowshipId).orElse(null);
        if (fellowship == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.unknown");
        }
        if (!owns(fellowship, actor)) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.owner_only");
        }
        String targetName = data.nameOf(target);
        if (target.equals(actor.getUUID()) || !fellowship.isMember(target)) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.not_in_fellowship", targetName);
        }
        ServerPlayer heir = server.getPlayerList().getPlayer(target);
        if (heir == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.heir_offline", targetName);
        }
        int limit = leadLimit(achievementCounter.applyAsInt(heir));
        if (data.ownedCount(target) >= limit) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.heir_lead_limit", targetName, limit);
        }
        fellowship.admins().remove(target);
        fellowship.members().remove(target);
        fellowship.admins().add(fellowship.owner());
        fellowship.setOwner(target);
        data.setDirty();
        notifyMembers(server, fellowship, Component.translatable("fellowship.lotrrevival.notice.transferred", targetName, fellowship.name()), null);
        touch(server, fellowship);
        return FellowshipResult.ok("fellowship.lotrrevival.transferred", targetName);
    }

    public static FellowshipResult rename(ServerPlayer actor, UUID fellowshipId, String rawName) {
        MinecraftServer server = actor.getServer();
        FellowshipData data = FellowshipData.get(server);
        Fellowship fellowship = data.find(fellowshipId).orElse(null);
        if (fellowship == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.unknown");
        }
        if (!owns(fellowship, actor)) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.owner_only");
        }
        Optional<String> name = validName(rawName);
        if (name.isEmpty()) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.bad_name", NAME_MAX);
        }
        if (nameClash(data, name.get(), fellowship.allMembers(), fellowship.id())) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.name_taken", name.get());
        }
        String old = fellowship.name();
        fellowship.setName(name.get());
        data.setDirty();
        notifyMembers(server, fellowship, Component.translatable("fellowship.lotrrevival.notice.renamed", old, name.get()), null);
        touch(server, fellowship);
        return FellowshipResult.ok("fellowship.lotrrevival.renamed", name.get());
    }

    public static FellowshipResult disband(ServerPlayer actor, UUID fellowshipId) {
        MinecraftServer server = actor.getServer();
        Fellowship fellowship = FellowshipData.get(server).find(fellowshipId).orElse(null);
        if (fellowship == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.unknown");
        }
        if (!owns(fellowship, actor)) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.owner_only");
        }
        disbandNow(server, fellowship);
        return FellowshipResult.ok("fellowship.lotrrevival.disbanded", fellowship.name());
    }

    /** Disbands without a role check (admin command). */
    public static void disbandNow(MinecraftServer server, Fellowship fellowship) {
        notifyMembers(server, fellowship, Component.translatable("fellowship.lotrrevival.notice.disbanded", fellowship.name()), null);
        List<UUID> affected = new ArrayList<>(fellowship.allMembers());
        affected.addAll(fellowship.invites().keySet());
        FellowshipData.get(server).remove(fellowship.id());
        FellowshipSync.markDirty(server, affected);
    }

    /** Sets the icon from a held stack (count 1, components kept); an empty stack clears it. */
    public static FellowshipResult setIcon(ServerPlayer actor, UUID fellowshipId, ItemStack held) {
        MinecraftServer server = actor.getServer();
        FellowshipData data = FellowshipData.get(server);
        Fellowship fellowship = data.find(fellowshipId).orElse(null);
        if (fellowship == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.unknown");
        }
        if (!manages(fellowship, actor)) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.no_permission");
        }
        ItemStack icon = held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1);
        fellowship.setIcon(icon);
        data.setDirty();
        touch(server, fellowship);
        return FellowshipResult.ok(icon.isEmpty() ? "fellowship.lotrrevival.icon_cleared" : "fellowship.lotrrevival.icon_set");
    }

    public static FellowshipResult setOption(ServerPlayer actor, UUID fellowshipId, FellowshipOption option, boolean value) {
        MinecraftServer server = actor.getServer();
        FellowshipData data = FellowshipData.get(server);
        Fellowship fellowship = data.find(fellowshipId).orElse(null);
        if (fellowship == null) {
            return FellowshipResult.fail("fellowship.lotrrevival.error.unknown");
        }
        if (option.ownerOnly() ? !owns(fellowship, actor) : !manages(fellowship, actor)) {
            return FellowshipResult.fail(
                    option.ownerOnly()
                            ? "fellowship.lotrrevival.error.owner_only"
                            : "fellowship.lotrrevival.error.no_permission");
        }
        fellowship.setOption(option, value);
        data.setDirty();
        touch(server, fellowship);
        return FellowshipResult.ok(
                value ? "fellowship.lotrrevival.option.on" : "fellowship.lotrrevival.option.off",
                Component.translatable("fellowship.lotrrevival.option." + option.id()));
    }

    // ------------------------------------------------------------------ chat

    /** Sends a fellowship chat line to online members (and the console). Returns the number of recipients. */
    public static int chat(ServerPlayer sender, Fellowship fellowship, Component line) {
        MinecraftServer server = sender.getServer();
        int sent = 0;
        for (UUID member : fellowship.allMembers()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                online.sendSystemMessage(line);
                sent++;
            }
        }
        LOGGER.info("[Fellowship {}] {}", fellowship.name(), line.getString());
        return sent;
    }

    // ------------------------------------------------------------------ internals

    private static boolean owns(Fellowship fellowship, Player player) {
        return fellowship.role(player.getUUID()).filter(role -> role == FellowshipRole.OWNER).isPresent();
    }

    private static boolean manages(Fellowship fellowship, Player player) {
        return fellowship.role(player.getUUID()).filter(FellowshipRole::canManage).isPresent();
    }

    /** Sends a notice to every online member except {@code except}. */
    private static void notifyMembers(MinecraftServer server, Fellowship fellowship, Component notice, UUID except) {
        for (UUID member : fellowship.allMembers()) {
            if (member.equals(except)) {
                continue;
            }
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                online.sendSystemMessage(notice);
            }
        }
    }

    /** Marks everyone who sees this fellowship (members and invitees) for a sync. */
    private static void touch(MinecraftServer server, Fellowship fellowship) {
        List<UUID> viewers = new ArrayList<>(fellowship.allMembers());
        viewers.addAll(fellowship.invites().keySet());
        FellowshipSync.markDirty(server, viewers);
    }
}
