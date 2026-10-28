package io.github.sethwillemsvg.lotrrevival.progression.datagen.banner;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingBlocks;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.material.MaterialBlocks;
import io.github.sethwillemsvg.lotrrevival.core.material.Metal;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerBlocks;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerComponents;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerDesign;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerPermission;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerTags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Datagen of the banner system: designs, block tags, block states and models, loot and lang. */
public final class BannerDatagen {
    /** Cloth colour and emblem colour of the design of each faction (our own palette). */
    private static final Map<ResourceKey<Faction>, int[]> COLORS = new LinkedHashMap<>();

    static {
        colors(Factions.HOBBIT, 0x4F7A3A, 0xE8D9A0);
        colors(Factions.BREE, 0x7A4B2A, 0xF0E0B0);
        colors(Factions.DUNEDAIN_NORTH, 0x2F4A3A, 0xC9D1C8);
        colors(Factions.BLUE_MOUNTAINS, 0x2D4F8A, 0xD7B968);
        colors(Factions.HIGH_ELF, 0xE3E6F2, 0x3A5FA8);
        colors(Factions.GUNDABAD, 0x4A3B2E, 0xB03A2E);
        colors(Factions.ANGMAR, 0x3A4650, 0xB8C4CC);
        colors(Factions.WOOD_ELF, 0x3F6B3A, 0xE0C060);
        colors(Factions.DOL_GULDUR, 0x2B2B3A, 0x7A5AB0);
        colors(Factions.DALE, 0x7A2E2E, 0xE6C46A);
        colors(Factions.DURINS_FOLK, 0x35507A, 0xD9D9D9);
        colors(Factions.LOTHLORIEN, 0xD9C77A, 0x4C7A4C);
        colors(Factions.DUNLAND, 0x5A4A3A, 0xC08040);
        colors(Factions.ISENGARD, 0x23272B, 0xE8E8E8);
        colors(Factions.FANGORN, 0x2F5A32, 0x9BC27A);
        colors(Factions.ROHAN, 0x2F5D3A, 0xE0D07A);
        colors(Factions.GONDOR, 0xEDEDED, 0x1E1E1E);
        colors(Factions.MORDOR, 0x1A1A1A, 0xC0352B);
        colors(Factions.DORWINION, 0x6A2E7A, 0xE8C86A);
        colors(Factions.EASTERLING, 0x8A2E1F, 0xE6C050);
        colors(Factions.NEAR_HARAD, 0x8A1E1E, 0x1A1A1A);
        colors(Factions.FAR_HARAD, 0x1E1E1E, 0xD8B040);
        colors(Factions.HARAD_JUNGLE, 0x2E6A4A, 0xD8C050);
        colors(Factions.HALF_TROLL, 0x4A4A2A, 0xA03030);
        colors(Factions.UTUMNO, 0x120A0A, 0xC04010);
    }

    private BannerDatagen() {}

    private static void colors(ResourceKey<Faction> faction, int cloth, int emblem) {
        COLORS.put(faction, new int[] {cloth, emblem});
    }

    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        ExistingFileHelper files = event.getExistingFileHelper();

