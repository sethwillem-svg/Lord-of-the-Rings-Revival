package io.github.sethwillemsvg.lotrrevival.structures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * Data map {@code lotr_revival:structure_info} on {@code minecraft:worldgen/structure}: who lives in a structure and who
 * built it. Our builds' entries are written by tools/structure-import; Middle-earth's come from
 * tools/structure-import/venues_middle_earth.json.
 */
public record StructureInfo(Optional<ResourceLocation> faction, boolean residents, Optional<String> region, int groundOffset,
                            Optional<String> build, Optional<Credit> credit) {

    /** Output licences an adapted template may carry (the tool maps CC 3.0 sources to 4.0). */
    public static final Set<String> ACCEPTED_LICENCES = Set.of("CC0-1.0", "CC-BY-4.0", "CC-BY-SA-4.0", "LicenseRef-ASSETS_LICENSE");

    public record Credit(String title, String author, Optional<String> sourceUrl, String licence,
                         Optional<String> sourceLicence, Optional<String> licenceUrl, Optional<String> changes,
                         Optional<String> attribution, boolean ownWork) {
        public static final Codec<Credit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("title").forGetter(Credit::title),
                Codec.STRING.fieldOf("author").forGetter(Credit::author),
                Codec.STRING.optionalFieldOf("source_url").forGetter(Credit::sourceUrl),
                Codec.STRING.fieldOf("licence").forGetter(Credit::licence),
                Codec.STRING.optionalFieldOf("source_licence").forGetter(Credit::sourceLicence),
                Codec.STRING.optionalFieldOf("licence_url").forGetter(Credit::licenceUrl),
                Codec.STRING.optionalFieldOf("changes").forGetter(Credit::changes),
                Codec.STRING.optionalFieldOf("attribution").forGetter(Credit::attribution),
                Codec.BOOL.optionalFieldOf("own_work", false).forGetter(Credit::ownWork)
        ).apply(i, Credit::new));
    }

    public static final Codec<StructureInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(StructureInfo::faction),
            Codec.BOOL.optionalFieldOf("residents", false).forGetter(StructureInfo::residents),
            Codec.STRING.optionalFieldOf("region").forGetter(StructureInfo::region),
            Codec.INT.optionalFieldOf("ground_offset", 0).forGetter(StructureInfo::groundOffset),
            Codec.STRING.optionalFieldOf("build").forGetter(StructureInfo::build),
            Credit.CODEC.optionalFieldOf("credit").forGetter(StructureInfo::credit)
    ).apply(i, StructureInfo::new));

    public static final DataMapType<Structure, StructureInfo> TYPE =
            DataMapType.builder(LotrRevival.id("structure_info"), Registries.STRUCTURE, CODEC).build();

    public static final StructureInfo NONE = new StructureInfo(Optional.empty(), false, Optional.empty(), 0, Optional.empty(),
            Optional.empty());

    /** The entry for a structure id, or empty. Server side; needs the level's registries. */
    public static Optional<StructureInfo> of(RegistryAccess access, ResourceLocation structure) {
        Registry<Structure> reg = access.registryOrThrow(Registries.STRUCTURE);
        return reg.getHolder(ResourceKey.create(Registries.STRUCTURE, structure)).map(h -> h.getData(TYPE));
    }

    public static Optional<StructureInfo> of(Holder<Structure> holder) {
        return Optional.ofNullable(holder.getData(TYPE));
    }

    /** A credit that lets a template under build/ place: own work, or an accepted licence with author and source. */
    public boolean creditOk() {
        return credit.map(c -> c.ownWork() || (ACCEPTED_LICENCES.contains(c.licence()) && !c.author().isBlank()
                && c.sourceUrl().isPresent())).orElse(false);
    }
}
