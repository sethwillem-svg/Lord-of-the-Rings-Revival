package io.github.sethwillemsvg.lotrrevival.progression.quest.offer;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/** Tags of the quest offer system. */
public final class OfferTags {
    /** NPC entity types that may give quests; datagen fills it with {@code #lotrrevival:npcs}. */
    public static final TagKey<EntityType<?>> QUEST_GIVERS =
            TagKey.create(Registries.ENTITY_TYPE, LOTRRevival.id("quest_givers"));

    private OfferTags() {}
}
