package io.github.sethwillemsvg.lotrrevival.progression.title;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/** Where a title comes from, dispatched on the JSON field {@code type}. See {@link Titles#isUsable}. */
public sealed interface TitleSource {
    Type type();

    /** The {@code type} values. */
    enum Type implements StringRepresentable {
        STARTER("starter"),
        SPECIAL("special"),
        ALIGNMENT("alignment"),
        ACHIEVEMENT("achievement"),
        ALIGNMENT_RANK("alignment_rank");

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public MapCodec<? extends TitleSource> codec() {
            return switch (this) {
                case STARTER -> Starter.CODEC;
                case SPECIAL -> Special.CODEC;
                case ALIGNMENT -> FactionAlignment.CODEC;
                case ACHIEVEMENT -> AchievementSource.CODEC;
                case ALIGNMENT_RANK -> AlignmentRank.CODEC;
            };
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    Codec<TitleSource> CODEC = Type.CODEC.dispatch("type", TitleSource::type, Type::codec);

    /** Always usable. */
    record Starter() implements TitleSource {
        public static final Starter INSTANCE = new Starter();
        public static final MapCodec<Starter> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public Type type() {
            return Type.STARTER;
        }
    }

    /** Usable when granted by command or code. */
    record Special() implements TitleSource {
        public static final Special INSTANCE = new Special();
        public static final MapCodec<Special> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public Type type() {
            return Type.SPECIAL;
        }
    }

    /**
     * Alignment with any one listed faction reaches {@code min}; an empty {@code min} means the faction's
     * pledge-rank threshold (the JSON value {@code "pledge"}).
     */
    record FactionAlignment(List<ResourceKey<Faction>> factions, Optional<Float> min) implements TitleSource {
        private static final String PLEDGE = "pledge";

        private static final Codec<Optional<Float>> MIN_CODEC = Codec.either(
                        Codec.FLOAT,
                        Codec.STRING.validate(s -> PLEDGE.equals(s)
                                ? DataResult.success(s)
                                : DataResult.error(() -> "Expected a number or \"pledge\"")))
                .xmap(
                        either -> either.map(Optional::of, s -> Optional.<Float>empty()),
                        min -> min.<Either<Float, String>>map(Either::left).orElseGet(() -> Either.right(PLEDGE)));

        public static final MapCodec<FactionAlignment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        ResourceKey.codec(ModDataRegistries.FACTION)
                                .listOf()
                                .validate(list -> list.isEmpty()
                                        ? DataResult.error(() -> "factions must not be empty")
                                        : DataResult.success(list))
                                .fieldOf("factions")
                                .forGetter(FactionAlignment::factions),
                        MIN_CODEC.fieldOf("min").forGetter(FactionAlignment::min))
                .apply(i, FactionAlignment::new));

        @Override
        public Type type() {
            return Type.ALIGNMENT;
        }
    }

    /** Usable when the achievement is earned, eligible and counted. */
    record AchievementSource(ResourceLocation achievement) implements TitleSource {
        public static final MapCodec<AchievementSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("achievement").forGetter(AchievementSource::achievement))
                .apply(i, AchievementSource::new));

        @Override
        public Type type() {
            return Type.ACHIEVEMENT;
        }
    }

    /** Usable at {@code rank} (a Core rank id such as {@code r3}) with the faction. */
    record AlignmentRank(ResourceKey<Faction> faction, String rank) implements TitleSource {
        public static final MapCodec<AlignmentRank> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        ResourceKey.codec(ModDataRegistries.FACTION).fieldOf("faction").forGetter(AlignmentRank::faction),
                        FactionRank.ID_CODEC.fieldOf("rank").forGetter(AlignmentRank::rank))
                .apply(i, AlignmentRank::new));

        @Override
        public Type type() {
            return Type.ALIGNMENT_RANK;
        }
    }
}
