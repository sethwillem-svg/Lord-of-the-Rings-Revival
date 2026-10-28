package io.github.sethwillemsvg.lotrrevival.core.tag;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/**
 * Shared tag keys (keys only; members are filled by the owning pod's datagen).
 * Per-faction and per-material families (for example {@code faction/<faction_id>},
 * {@code armor/<faction_id>}, {@code ores/<material>}) are added once the faction
 * registry and materials exist.
 */
public final class ModTags {
    private ModTags() {}

    public static final class Blocks {
        public static final TagKey<Block> CRAFTING_TABLES = tag("crafting_tables");
        public static final TagKey<Block> ROAD_SURFACES = tag("road_surfaces");

        private Blocks() {}

        private static TagKey<Block> tag(String path) {
            return TagKey.create(Registries.BLOCK, LOTRRevival.id(path));
        }
    }

    public static final class Items {
        public static final TagKey<Item> WEAPONS_DAGGERS = tag("weapons/daggers");
        public static final TagKey<Item> WEAPONS_SPEARS = tag("weapons/spears");
        public static final TagKey<Item> WEAPONS_PIKES = tag("weapons/pikes");
        public static final TagKey<Item> WEAPONS_BATTLEAXES = tag("weapons/battleaxes");
        public static final TagKey<Item> WEAPONS_HAMMERS = tag("weapons/hammers");
        public static final TagKey<Item> WEAPONS_THROWING = tag("weapons/throwing");
        public static final TagKey<Item> POISONABLE = tag("poisonable");
        public static final TagKey<Item> COINS = tag("coins");

        private Items() {}

        private static TagKey<Item> tag(String path) {
            return TagKey.create(Registries.ITEM, LOTRRevival.id(path));
        }
    }

    public static final class EntityTypes {
        public static final TagKey<EntityType<?>> ORCS = tag("orcs");
        public static final TagKey<EntityType<?>> ELVES = tag("elves");
        public static final TagKey<EntityType<?>> DWARVES = tag("dwarves");
        public static final TagKey<EntityType<?>> HOBBITS = tag("hobbits");
        public static final TagKey<EntityType<?>> MEN = tag("men");
        public static final TagKey<EntityType<?>> TROLLS = tag("trolls");
        public static final TagKey<EntityType<?>> ENTS = tag("ents");
        public static final TagKey<EntityType<?>> MOUNTS = tag("mounts");

        private EntityTypes() {}

        private static TagKey<EntityType<?>> tag(String path) {
            return TagKey.create(Registries.ENTITY_TYPE, LOTRRevival.id(path));
        }
    }

    public static final class Biomes {
        public static final TagKey<Biome> IS_MIDDLE_EARTH = tag("is_middle_earth");

        private Biomes() {}

        private static TagKey<Biome> tag(String path) {
            return TagKey.create(Registries.BIOME, LOTRRevival.id(path));
        }
    }

    /** Faction tags (kind, region, hidden, isolationist). */
    public static final class Factions {
        // TODO(#19): add TagKey<Faction> constants once the faction datapack registry exists.
        private Factions() {}
    }
}
