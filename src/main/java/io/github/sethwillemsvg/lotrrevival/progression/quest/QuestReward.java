package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * An extra reward of a template ({@code extra_rewards}). The base alignment and coin rewards come from the
 * template's reward factor; these add to them. Dispatched by {@code type} through {@link QuestRewardTypes}.
 * Reward types only call into the builder; Progression applies it afterwards through Core APIs.
 */
public sealed interface QuestReward {
    ResourceLocation type();

    /** Adds this reward to the pile for one completion. */
    void grant(RewardBuilder rewards);

    /** A flat extra amount of alignment for the group's reward faction, without variance. */
    record Alignment(float amount) implements QuestReward {
        public static final ResourceLocation TYPE = LOTRRevival.id("alignment");
        public static final MapCodec<Alignment> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        com.mojang.serialization.Codec.FLOAT.fieldOf("amount").forGetter(Alignment::amount))
                .apply(i, Alignment::new));

        @Override
        public ResourceLocation type() {
            return TYPE;
        }

        @Override
        public void grant(RewardBuilder rewards) {
            rewards.addAlignment(amount);
        }
    }

    /** A flat extra amount of coins, without variance. */
    record Coins(int amount) implements QuestReward {
        public static final ResourceLocation TYPE = LOTRRevival.id("coins");
        public static final MapCodec<Coins> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        com.mojang.serialization.Codec.INT.fieldOf("amount").forGetter(Coins::amount))
                .apply(i, Coins::new));

        @Override
        public ResourceLocation type() {
            return TYPE;
        }

        @Override
        public void grant(RewardBuilder rewards) {
            rewards.addCoins(amount);
        }
    }

    /** Reserved, shape only: one random item from a loot table. Grants nothing yet. */
    record ItemTable(ResourceLocation lootTable) implements QuestReward {
        public static final ResourceLocation TYPE = LOTRRevival.id("item_table");
        public static final MapCodec<ItemTable> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("loot_table").forGetter(ItemTable::lootTable))
                .apply(i, ItemTable::new));

        @Override
        public ResourceLocation type() {
            return TYPE;
        }

        @Override
        public void grant(RewardBuilder rewards) {
            // Reserved: the content spec decides how item tables are rolled.
        }
    }

    /** Reserved: the giver joins the player as a hired unit. Only records the flag; the NPC pod does the hiring. */
    record HireGiver() implements QuestReward {
        public static final ResourceLocation TYPE = LOTRRevival.id("hire_giver");
        public static final MapCodec<HireGiver> MAP_CODEC = MapCodec.unit(HireGiver::new);

        @Override
        public ResourceLocation type() {
            return TYPE;
        }

        @Override
        public void grant(RewardBuilder rewards) {
            rewards.setHired(true);
        }
    }

    /** Reserved, shape only: a lore book from the listed categories. Grants nothing yet. */
    record LoreBook(List<ResourceLocation> categories) implements QuestReward {
        public static final ResourceLocation TYPE = LOTRRevival.id("lore_book");
        public static final MapCodec<LoreBook> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        ResourceLocation.CODEC
                                .listOf()
                                .optionalFieldOf("categories", List.of())
                                .forGetter(LoreBook::categories))
                .apply(i, LoreBook::new));

        @Override
        public ResourceLocation type() {
            return TYPE;
        }

        @Override
        public void grant(RewardBuilder rewards) {
            // Reserved: lore books arrive with the lore system.
        }
    }
}