        for (ResourceKey<Faction> faction : Factions.REAL) {
            if (!COLORS.containsKey(faction)) {
                throw new IllegalStateException("No banner design colours for " + faction.location());
            }
        }
        registerLang();
        generator.addProvider(event.includeServer(), new Designs(output));
        generator.addProvider(event.includeServer(), new Tags(output, lookup, files));
        generator.addProvider(event.includeServer(), new Loot(output, lookup));
        generator.addProvider(event.includeClient(), new BlockStates(output, files));
        generator.addProvider(event.includeClient(), new ItemModels(output, files));
    }

    // ------------------------------------------------------------------ designs

    /**
     * One design per faction at {@code data/lotrrevival/lotrrevival/banner_design/<faction>.json}. A plain provider
     * (not part of the shared registry provider) so later progression registries need no change in DataGenerators.
     */
    static final class Designs implements DataProvider {
        private final PackOutput output;

        Designs(PackOutput output) {
            this.output = output;
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            PackOutput.PathProvider paths = output.createRegistryElementsPathProvider(BannerDesign.REGISTRY);
            List<CompletableFuture<?>> writes = new ArrayList<>();
            for (ResourceKey<Faction> faction : Factions.REAL) {
                int[] palette = COLORS.get(faction);
                BannerDesign design = new BannerDesign(faction, palette[0], palette[1], Optional.empty());
                JsonElement json = BannerDesign.CODEC
                        .encodeStart(JsonOps.INSTANCE, design)
                        .getOrThrow(error -> new IllegalStateException("banner design " + faction.location() + ": " + error));
                writes.add(DataProvider.saveStable(cache, json, paths.json(BannerDesign.idFor(faction))));
            }
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }

        @Override
        public String getName() {
            return "Banner designs";
        }
    }

    // ------------------------------------------------------------------ tags

    static final class Tags extends BlockTagsProvider {
        Tags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
            super(output, lookup, LOTRRevival.MOD_ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            // Bases: vanilla gold plus Core's silver and bronze storage blocks (core.material, #108).
            tag(BannerTags.BASE_GOLD).add(Blocks.GOLD_BLOCK);
            tag(BannerTags.BASE_SILVER).add(MaterialBlocks.BLOCK.get(Metal.SILVER).get());
            tag(BannerTags.BASE_BRONZE).add(MaterialBlocks.BLOCK.get(Metal.BRONZE).get());

            tag(BannerPermission.PERSONAL_CONTAINERS.tag()).add(Blocks.ENDER_CHEST);
            tag(BannerPermission.DOORS.tag())
                    .addTag(BlockTags.DOORS)
                    .addTag(BlockTags.TRAPDOORS)
                    .addTag(BlockTags.FENCE_GATES);
            tag(BannerPermission.BEDS.tag()).addTag(BlockTags.BEDS);
            tag(BannerPermission.SWITCHES.tag())
                    .addTag(BlockTags.BUTTONS)
                    .add(Blocks.LEVER)
                    .addTag(BlockTags.PRESSURE_PLATES);
            tag(BannerPermission.FOOD.tag())
                    .add(Blocks.CAKE)
                    .addTag(BlockTags.CANDLE_CAKES)
                    .add(BrewingBlocks.BARREL.get());
            var tables = tag(BannerPermission.TABLES.tag());
            tables.addOptionalTag(ModTags.Blocks.CRAFTING_TABLES.location());
            for (Block block : List.of(
                    Blocks.CRAFTING_TABLE,
                    Blocks.ANVIL,
                    Blocks.CHIPPED_ANVIL,
                    Blocks.DAMAGED_ANVIL,
                    Blocks.SMITHING_TABLE,
                    Blocks.GRINDSTONE,
                    Blocks.STONECUTTER,
                    Blocks.LOOM,
                    Blocks.CARTOGRAPHY_TABLE,
                    Blocks.FLETCHING_TABLE,
                    Blocks.ENCHANTING_TABLE)) {
                tables.add(block);
            }
            var containers = tag(BannerPermission.CONTAINERS.tag());
            containers.addTag(BlockTags.SHULKER_BOXES);
            for (Block block : List.of(
                    Blocks.CHEST,
                    Blocks.TRAPPED_CHEST,
                    Blocks.BARREL,
                    Blocks.FURNACE,
                    Blocks.BLAST_FURNACE,
                    Blocks.SMOKER,
                    Blocks.HOPPER,
                    Blocks.DROPPER,
                    Blocks.DISPENSER,
                    Blocks.BREWING_STAND,
                    Blocks.LECTERN,
                    Blocks.JUKEBOX,
                    Blocks.CHISELED_BOOKSHELF,
                    Blocks.DECORATED_POT)) {
                containers.add(block);
            }
        }

        @Override
        public String getName() {
            return "Banner block tags";
        }
    }

    // ------------------------------------------------------------------ block states and models

    static final class BlockStates extends BlockStateProvider {
        BlockStates(PackOutput output, ExistingFileHelper files) {
            super(output, LOTRRevival.MOD_ID, files);
        }

        @Override
        protected void registerStatesAndModels() {
            // The cloth and pole are drawn by the block entity renderer; the model only carries the particle texture.
            ModelFile model = models().getBuilder("faction_banner")
                    .parent(new ModelFile.UncheckedModelFile("builtin/entity"))
                    .texture("particle", mcLoc("block/dark_oak_planks"));
            simpleBlock(BannerBlocks.STANDING.get(), model);
            horizontalBlock(BannerBlocks.WALL.get(), model);
        }

        @Override
        public String getName() {
            return "Banner block states";
        }
    }

    static final class ItemModels extends ItemModelProvider {
        ItemModels(PackOutput output, ExistingFileHelper files) {
            super(output, LOTRRevival.MOD_ID, files);
        }

        @Override
        protected void registerModels() {
            withExistingParent("faction_banner", mcLoc("item/generated")).texture("layer0", modLoc("item/faction_banner"));
        }

        @Override
        public String getName() {
            return "Banner item models";
        }
    }

    // ------------------------------------------------------------------ loot

    /** The banner drops itself with its design and, from its owner, its protection settings. */
    static final class Loot implements DataProvider {
        private final PackOutput output;
        private final CompletableFuture<HolderLookup.Provider> registries;

        Loot(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            this.output = output;
            this.registries = registries;
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return registries.thenCompose(provider -> {
                PackOutput.PathProvider paths = output.createRegistryElementsPathProvider(Registries.LOOT_TABLE);
                List<CompletableFuture<?>> writes = new ArrayList<>();
                new BannerLoot().generate((key, builder) -> {
                    LootTable table = builder.setParamSet(LootContextParamSets.BLOCK).build();
                    writes.add(DataProvider.saveStable(
                            cache, provider, LootTable.DIRECT_CODEC, table, paths.json(key.location())));
                });
                return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
            });
        }

        @Override
        public String getName() {
            return "Banner loot tables";
        }
    }

    private static final class BannerLoot implements LootTableSubProvider {
        @Override
        public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output) {
            Block banner = BannerBlocks.STANDING.get();
            output.accept(
                    banner.getLootTable(),
                    LootTable.lootTable()
                            .withPool(LootPool.lootPool()
                                    .setRolls(ConstantValue.exactly(1))
                                    .add(LootItem.lootTableItem(BannerBlocks.ITEM.get())
                                            .apply(CopyComponentsFunction.copyComponents(
                                                            CopyComponentsFunction.Source.BLOCK_ENTITY)
                                                    .include(BannerComponents.DESIGN.get())
                                                    .include(BannerComponents.PROTECTION.get())))));
        }
    }

    // ------------------------------------------------------------------ lang

    private static void registerLang() {
        ModLangProvider.contribute(lang -> {
            lang.add("block.lotrrevival.faction_banner", "Faction Banner");
            lang.add("block.lotrrevival.faction_wall_banner", "Faction Wall Banner");
            lang.add("item.lotrrevival.faction_banner", "Faction Banner");
            lang.add("item.lotrrevival.faction_banner.named", "%s Banner");
            lang.add("banner.lotrrevival.protected", "This land is protected by %s.");
            lang.add("banner.lotrrevival.stronghold", "a stronghold");
            lang.add("banner.lotrrevival.already_protected", "This land is already protected.");
            lang.add("banner.lotrrevival.need_alignment", "You need at least +1 alignment with %s to raise this banner.");
            lang.add("banner.lotrrevival.need_space", "A banner needs two free blocks above it.");
            String g = "gui.lotrrevival.banner.";
            lang.add("banner.lotrrevival.edit.saved", "Banner settings saved.");
            lang.add("banner.lotrrevival.edit.rejected", "Those banner settings were not accepted.");
            lang.add(g + "title", "Banner of %s");
            lang.add(g + "mode", "Mode: %s");
            lang.add(g + "mode.faction", "Faction");
            lang.add(g + "mode.whitelist", "Whitelist");
            lang.add(g + "mode.tooltip", "Faction: anyone with enough alignment may act. Whitelist: only listed players and fellowships.");
            lang.add(g + "threshold", "Alignment needed");
            lang.add(g + "range", "Range (op)");
            lang.add(g + "self_protection", "Self-protection: %s");
            lang.add(g + "on", "On");
            lang.add(g + "off", "Off");
            lang.add(g + "defaults", "Everyone else may:");
            lang.add(g + "whitelist", "Whitelist (%1$s of %2$s slots)");
            lang.add(g + "owner", "Owner");
            lang.add(g + "fellowship_tag", "Fellowship");
            lang.add(g + "remove", "Remove this entry");
            lang.add(g + "add_player", "Add player");
            lang.add(g + "player_name", "Player name");
            lang.add(g + "add_fellowship", "Add");
            lang.add(g + "no_fellowship", "No fellowship");
            lang.add(g + "page", "Page %1$s of %2$s");
            lang.add(g + "done", "Save");
            lang.add(g + "cancel", "Cancel");
            lang.add(g + "locked", "The owner always has full access.");
            lang.add(g + "full_list", "The whitelist is full.");
            lang.add(g + "permission.full", "Full access");
            lang.add(g + "permission.doors", "Doors and gates");
            lang.add(g + "permission.tables", "Crafting tables");
            lang.add(g + "permission.containers", "Containers");
            lang.add(g + "permission.personal_containers", "Personal containers");
            lang.add(g + "permission.food", "Food and drink");
            lang.add(g + "permission.beds", "Beds");
            lang.add(g + "permission.switches", "Levers and buttons");
            lang.add("commands.lotrrevival.banner.none", "There is no faction banner at that position.");
            lang.add("commands.lotrrevival.banner.range.set", "Set the banner's protection range to %s.");
            lang.add("commands.lotrrevival.banner.range.reset", "Reset the banner's range; it now protects %s blocks.");
            lang.add("commands.lotrrevival.banner.whitelistsize", "The banner's whitelist now has %s slots.");
            lang.add(
                    "commands.lotrrevival.banner.info",
                    "Banner %s: range %s (custom %s), owner %s, mode %s, threshold %s, whitelist slots used %s of %s, "
                            + "self-protection %s, structure %s.");
        });
    }

    /** The design ids this datagen writes, for tests. */
    public static List<ResourceLocation> designIds() {
        return BannerDesign.defaultIds();
    }
}
