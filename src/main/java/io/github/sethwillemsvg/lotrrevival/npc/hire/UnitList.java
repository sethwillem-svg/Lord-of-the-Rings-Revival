package io.github.sethwillemsvg.lotrrevival.npc.hire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

/**
 * The units one trader role hires out.
 *
 * @param baseAlignment list base alignment; an entry's required alignment is this plus its extra alignment
 * @param units the entries
 */
public record UnitList(float baseAlignment, List<UnitEntry> units) {
    public static final Codec<UnitList> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.FLOAT.fieldOf("base_alignment").forGetter(UnitList::baseAlignment),
                    UnitEntry.CODEC.listOf().fieldOf("units").forGetter(UnitList::units))
            .apply(i, UnitList::new));

    /** Required alignment R for an entry of this list. */
    public float requiredAlignment(UnitEntry entry) {
        return baseAlignment + entry.extraAlignment();
    }

    public Optional<UnitEntry> entry(String id) {
        return units.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }
}
