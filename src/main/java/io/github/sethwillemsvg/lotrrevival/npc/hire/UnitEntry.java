package io.github.sethwillemsvg.lotrrevival.npc.hire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * One hirable unit in a trader's list.
 *
 * @param id short id, unique in the list
 * @param entity entity type of the unit
 * @param cost base cost C in silver coins
 * @param extraAlignment added to the list's base alignment to give the required alignment R
 * @param pledge pledge condition to hire and to keep the unit
 * @param task what the unit does once hired
 * @param mount entity type of a mount, if the unit is mounted (not shipped yet)
 * @param mountArmorChance chance the mount is armoured (not shipped yet)
 */
public record UnitEntry(
        String id,
        ResourceLocation entity,
        int cost,
        float extraAlignment,
        PledgeCondition pledge,
        UnitTask task,
        Optional<ResourceLocation> mount,
        Optional<Float> mountArmorChance) {
    public static final Codec<UnitEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.fieldOf("id").forGetter(UnitEntry::id),
                    ResourceLocation.CODEC.fieldOf("entity").forGetter(UnitEntry::entity),
                    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("cost").forGetter(UnitEntry::cost),
                    Codec.FLOAT.optionalFieldOf("extra_alignment", 0.0f).forGetter(UnitEntry::extraAlignment),
                    PledgeCondition.CODEC.optionalFieldOf("pledge", PledgeCondition.NONE).forGetter(UnitEntry::pledge),
                    UnitTask.CODEC.optionalFieldOf("task", UnitTask.WARRIOR).forGetter(UnitEntry::task),
                    ResourceLocation.CODEC.optionalFieldOf("mount").forGetter(UnitEntry::mount),
                    Codec.floatRange(0.0f, 1.0f).optionalFieldOf("mount_armor_chance").forGetter(UnitEntry::mountArmorChance))
            .apply(i, UnitEntry::new));

    /** A plain on-foot warrior with no pledge condition. */
    public static UnitEntry warrior(String id, ResourceLocation entity, int cost, float extraAlignment) {
        return new UnitEntry(
                id, entity, cost, extraAlignment, PledgeCondition.NONE, UnitTask.WARRIOR, Optional.empty(), Optional.empty());
    }
}
