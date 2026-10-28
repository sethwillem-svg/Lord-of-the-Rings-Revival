package io.github.sethwillemsvg.lotrrevival.npc.hire;

import com.mojang.serialization.Codec;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionKind;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;

/** What a player must be pledged to for a unit entry to be hirable (and to keep the unit). */
public enum PledgeCondition implements StringRepresentable {
    /** No pledge needed. */
    NONE("none"),
    /** Pledged to the unit's own faction. */
    FACTION("faction"),
    /** Pledged to an elven faction that is not also a men faction (so Dorwinion does not count). */
    ANY_ELF("any_elf"),
    /** Pledged to a dwarven faction. */
    ANY_DWARF("any_dwarf");

    public static final Codec<PledgeCondition> CODEC = StringRepresentable.fromEnum(PledgeCondition::values);

    private final String serializedName;

    PledgeCondition(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /**
     * Pure form of the check.
     *
     * @param unitFaction the faction of the unit
     * @param pledge the player's pledge, if any
     * @param hasKind whether the pledged faction has the given kind tag
     */
    public boolean holds(
            ResourceKey<Faction> unitFaction, Optional<ResourceKey<Faction>> pledge, Predicate<FactionKind> hasKind) {
        return switch (this) {
            case NONE -> true;
            case FACTION -> pledge.isPresent() && pledge.get().equals(unitFaction);
            case ANY_ELF -> pledge.isPresent() && hasKind.test(FactionKind.ELF) && !hasKind.test(FactionKind.MEN);
            case ANY_DWARF -> pledge.isPresent() && hasKind.test(FactionKind.DWARF);
        };
    }

    /** Check against the faction registry and kind tags of a level. */
    public boolean holds(Level level, ResourceKey<Faction> unitFaction, Optional<ResourceKey<Faction>> pledge) {
        if (this == NONE) {
            return true;
        }
        Registry<Faction> registry = level.registryAccess().registryOrThrow(ModDataRegistries.FACTION);
        return holds(unitFaction, pledge, kind -> pledge.flatMap(registry::getHolder)
                .map(holder -> holder.is(ModTags.Factions.kind(kind)))
                .orElse(false));
    }
}
