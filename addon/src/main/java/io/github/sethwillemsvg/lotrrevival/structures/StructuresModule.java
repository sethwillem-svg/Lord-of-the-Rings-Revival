package io.github.sethwillemsvg.lotrrevival.structures;

import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.structures.dev.BlockDump;
import io.github.sethwillemsvg.lotrrevival.structures.dev.StructureUpgrade;
import io.github.sethwillemsvg.lotrrevival.structures.venue.ChunkProbe;
import io.github.sethwillemsvg.lotrrevival.structures.venue.StructureVenues;
import io.github.sethwillemsvg.lotrrevival.structures.venue.VenueIndex;
import io.github.sethwillemsvg.lotrrevival.structures.venue.VenueIngest;
import io.github.sethwillemsvg.lotrrevival.structures.venue.VenueKind;
import io.github.sethwillemsvg.lotrrevival.structures.venue.VenueResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.sevenstars.middleearth.registries.DynamicRegistriesME;

/**
 * Structures from licensed builds and event venues (specs/addon/structures.md). Registers the structure type
 * {@code lotr_revival:build}, the placement type {@code lotr_revival:configured_spread}, the data map
 * {@code lotr_revival:structure_info}, the config, the world lock, the venue index and its commands.
 */
public final class StructuresModule {
    static final String SYS = StructureStats.SYS;

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, LotrRevival.MOD_ID);
    public static final DeferredRegister<StructurePlacementType<?>> PLACEMENT_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, LotrRevival.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<BuildStructure>> BUILD =
            STRUCTURE_TYPES.register("build", () -> () -> BuildStructure.CODEC);
    public static final DeferredHolder<StructurePlacementType<?>, StructurePlacementType<ConfiguredSpreadPlacement>> CONFIGURED_SPREAD =
            PLACEMENT_TYPES.register("configured_spread", () -> () -> ConfiguredSpreadPlacement.CODEC);

    private StructuresModule() {}

    public static void init(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        PLACEMENT_TYPES.register(modBus);
        modBus.addListener((RegisterDataMapTypesEvent e) -> e.register(StructureInfo.TYPE));
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, StructuresConfig.SPEC,
                StructuresConfig.FILE);

        NeoForge.EVENT_BUS.addListener(StructuresModule::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(StructuresModule::onServerStarted);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            WorldLock.clear();
            VenueResolver.clear();
        });
        NeoForge.EVENT_BUS.addListener(VenueIngest::onChunkLoad);
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent e) -> {
            VenueResolver.clear();
            LotrLog.dbg(SYS, "venue kinds and info cache cleared ({})", e.getUpdateCause());
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel level) ChunkProbe.tick(level);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            int tick = e.getServer().getTickCount();
            if (tick % StructureStats.SUMMARY_TICKS == 0) StructureStats.summary(tick);
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> VenueCommands.register(e.getDispatcher()));
        io.github.sethwillemsvg.lotrrevival.core.api.StructureVenues.set(new StructureVenues.CoreLookup());
        LotrLog.info(SYS, "registered structure type lotr_revival:build, placement lotr_revival:configured_spread, "
                + "data map lotr_revival:structure_info, config {}", StructuresConfig.FILE);
    }

    private static void onLevelLoad(LevelEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        try {
            if (level.dimension() == Level.OVERWORLD) WorldLock.get(level).apply(level, false);
            if (VenueIngest.indexed(level)) {
                VenueIndex idx = VenueIndex.get(level);
                LotrLog.info(SYS, "venues {}: {} entries ({} complete)", level.dimension().location(), idx.size(), idx.completeCount());
            }
        } catch (RuntimeException ex) {
            LotrLog.error(SYS, "loading the world lock or venue index of " + level.dimension().location() + " failed", ex);
        }
    }

    private static void onServerStarted(ServerStartedEvent e) {
        MinecraftServer server = e.getServer();
        logDataLoad(server);
        boolean dump = BlockDump.runIfAsked(server);
        boolean upgrade = StructureUpgrade.runIfAsked(server);
        if (dump || upgrade) {
            LotrLog.info(SYS, "dev mode finished; stopping the server");
            server.halt(false);
        }
    }

    /** INFO at data load: builds, sets with effective spacing, pools, venue tag sizes, credits; WARN for gaps. */
    static void logDataLoad(MinecraftServer server) {
        var access = server.registryAccess();
        Registry<Structure> structures = access.registryOrThrow(Registries.STRUCTURE);
        List<String> builds = new ArrayList<>();
        int credits = 0;
        for (var entry : structures.entrySet()) {
            if (!(entry.getValue() instanceof BuildStructure b)) continue;
            builds.add(b.build());
            StructureInfo info = structures.getHolderOrThrow(entry.getKey()).getData(StructureInfo.TYPE);
            if (info == null || !info.creditOk()) {
                LotrLog.warn(SYS, "build {} has no valid structure_info credit: it will never place", b.build());
            } else if (info.credit().map(c -> !c.ownWork()).orElse(false)) {
                credits++;
            }
        }
        Registry<StructureSet> sets = access.registryOrThrow(Registries.STRUCTURE_SET);
        List<String> setLines = new ArrayList<>();
        sets.entrySet().forEach(en -> {
            if (en.getValue().placement() instanceof ConfiguredSpreadPlacement p) {
                WorldLock.Entry v = p.values();
                setLines.add(en.getKey().location().getPath() + " " + v.spacing() + "/" + v.separation());
            }
        });
        List<String> pools = new ArrayList<>();
        try {
            access.registryOrThrow(DynamicRegistriesME.STRUCTURE_MANAGER_DATA).keySet().forEach(id -> {
                if (id.getNamespace().equals(LotrRevival.MOD_ID)) pools.add(id.getPath());
            });
        } catch (RuntimeException ex) {
            LotrLog.warn(SYS, "Middle-earth's structure_manager_data registry is not available: {}", ex.toString());
        }
        Map<String, Integer> tagSizes = new TreeMap<>();
        for (VenueKind k : VenueKind.values()) {
            tagSizes.put(k.id(), structures.getTag(k.tag()).map(t -> t.size()).orElse(0));
        }
        LotrLog.info(SYS, "loaded {} builds {}, {} sets [{}], world lock: {}, {} pools {}, venues: {}, {} third-party credits",
                builds.size(), builds, setLines.size(), String.join(", ", setLines), WorldLock.describe(), pools.size(), pools,
                tagSizes, credits);
        // TODO(spec §Edge cases): WARN once per template whose raw palette names a block missing from the registry
        // (TemplateTests covers it in CI).
    }
}
