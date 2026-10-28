package io.github.sethwillemsvg.lotrrevival.progression.quest;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import java.util.Optional;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/**
 * What a quest asks of the player. Dispatched by {@code type} through {@link QuestObjectives}. Base reward values
 * are fixed from the rolled count {@code N} when the quest starts (spec "Base values").
 */
public sealed interface QuestObjective {
    ResourceLocation type();

    /** Range the target count is rolled from. */
    CountRange count();

    /** Alignment base for the rolled count and the template's reward factor. */
    float alignmentBase(int n, float factor);

    /** Coin base for the rolled count, the reward factor and the alignment base. */
    int coinBase(int n, float factor, float alignmentBase);

    /** Bring items to the giver. */
    record Collect(ItemPredicate item, CountRange count) implements QuestObjective {
        public static final ResourceLocation TYPE = LOTRRevival.id("collect");
        public static final MapCodec<Collect> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        ItemPredicate.CODEC.fieldOf("item").forGetter(Collect::item),
                        CountRange.CODEC.fieldOf("count").forGetter(Collect::count))
                .apply(i, Collect::new));

        @Override
        public ResourceLocation type() {
            return TYPE;
        }

        /** Damage and other components are ignored unless the predicate itself names them. */
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && item.test(stack);
        }

        @Override
        public float alignmentBase(int n, float factor) {
            return Math.max(n * factor, 1.0f);
        }

        @Override
        public int coinBase(int n, float factor, float alignmentBase) {
            return Math.round(alignmentBase * 2.0f);
        }
    }

    /** Kill entities of a type or entity type tag. */
    record KillEntity(HolderSet<EntityType<?>> entities, CountRange count) implements QuestObjective {
        public static final ResourceLocation TYPE = LOTRRevival.id("kill_entity");
        public static final MapCodec<KillEntity> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE)
                                .fieldOf("entity")
                                .forGetter(KillEntity::entities),
                        CountRange.CODEC.fieldOf("count").forGetter(KillEntity::count))
                .apply(i, KillEntity::new));

        @Override
        public ResourceLocation type() {
            return TYPE;
        }

        @SuppressWarnings("deprecation")
        public boolean matches(EntityType<?> victim) {
            return entities.contains(victim.builtInRegistryHolder());
        }

        @Override
        public float alignmentBase(int n, float factor) {
            return n * factor;
        }

        @Override
        public int coinBase(int n, float factor, float alignmentBase) {
            return Math.round(n * 2.0f * factor);
        }
    }

    /** Kill NPCs of one faction or of any faction in a faction tag. Exactly one of the two is set. */
    record KillFaction(Optional<ResourceKey<Faction>> faction, Optional<TagKey<Faction>> factionTag, CountRange count)
            implements QuestObjective {
        public static final ResourceLocation TYPE = LOTRRevival.id("kill_faction");
        public static final MapCodec<KillFaction> MAP_CODEC = RecordCodecBuilder.<KillFaction>mapCodec(i -> i.group(
                        ResourceKey.codec(ModDataRegistries.FACTION)
                                .optionalFieldOf("faction")
                                .forGetter(KillFaction::faction),
                        TagKey.codec(ModDataRegistries.FACTION)
                                .optionalFieldOf("faction_tag")
                                .forGetter(KillFaction::factionTag),
                        CountRange.CODEC.fieldOf("count").forGetter(KillFaction::count))
                .apply(i, KillFaction::new))
                .validate(o -> o.faction().isPresent() == o.factionTag().isPresent()
                        ? DataResult.error(() -> "kill_faction needs exactly one of faction and faction_tag")
                        : DataResult.success(o));

        @Override
        public ResourceLocation type() {
            return TYPE;
        }

        public boolean matches(RegistryAccess access, ResourceKey<Faction> victimFaction) {
            if (faction.isPresent()) {
                return faction.get().equals(victimFaction);
            }
            Registry<Faction> registry = access.registryOrThrow(ModDataRegistries.FACTION);
            Optional<Holder.Reference<Faction>> holder = registry.getHolder(victimFaction);
            return holder.isPresent() && holder.get().is(factionTag.get());
        }

        @Override
        public float alignmentBase(int n, float factor) {
            return n * factor;
        }

        @Override
        public int coinBase(int n, float factor, float alignmentBase) {
            return Math.round(n * 2.0f * factor);
        }
    }

    /** Reserved, shape only: rob NPCs of a faction (later spec). */
    record Pickpocket(ResourceKey<Faction> faction, CountRange count) implements QuestObjective {
        public static final ResourceLocation TYPE = LOTRRevival.id("pickpocket");
        public static final MapCodec<Pickpocket> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        ResourceKey.codec(ModDataRegistries.FACTION)
                                .fieldOf("faction")
                                .forGetter(Pickpocket::faction),
                        CountRange.CODEC.fieldOf("count").forGetter(Pickpocket::count))
                .apply(i, Pickpocket::new));

        @Override
        public ResourceLocation type() {
            return TYPE;
        }

        @Override
        public float alignmentBase(int n, float factor) {
            return Math.max(n * factor, 1.0f);
        }

        @Override
        public int coinBase(int n, float factor, float alignmentBase) {
            return Math.round(alignmentBase * 5.0f);
        }
    }

    /** Reserved, shape only: hunt a player. The count stands for the target's kill count at offer time. */
    record Bounty() implements QuestObjective {
        public static final ResourceLocation TYPE = LOTRRevival.id("bounty");
        public static final MapCodec<Bounty> MAP_CODEC = MapCodec.unit(Bounty::new);

        @Override
        public ResourceLocation type() {
            return TYPE;
        }

        @Override
        public CountRange count() {
            return CountRange.ONE;
        }

        @Override
        public float alignmentBase(int n, float factor) {
            return Mth.clamp(n, 1, 50);
        }

        @Override
        public int coinBase(int n, float factor, float alignmentBase) {
            return Mth.clamp(n * 10, 1, 1000);
        }
    }

    /** Reserved, shape only: a scripted objective (tutorial) driven by its own system. */
    record Scripted(ResourceLocation script) implements QuestObjective {
        public static final ResourceLocation TYPE = LOTRRevival.id("scripted");
        public static final MapCodec<Scripted> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("script").forGetter(Scripted::script))
                .apply(i, Scripted::new));

        @Override
        public ResourceLocation type() {
            return TYPE;
        }

        @Override
        public CountRange count() {
            return CountRange.ONE;
        }

        @Override
        public float alignmentBase(int n, float factor) {
            return 0.0f;
        }

        @Override
        public int coinBase(int n, float factor, float alignmentBase) {
            return 0;
        }
    }
}
