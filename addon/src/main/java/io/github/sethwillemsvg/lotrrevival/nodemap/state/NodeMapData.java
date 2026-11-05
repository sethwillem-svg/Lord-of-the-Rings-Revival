package io.github.sethwillemsvg.lotrrevival.nodemap.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeRef;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Per-player node map data (attachment {@code lotr_revival:nodemap}, copied on death): known and rumoured nodes, home
 * reveal flag, camps, the travel cooldown, the last arrival and the trip count. Mutable; saved by {@link #CODEC}.
 */
public final class NodeMapData {
    public static final int MAX_STORED_CAMPS = 64;

    public record Camp(int id, String name, BlockPos pos, long created) {
        public static final Codec<Camp> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Camp::id),
                Codec.STRING.fieldOf("name").forGetter(Camp::name),
                BlockPos.CODEC.fieldOf("pos").forGetter(Camp::pos),
                Codec.LONG.fieldOf("created").forGetter(Camp::created)
        ).apply(i, Camp::new));
    }

    public static final Codec<NodeMapData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("known", List.of()).forGetter(d -> List.copyOf(d.known)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("rumoured", List.of()).forGetter(d -> List.copyOf(d.rumoured)),
            Codec.BOOL.optionalFieldOf("home_revealed", false).forGetter(d -> d.homeRevealed),
            Camp.CODEC.listOf().optionalFieldOf("camps", List.of()).forGetter(d -> List.copyOf(d.camps)),
            Codec.INT.optionalFieldOf("next_camp_id", 1).forGetter(d -> d.nextCampId),
            Codec.INT.optionalFieldOf("cooldown", 0).forGetter(d -> d.cooldown),
            NodeRef.CODEC.optionalFieldOf("last_node").forGetter(d -> Optional.ofNullable(d.lastNode)),
            Codec.INT.optionalFieldOf("trips", 0).forGetter(d -> d.trips)
    ).apply(i, NodeMapData::new));

    private final Set<ResourceLocation> known = new LinkedHashSet<>();
    private final Set<ResourceLocation> rumoured = new LinkedHashSet<>();
    private boolean homeRevealed;
    private final List<Camp> camps = new ArrayList<>();
    private int nextCampId = 1;
    private int cooldown;
    @Nullable private NodeRef lastNode;
    private int trips;

    public NodeMapData() {}

    private NodeMapData(List<ResourceLocation> known, List<ResourceLocation> rumoured, boolean homeRevealed, List<Camp> camps,
                        int nextCampId, int cooldown, Optional<NodeRef> lastNode, int trips) {
        this.known.addAll(known);
        rumoured.forEach(r -> { if (!this.known.contains(r)) this.rumoured.add(r); });
        this.homeRevealed = homeRevealed;
        this.camps.addAll(camps.subList(0, Math.min(camps.size(), MAX_STORED_CAMPS)));
        this.nextCampId = nextCampId;
        this.cooldown = Math.max(0, cooldown);
        this.lastNode = lastNode.orElse(null);
        this.trips = trips;
    }

    public NodeMapData copy() {
        return new NodeMapData(List.copyOf(known), List.copyOf(rumoured), homeRevealed, camps, nextCampId, cooldown,
                Optional.ofNullable(lastNode), trips);
    }

    public Set<ResourceLocation> known() { return java.util.Collections.unmodifiableSet(known); }
    public Set<ResourceLocation> rumoured() { return java.util.Collections.unmodifiableSet(rumoured); }

    public boolean isKnown(ResourceLocation id) { return known.contains(id); }
    public boolean isRumoured(ResourceLocation id) { return rumoured.contains(id); }

    /** Makes the node known (and no longer only rumoured); true if it was not known before. */
    public boolean addKnown(ResourceLocation id) {
        rumoured.remove(id);
        return known.add(id);
    }

    /** Rumours a node that is not known yet; true if it is newly rumoured. */
    public boolean addRumoured(ResourceLocation id) {
        return !known.contains(id) && rumoured.add(id);
    }

    public boolean forget(ResourceLocation id) {
        return known.remove(id) | rumoured.remove(id);
    }

    public void forgetAll() {
        known.clear();
        rumoured.clear();
    }

    public boolean homeRevealed() { return homeRevealed; }
    public void setHomeRevealed(boolean v) { homeRevealed = v; }

    public List<Camp> camps() { return java.util.Collections.unmodifiableList(camps); }

    @Nullable
    public Camp camp(int id) {
        for (Camp c : camps) if (c.id() == id) return c;
        return null;
    }

    public Camp addCamp(String name, BlockPos pos, long time) {
        Camp c = new Camp(nextCampId++, name, pos.immutable(), time);
        camps.add(c);
        return c;
    }

    public boolean renameCamp(int id, String name) {
        for (int i = 0; i < camps.size(); i++) {
            Camp c = camps.get(i);
            if (c.id() == id) {
                camps.set(i, new Camp(id, name, c.pos(), c.created()));
                return true;
            }
        }
        return false;
    }

    public boolean removeCamp(int id) {
        return camps.removeIf(c -> c.id() == id);
    }

    public int nextCampId() { return nextCampId; }
    public int cooldown() { return cooldown; }
    public void setCooldown(int ticks) { cooldown = Math.max(0, ticks); }

    /** One tick of cooldown; true when it just reached 0. */
    public boolean tickCooldown() {
        if (cooldown <= 0) return false;
        return --cooldown == 0;
    }

    @Nullable public NodeRef lastNode() { return lastNode; }
    public void setLastNode(@Nullable NodeRef ref) { lastNode = ref; }
    public int trips() { return trips; }
    public void addTrip() { trips++; }

    /** Field-by-field equality, for the persistence test. */
    public boolean sameAs(NodeMapData o) {
        return known.equals(o.known) && rumoured.equals(o.rumoured) && homeRevealed == o.homeRevealed && camps.equals(o.camps)
                && nextCampId == o.nextCampId && cooldown == o.cooldown && java.util.Objects.equals(lastNode, o.lastNode)
                && trips == o.trips;
    }

    @Override
    public String toString() {
        return "known=" + known.size() + " rumoured=" + rumoured.size() + " camps=" + camps.size() + " cooldown=" + cooldown
                + " last=" + lastNode + " trips=" + trips + " home_revealed=" + homeRevealed;
    }
}
