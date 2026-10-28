package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The {@code lotrrevival:travel} player attachment. Immutable: every change returns a new value.
 *
 * @param regions unlocked region ids
 * @param uses arrivals per fixed waypoint id, capped at {@link #MAX_USES}
 * @param custom the player's custom waypoints, at most {@link #MAX_CUSTOM}
 * @param nextCustomId id for the next custom waypoint, never reused
 * @param clock travel clock in ticks, saturating at {@link #MAX_CLOCK}
 * @param last last arrival
 * @param lastLogout epoch ms of the last logout, 0 when unknown
 */
public record TravelData(
        Set<ResourceLocation> regions,
        Map<ResourceLocation, Integer> uses,
        List<Custom> custom,
        int nextCustomId,
        int clock,
        Optional<WaypointRef> last,
        long lastLogout) {
    public static final int MAX_CLOCK = 1_000_000;
    public static final int MAX_USES = 1000;
    public static final int MAX_CUSTOM = 1024;

    /** A custom waypoint: a name and a Middle-earth block position. */
    public record Custom(int id, String name, BlockPos pos, int uses) {
        public static final Codec<Custom> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.INT.fieldOf("id").forGetter(Custom::id),
                        Codec.STRING.fieldOf("name").forGetter(Custom::name),
                        BlockPos.CODEC.fieldOf("pos").forGetter(Custom::pos),
                        Codec.INT.optionalFieldOf("uses", 0).forGetter(Custom::uses))
                .apply(i, Custom::new));
    }

    public static final TravelData EMPTY = new TravelData(Set.of(), Map.of(), List.of(), 0, 0, Optional.empty(), 0L);

    public static final Codec<TravelData> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceLocation.CODEC
                            .listOf()
                            .optionalFieldOf("regions", List.of())
                            .forGetter(d -> List.copyOf(d.regions())),
                    Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT)
                            .optionalFieldOf("uses", Map.of())
                            .forGetter(TravelData::uses),
                    Custom.CODEC.listOf().optionalFieldOf("custom", List.of()).forGetter(TravelData::custom),
                    Codec.INT.optionalFieldOf("next_custom_id", 0).forGetter(TravelData::nextCustomId),
                    Codec.INT.optionalFieldOf("clock", 0).forGetter(TravelData::clock),
                    WaypointRef.CODEC.optionalFieldOf("last").forGetter(TravelData::last),
                    Codec.LONG.optionalFieldOf("last_logout", 0L).forGetter(TravelData::lastLogout))
            .apply(i, (regions, uses, custom, next, clock, last, logout) ->
                    new TravelData(new HashSet<>(regions), uses, custom, next, clock, last, logout)));

    public TravelData {
        regions = Set.copyOf(regions);
        Map<ResourceLocation, Integer> cappedUses = new HashMap<>();
        uses.forEach((id, n) -> cappedUses.put(id, Math.max(0, Math.min(MAX_USES, n))));
        uses = Map.copyOf(cappedUses);
        custom = List.copyOf(custom.size() > MAX_CUSTOM ? custom.subList(0, MAX_CUSTOM) : custom);
        clock = Math.max(0, Math.min(MAX_CLOCK, clock));
        nextCustomId = Math.max(0, nextCustomId);
    }

    public static TravelData get(Player player) {
        return player.getData(WaypointAttachments.TRAVEL);
    }

    public static void set(Player player, TravelData data) {
        player.setData(WaypointAttachments.TRAVEL, data);
    }

    public boolean hasRegion(ResourceLocation region) {
        return regions.contains(region);
    }

    public int usesOf(ResourceLocation waypoint) {
        return uses.getOrDefault(waypoint, 0);
    }

    public TravelData withRegions(Set<ResourceLocation> newRegions) {
        return new TravelData(newRegions, uses, custom, nextCustomId, clock, last, lastLogout);
    }

    public TravelData withRegion(ResourceLocation region) {
        Set<ResourceLocation> set = new HashSet<>(regions);
        set.add(region);
        return withRegions(set);
    }

    public TravelData withClock(int ticks) {
        return new TravelData(regions, uses, custom, nextCustomId, ticks, last, lastLogout);
    }

    /** Adds ticks to the clock, saturating at {@link #MAX_CLOCK}. */
    public TravelData addClock(long ticks) {
        long sum = (long) clock + ticks;
        return withClock((int) Math.max(0L, Math.min(MAX_CLOCK, sum)));
    }

    public TravelData withLastLogout(long epochMillis) {
        return new TravelData(regions, uses, custom, nextCustomId, clock, last, epochMillis);
    }

    public TravelData withLast(WaypointRef ref) {
        return new TravelData(regions, uses, custom, nextCustomId, clock, Optional.of(ref), lastLogout);
    }

    public TravelData withUse(ResourceLocation waypoint) {
        Map<ResourceLocation, Integer> map = new HashMap<>(uses);
        map.put(waypoint, Math.min(MAX_USES, usesOf(waypoint) + 1));
        return new TravelData(regions, map, custom, nextCustomId, clock, last, lastLogout);
    }

    /** Adds a custom waypoint with the next id; the caller has checked the limit. */
    public TravelData withNewCustom(String name, BlockPos pos) {
        List<Custom> list = new ArrayList<>(custom);
        list.add(new Custom(nextCustomId, name, pos, 0));
        return new TravelData(regions, uses, list, nextCustomId + 1, clock, last, lastLogout);
    }
}
