package io.github.sethwillemsvg.lotrrevival.core.registry;

import com.mojang.serialization.MapCodec;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The single home for every DeferredRegister in the mod. Owned by the Core pod.
 *
 * <p>Pods add entries in their own classes (for example {@code world.ModBiomeBlocks})
 * that reference these registers. They do not create new DeferredRegisters.
 */
public final class ModRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(LOTRRevival.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LOTRRevival.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LOTRRevival.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, LOTRRevival.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, LOTRRevival.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, LOTRRevival.MOD_ID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(LOTRRevival.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LOTRRevival.MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, LOTRRevival.MOD_ID);
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, LOTRRevival.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, LOTRRevival.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, LOTRRevival.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<MemoryModuleType<?>> MEMORY_MODULE_TYPES =
            DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<SensorType<?>> SENSOR_TYPES =
            DeferredRegister.create(Registries.SENSOR_TYPE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGER_TYPES =
            DeferredRegister.create(Registries.TRIGGER_TYPE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<LootItemConditionType> LOOT_CONDITION_TYPES =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTION_TYPES =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, LOTRRevival.MOD_ID);
    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPES =
            DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, LOTRRevival.MOD_ID);

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, LOTRRevival.MOD_ID);

    private ModRegistries() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        MOB_EFFECTS.register(modEventBus);
        ARMOR_MATERIALS.register(modEventBus);
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        FEATURES.register(modEventBus);
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECES.register(modEventBus);
        CHUNK_GENERATORS.register(modEventBus);
        BIOME_SOURCES.register(modEventBus);
        POI_TYPES.register(modEventBus);
        MEMORY_MODULE_TYPES.register(modEventBus);
        SENSOR_TYPES.register(modEventBus);
        TRIGGER_TYPES.register(modEventBus);
        LOOT_CONDITION_TYPES.register(modEventBus);
        LOOT_FUNCTION_TYPES.register(modEventBus);
        COMMAND_ARGUMENT_TYPES.register(modEventBus);
        DENSITY_FUNCTION_TYPES.register(modEventBus);
    }
}
