package io.github.sethwillemsvg.lotrrevival.progression.achievement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.FactionRank;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * Display half of an achievement: an entry of the synced datapack registry {@code lotrrevival:achievement}.
 * The backing half is an advancement with the same id and no display block.
 *
 * @param requires all must hold for the entry to be eligible; mutually exclusive with {@code rank}
 * @param rank rank entries carry this instead of {@code requires}
 * @param counted false for anything outside Middle-earth
 */
public record AchievementEntry(
        AchievementTab tab,
        ItemStack icon,
        Component title,
        Component description,
        SortGroup sortGroup,
        List<Requirement> requires,
        Optional<RankRequirement> rank,
        boolean counted) {

    /** {@code lotrrevival:achievement}, files at {@code data/<ns>/lotrrevival/achievement/<tab>/<name>.json}. */
    public static final ResourceKey<Registry<AchievementEntry>> REGISTRY = ModDataRegistries.achievement();

    /** Page order group: {@code special} by id, then {@code biome}, then {@code other} alphabetically. */
    public enum SortGroup implements StringRepresentable {
        SPECIAL("special"),
        BIOME("biome"),
        OTHER("other");

        public static final Codec<SortGroup> CODEC = StringRepresentable.fromEnum(SortGroup::values);

        private final String id;

        SortGroup(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    /** Relation the player's alignment must have with one of the factions. */
    public enum Relation implements StringRepresentable {
        ALLY("ally"),
        ENEMY("enemy");

        public static final Codec<Relation> CODEC = StringRepresentable.fromEnum(Relation::values);

        private final String id;

        Relation(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    /** {@code relation} plus {@code factions}; any one listed faction satisfies it. */
    public record Requirement(Relation relation, List<ResourceKey<Faction>> factions) {
        public static final Codec<Requirement> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Relation.CODEC.fieldOf("relation").forGetter(Requirement::relation),
                        ResourceKey.codec(ModDataRegistries.FACTION)
                                .listOf()
                                .validate(list -> list.isEmpty()
                                        ? DataResult.error(() -> "factions must not be empty")
                                        : DataResult.success(list))
                                .fieldOf("factions")
                                .forGetter(Requirement::factions))
                .apply(i, Requirement::new));
    }

    /** A rank entry: {@code rank} is the Core rank id ({@link FactionRank#id()}, for example {@code r3}). */
    public record RankRequirement(ResourceKey<Faction> faction, String rank) {
        public static final Codec<RankRequirement> CODEC = RecordCodecBuilder.create(i -> i.group(
                        ResourceKey.codec(ModDataRegistries.FACTION)
                                .fieldOf("faction")
                                .forGetter(RankRequirement::faction),
                        FactionRank.ID_CODEC.fieldOf("rank").forGetter(RankRequirement::rank))
                .apply(i, RankRequirement::new));
    }

    /** JSON codec; also the network codec of the synced registry. */
    public static final Codec<AchievementEntry> CODEC = RecordCodecBuilder.<AchievementEntry>create(i -> i.group(
                    AchievementTab.CODEC.fieldOf("tab").forGetter(AchievementEntry::tab),
                    ItemStack.CODEC.fieldOf("icon").forGetter(AchievementEntry::icon),
                    ComponentSerialization.CODEC.fieldOf("title").forGetter(AchievementEntry::title),
                    ComponentSerialization.CODEC.fieldOf("description").forGetter(AchievementEntry::description),
                    SortGroup.CODEC.fieldOf("sort_group").forGetter(AchievementEntry::sortGroup),
                    Requirement.CODEC
                            .listOf()
                            .optionalFieldOf("requires", List.of())
                            .forGetter(AchievementEntry::requires),
                    RankRequirement.CODEC.optionalFieldOf("rank").forGetter(AchievementEntry::rank),
                    Codec.BOOL.optionalFieldOf("counted", true).forGetter(AchievementEntry::counted))
            .apply(i, AchievementEntry::new))
            .validate(entry -> !entry.requires().isEmpty() && entry.rank().isPresent()
                    ? DataResult.error(() -> "requires and rank are mutually exclusive")
                    : DataResult.success(entry));
}
