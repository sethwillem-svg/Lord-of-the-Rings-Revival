package io.github.sethwillemsvg.lotrrevival.npc.anchor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * One structure-bound spawn point. It lives in the chunk that holds {@code pos}.
 *
 * @param pos the home point of every NPC of this anchor
 * @param typeId entity type id (kept as an id so a removed type does not make the whole chunk unreadable)
 * @param role role to give each NPC; empty rolls it like a normal spawn
 * @param count how many living NPCs the anchor keeps
 * @param homeRadius home radius given to each NPC
 * @param npcs UUIDs of the NPCs that currently belong to the anchor
 * @param deaths game times at which a member died and has not been replaced yet
 */
public record SpawnAnchor(
        BlockPos pos,
        ResourceLocation typeId,
        Optional<NpcRole> role,
        int count,
        int homeRadius,
        List<UUID> npcs,
        List<Long> deaths) {
    public static final Codec<SpawnAnchor> CODEC = RecordCodecBuilder.create(i -> i.group(
                    BlockPos.CODEC.fieldOf("pos").forGetter(SpawnAnchor::pos),
                    ResourceLocation.CODEC.fieldOf("type").forGetter(SpawnAnchor::typeId),
                    NpcRole.CODEC.optionalFieldOf("role").forGetter(SpawnAnchor::role),
                    Codec.INT.fieldOf("count").forGetter(SpawnAnchor::count),
                    Codec.INT.fieldOf("home_radius").forGetter(SpawnAnchor::homeRadius),
                    UUIDUtil.CODEC.listOf().optionalFieldOf("npcs", List.of()).forGetter(SpawnAnchor::npcs),
                    Codec.LONG.listOf().optionalFieldOf("deaths", List.of()).forGetter(SpawnAnchor::deaths))
            .apply(i, SpawnAnchor::new));

    public SpawnAnchor {
        pos = pos.immutable();
        npcs = List.copyOf(npcs);
        deaths = List.copyOf(deaths);
    }

    public static SpawnAnchor create(
            BlockPos pos, EntityType<?> type, Optional<NpcRole> role, int count, int homeRadius) {
        return new SpawnAnchor(
                pos, BuiltInRegistries.ENTITY_TYPE.getKey(type), role, count, homeRadius, List.of(), List.of());
    }

    public Optional<EntityType<?>> entityType() {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(typeId);
    }

    public SpawnAnchor withNpcs(List<UUID> newNpcs) {
        return new SpawnAnchor(pos, typeId, role, count, homeRadius, newNpcs, deaths);
    }

    public SpawnAnchor withDeaths(List<Long> newDeaths) {
        return new SpawnAnchor(pos, typeId, role, count, homeRadius, npcs, newDeaths);
    }

    /** Same anchor with one more member. */
    public SpawnAnchor withMember(UUID id) {
        List<UUID> list = new ArrayList<>(npcs);
        list.add(id);
        return withNpcs(list);
    }

    /** Whether this is the same spawn point and kind of NPC (member lists and timers ignored). */
    public boolean sameSpot(BlockPos otherPos, ResourceLocation otherType) {
        return pos.equals(otherPos) && typeId.equals(otherType);
    }
}
