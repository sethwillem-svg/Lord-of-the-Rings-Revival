package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import io.github.sethwillemsvg.lotrrevival.world.WorldConfig;
import io.github.sethwillemsvg.lotrrevival.world.network.TravelSync;
import io.github.sethwillemsvg.lotrrevival.world.travel.TravelChecks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Custom waypoint service: create at the player's block position, rename and delete (specs/world/waypoints-travel.md,
 * "Waypoints" rule 5 and "Rules and numbers"). Server side only. Every successful change sends a full travel sync.
 * The same rules serve the {@code /lotr waypoint custom} commands and the {@code world/custom_waypoint_edit} payload.
 */
public final class CustomWaypoints {
    /** Longest name after trimming. */
    public static final int MAX_NAME = 32;

    /** Config value of {@code waypoints.custom_min_y} that switches the depth limit off. */
    public static final int NO_MIN_Y = -2048;

    private CustomWaypoints() {}

    /**
     * Outcome of an edit.
     *
     * @param ok whether the change was made
     * @param message what to tell the player (success or the refusal reason)
     * @param id the id of the created or changed waypoint, when there is one
     */
    public record Result(boolean ok, Component message, OptionalInt id) {
        static Result refused(String key, Object... args) {
            return new Result(false, Component.translatable(key, args), OptionalInt.empty());
        }

        static Result done(String key, int id, Object... args) {
            return new Result(true, Component.translatable(key, args), OptionalInt.of(id));
        }
    }

    /** The name trimmed, or empty when it is blank or longer than {@link #MAX_NAME} characters after trimming. */
    public static Optional<String> cleanName(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String name = raw.strip();
        return name.isEmpty() || name.length() > MAX_NAME ? Optional.empty() : Optional.of(name);
    }

    /** Whether {@code y} is below the configured minimum; {@link #NO_MIN_Y} (or lower) means no limit. */
    public static boolean belowMinY(int y, int minY) {
        return minY > NO_MIN_Y && y < minY;
    }

    /** Creates a custom waypoint at the player's block position under the limit their achievements allow. */
    public static Result create(ServerPlayer player, String rawName) {
        return create(player, rawName, TravelRules.customLimit(player));
    }

    /**
     * Creates a custom waypoint with an explicit limit. Checks, in order: Middle-earth only, the name, the minimum Y, the
     * limit (existing waypoints count, so a player whose limit dropped keeps theirs but cannot add more).
     */
    public static Result create(ServerPlayer player, String rawName, int limit) {
        if (!TravelChecks.middleEarth.test(player.serverLevel())) {
            return Result.refused("message.lotrrevival.custom.wrong_dimension");
        }
        Optional<String> name = cleanName(rawName);
        if (name.isEmpty()) {
            return Result.refused("message.lotrrevival.custom.bad_name", MAX_NAME);
        }
        BlockPos pos = player.blockPosition();
        int minY = WorldConfig.get(WorldConfig.WAYPOINTS_CUSTOM_MIN_Y);
        if (belowMinY(pos.getY(), minY)) {
            return Result.refused("message.lotrrevival.custom.too_low", minY);
        }
        TravelData data = TravelData.get(player);
        if (!TravelRules.canCreateCustom(data.custom().size(), limit)) {
            return Result.refused("message.lotrrevival.custom.limit", Math.min(limit, TravelData.MAX_CUSTOM));
        }
        TravelData updated = data.withNewCustom(name.get(), pos);
        TravelData.set(player, updated);
        TravelSync.send(player);
        return Result.done("message.lotrrevival.custom.created", updated.nextCustomId() - 1, name.get());
    }

    public static Result rename(ServerPlayer player, int id, String rawName) {
        Optional<String> name = cleanName(rawName);
        if (name.isEmpty()) {
            return Result.refused("message.lotrrevival.custom.bad_name", MAX_NAME);
        }
        TravelData data = TravelData.get(player);
        List<TravelData.Custom> list = new ArrayList<>(data.custom());
        for (int i = 0; i < list.size(); i++) {
            TravelData.Custom custom = list.get(i);
            if (custom.id() == id) {
                list.set(i, new TravelData.Custom(id, name.get(), custom.pos(), custom.uses()));
                TravelData.set(player, withCustom(data, list));
                TravelSync.send(player);
                return Result.done("message.lotrrevival.custom.renamed", id, custom.name(), name.get());
            }
        }
        return Result.refused("message.lotrrevival.custom.unknown", id);
    }

    public static Result delete(ServerPlayer player, int id) {
        TravelData data = TravelData.get(player);
        List<TravelData.Custom> list = new ArrayList<>(data.custom());
        for (int i = 0; i < list.size(); i++) {
            TravelData.Custom custom = list.get(i);
            if (custom.id() == id) {
                list.remove(i);
                TravelData.set(player, withCustom(data, list));
                TravelSync.send(player);
                return Result.done("message.lotrrevival.custom.deleted", id, custom.name());
            }
        }
        return Result.refused("message.lotrrevival.custom.unknown", id);
    }

    /** The data with a new custom list; every other field, including {@code nextCustomId}, is kept (ids never reused). */
    private static TravelData withCustom(TravelData data, List<TravelData.Custom> custom) {
        return new TravelData(
                data.regions(), data.uses(), custom, data.nextCustomId(), data.clock(), data.last(), data.lastLogout());
    }
}
