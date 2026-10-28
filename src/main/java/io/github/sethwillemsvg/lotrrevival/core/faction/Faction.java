package io.github.sethwillemsvg.lotrrevival.core.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;

/**
 * A faction entry of the {@code lotrrevival:faction} datapack registry.
 *
 * @param color packed 0xRRGGBB
 * @param relations explicit relations only; missing pairs are neutral
 */
public record Faction(
        FactionRegion region,
        int color,
        int sortOrder,
        boolean hidden,
        float hostilityThreshold,
        Optional<Suspicion> suspicion,
        boolean disapprovesCivilianKills,
        Optional<Float> fixedAlignment,
        Optional<String> explicitPledgeRank,
        List<FactionRank> ranks,
        Map<ResourceKey<Faction>, FactionRelation> relations,
        List<FactionKind> kinds) {

    /** {@code "#RRGGBB"} string codec for packed colors. */
    public static final Codec<Integer> COLOR_CODEC = Codec.STRING.comapFlatMap(
            s -> s.matches("#[0-9a-fA-F]{6}")
                    ? DataResult.success(Integer.parseInt(s.substring(1), 16))
                    : DataResult.error(() -> "Expected #RRGGBB color, got " + s),
            c -> String.format("#%06X", c & 0xFFFFFF));

    public static final Codec<Faction> CODEC = RecordCodecBuilder.<Faction>create(i -> i.group(
                            FactionRegion.CODEC.fieldOf("region").forGetter(Faction::region),
                            COLOR_CODEC.fieldOf("color").forGetter(Faction::color),
                            Codec.INT.optionalFieldOf("sort_order", 0).forGetter(Faction::sortOrder),
                            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Faction::hidden),
                            Codec.FLOAT.optionalFieldOf("hostility_threshold", 0.0f)
                                    .forGetter(Faction::hostilityThreshold),
                            Suspicion.CODEC.optionalFieldOf("suspicion").forGetter(Faction::suspicion),
                            Codec.BOOL.optionalFieldOf("disapproves_civilian_kills", false)
                                    .forGetter(Faction::disapprovesCivilianKills),
                            Codec.FLOAT.optionalFieldOf("fixed_alignment").forGetter(Faction::fixedAlignment),
                            FactionRank.ID_CODEC.optionalFieldOf("pledge_rank")
                                    .forGetter(Faction::explicitPledgeRank),
                            FactionRank.CODEC.listOf()
                                    .optionalFieldOf("ranks", FactionRank.LADDER_A)
                                    .forGetter(Faction::ranks),
                            Codec.unboundedMap(ResourceKey.codec(ModDataRegistries.FACTION), FactionRelation.CODEC)
                                    .optionalFieldOf("relations", Map.of())
                                    .forGetter(Faction::relations),
                            FactionKind.CODEC.listOf().optionalFieldOf("kinds", List.of()).forGetter(Faction::kinds))
                    .apply(i, Faction::new))
            .validate(f -> f.suspicion().isPresent()
                            && f.suspicion().get().ceiling() <= f.hostilityThreshold()
                    ? DataResult.error(() -> "suspicion ceiling must be above hostility_threshold")
                    : DataResult.success(f));

    /** Relation to another faction; unlisted pairs are neutral. */
    public FactionRelation relationTo(ResourceKey<Faction> other) {
        return relations.getOrDefault(other, FactionRelation.NEUTRAL);
    }

    /** Pledge rank id: the explicit one, else the rank at threshold 100 unless the alignment is fixed. */
    public Optional<String> pledgeRank() {
        if (explicitPledgeRank.isPresent()) {
            return explicitPledgeRank;
        }
        if (fixedAlignment.isPresent()) {
            return Optional.empty();
        }
        return ranks.stream().filter(r -> r.threshold() == 100.0f).map(FactionRank::id).findFirst();
    }

    public boolean isPledgeable() {
        return fixedAlignment.isEmpty() && pledgeRank().isPresent();
    }
}
