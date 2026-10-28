package io.github.sethwillemsvg.lotrrevival.npc.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.item.CoreItems;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.npc.data.NpcRoleData;
import io.github.sethwillemsvg.lotrrevival.npc.data.NpcRoleStats;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.name.NpcNames;
import io.github.sethwillemsvg.lotrrevival.npc.item.NpcItems;
import com.google.gson.JsonArray;
import io.github.sethwillemsvg.lotrrevival.npc.data.NpcLoadoutData;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Every npc data provider. After this ticket npc tickets add providers here, not in {@code DataGenerators}. */
public final class NpcDataGen {
    private NpcDataGen() {}

    public static void gather(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        ExistingFileHelper files = event.getExistingFileHelper();

        generator.addProvider(event.includeServer(), new RoleProvider(output));
        generator.addProvider(event.includeServer(), new NameBankProvider(output));
        generator.addProvider(event.includeServer(), new LoadoutProvider(output));
        generator.addProvider(event.includeServer(), new NpcSpawnTableProvider(output));
        io.github.sethwillemsvg.lotrrevival.npc.hire.HireDataGen.gather(event);
        io.github.sethwillemsvg.lotrrevival.npc.hire.progress.UnitProgressDataGen.gather(event);
        io.github.sethwillemsvg.lotrrevival.npc.hire.command.UnitCommandLang.gather(event);
        io.github.sethwillemsvg.lotrrevival.npc.hire.horn.HornDataGen.gather(event);
        generator.addProvider(event.includeServer(), new io.github.sethwillemsvg.lotrrevival.npc.trade.TradePoolProvider(output));
        generator.addProvider(event.includeServer(), new NpcLoot(output, lookup));
        generator.addProvider(event.includeServer(), new EntityTags(output, lookup, files));
        generator.addProvider(event.includeClient(), new ItemModels(output, files));
    }

    /** {@code npc_roles/<type>.json}: trade and hire minimums and kill values per role. */
    static final class RoleProvider implements DataProvider {
        private final PackOutput.PathProvider paths;

        RoleProvider(PackOutput output) {
            this.paths = output.createPathProvider(PackOutput.Target.DATA_PACK, NpcRoleData.DIRECTORY);
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            Map<ResourceLocation, Map<NpcRole, NpcRoleStats>> tables = Map.of(
                    LOTRRevival.id("hobbit"), hobbit(),
                    LOTRRevival.id("hobbit_bounder"), guard(2.0f),
                    LOTRRevival.id("hobbit_shirriff"), shirriff(),
                    LOTRRevival.id("bree_hobbit"), breeCivilian(false),
                    LOTRRevival.id("bree_man"), breeCivilian(true),
                    LOTRRevival.id("bree_guard"), guard(2.0f));
            return CompletableFuture.allOf(tables.entrySet().stream()
                    .map(entry -> DataProvider.saveStable(
                            cache,
                            NpcRoleStats.TABLE_CODEC
                                    .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, entry.getValue())
                                    .getOrThrow(),
                            paths.json(entry.getKey())))
                    .toArray(CompletableFuture[]::new));
        }

        private static Map<NpcRole, NpcRoleStats> hobbit() {
            Map<NpcRole, NpcRoleStats> roles = new EnumMap<>(NpcRole.class);
            roles.put(NpcRole.VILLAGER, NpcRoleStats.of(1.0f));
            roles.put(NpcRole.FARMER, NpcRoleStats.trader(0.0f, 2.0f));
            roles.put(NpcRole.TRAVELLER, NpcRoleStats.of(1.0f));
            roles.put(NpcRole.INNKEEPER, NpcRoleStats.trader(0.0f, 2.0f));
            return roles;
        }

        /** Guards have no role; the villager key only carries the kill value. */
        private static Map<NpcRole, NpcRoleStats> guard(float killValue) {
            Map<NpcRole, NpcRoleStats> roles = new EnumMap<>(NpcRole.class);
            roles.put(NpcRole.VILLAGER, NpcRoleStats.of(killValue));
            return roles;
        }

        private static Map<NpcRole, NpcRoleStats> shirriff() {
            Map<NpcRole, NpcRoleStats> roles = new EnumMap<>(NpcRole.class);
            roles.put(NpcRole.UNIT_TRADER, NpcRoleStats.unitTrader(50.0f, 50.0f, 5.0f));
            return roles;
        }

        private static Map<NpcRole, NpcRoleStats> breeCivilian(boolean traveller) {
            Map<NpcRole, NpcRoleStats> roles = new EnumMap<>(NpcRole.class);
            roles.put(NpcRole.VILLAGER, NpcRoleStats.of(1.0f));
            roles.put(NpcRole.FARMER, NpcRoleStats.trader(0.0f, 2.0f));
            if (traveller) {
                roles.put(NpcRole.TRAVELLER, NpcRoleStats.of(1.0f));
            }
            roles.put(NpcRole.INNKEEPER, NpcRoleStats.trader(0.0f, 2.0f));
            return roles;
        }

        @Override
        public String getName() {
            return "NPC roles";
        }
    }

    /** {@code npc_names/<bank>.json}: JSON lists of first and family names. */
    static final class NameBankProvider implements DataProvider {
        private final PackOutput.PathProvider paths;

        NameBankProvider(PackOutput output) {
            this.paths = output.createPathProvider(PackOutput.Target.DATA_PACK, NpcNames.DIRECTORY);
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return CompletableFuture.allOf(NpcNameBanks.all().entrySet().stream()
                    .map(entry -> {
                        JsonArray array = new JsonArray();
                        entry.getValue().forEach(array::add);
                        return DataProvider.saveStable(cache, array, paths.json(LOTRRevival.id(entry.getKey())));
                    })
                    .toArray(CompletableFuture[]::new));
        }

        @Override
        public String getName() {
            return "NPC name banks";
        }
    }

    /** Puts npc entity types into the shared race tags owned by core. */
    static final class EntityTags extends EntityTypeTagsProvider {
        EntityTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
            super(output, lookup, LOTRRevival.MOD_ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            tag(ModTags.EntityTypes.HOBBITS)
                    .add(NpcEntityTypes.HOBBIT.get())
                    .add(NpcEntityTypes.HOBBIT_BOUNDER.get())
                    .add(NpcEntityTypes.HOBBIT_SHIRRIFF.get())
                    .add(NpcEntityTypes.BREE_HOBBIT.get());
            tag(ModTags.EntityTypes.NPCS).add(NpcEntityTypes.HOBBIT.get());
            for (var type : NpcEntityTypes.standardTypes()) {
                tag(ModTags.EntityTypes.NPCS).add(type.get());
            }
            tag(ModTags.EntityTypes.MEN)
                    .add(NpcEntityTypes.BREE_MAN.get())
                    .add(NpcEntityTypes.BREE_GUARD.get());
        }

        @Override
        public String getName() {
            return "NPC Entity Type Tags";
        }
    }

    static final class ItemModels extends ItemModelProvider {
        ItemModels(PackOutput output, ExistingFileHelper files) {
            super(output, LOTRRevival.MOD_ID, files);
        }

        @Override
        protected void registerModels() {
            for (var egg : NpcItems.spawnEggs()) {
                withExistingParent(egg.getId().getPath(), mcLoc("item/template_spawn_egg"));
            }
        }

        @Override
        public String getName() {
            return "NPC Item Models";
        }
    }

    /** {@code npc_loadouts/<type>.json}: weighted equipment per slot, per role. Placeholders until core items exist. */
    static final class LoadoutProvider implements DataProvider {
        private static final int BOUNDER_CAP = 0x5B7F3A;
        private static final int SHIRRIFF_CAP = 0x8A2F2F;
        private static final int BREE_GUARD_DYE = 0x3E5C8A;

        private final PackOutput.PathProvider paths;

        LoadoutProvider(PackOutput output) {
            this.paths = output.createPathProvider(PackOutput.Target.DATA_PACK, NpcLoadoutData.DIRECTORY);
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            Map<ResourceLocation, Map<String, Map<EquipmentSlot, List<NpcLoadoutData.Choice>>>> tables = Map.of(
                    LOTRRevival.id("hobbit"), civilian(),
                    LOTRRevival.id("bree_hobbit"), civilian(),
                    LOTRRevival.id("bree_man"), civilian(),
                    LOTRRevival.id("hobbit_bounder"), dagger(BOUNDER_CAP),
                    LOTRRevival.id("hobbit_shirriff"), dagger(SHIRRIFF_CAP),
                    LOTRRevival.id("bree_guard"), breeGuard());
            return CompletableFuture.allOf(tables.entrySet().stream()
                    .map(entry -> DataProvider.saveStable(
                            cache,
                            NpcLoadoutData.TABLE_CODEC
                                    .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, entry.getValue())
                                    .getOrThrow(),
                            paths.json(entry.getKey())))
                    .toArray(CompletableFuture[]::new));
        }

        private static Map<String, Map<EquipmentSlot, List<NpcLoadoutData.Choice>>> civilian() {
            return Map.of(
                    NpcRole.FARMER.getSerializedName(),
                    Map.of(EquipmentSlot.MAINHAND, List.of(NpcLoadoutData.Choice.of(Items.WOODEN_HOE, 1))),
                    NpcRole.TRAVELLER.getSerializedName(),
                    Map.of(EquipmentSlot.FEET, List.of(NpcLoadoutData.Choice.of(Items.LEATHER_BOOTS, 1))));
        }

        /** Placeholder iron sword for the dagger, plus a dyed leather cap. */
        private static Map<String, Map<EquipmentSlot, List<NpcLoadoutData.Choice>>> dagger(int capDye) {
            return Map.of(
                    NpcLoadoutData.DEFAULT_KEY,
                    Map.of(
                            EquipmentSlot.MAINHAND, List.of(NpcLoadoutData.Choice.of(Items.IRON_SWORD, 1)),
                            EquipmentSlot.HEAD, List.of(NpcLoadoutData.Choice.dyed(Items.LEATHER_HELMET, 1, capDye))));
        }

        /** 2 in 3 sword, 1 in 3 pike; the pike entry resolves to the sword until core registers pikes. */
        private static Map<String, Map<EquipmentSlot, List<NpcLoadoutData.Choice>>> breeGuard() {
            return Map.of(
                    NpcLoadoutData.DEFAULT_KEY,
                    Map.of(
                            EquipmentSlot.MAINHAND,
                            List.of(
                                    NpcLoadoutData.Choice.of(Items.IRON_SWORD, 2),
                                    NpcLoadoutData.Choice.of(Items.IRON_SWORD, 1).labelled("pike placeholder")),
                            EquipmentSlot.CHEST,
                            List.of(NpcLoadoutData.Choice.dyed(Items.LEATHER_CHESTPLATE, 1, BREE_GUARD_DYE)),
                            EquipmentSlot.HEAD,
                            List.of(NpcLoadoutData.Choice.dyed(Items.LEATHER_HELMET, 1, BREE_GUARD_DYE))));
        }

        @Override
        public String getName() {
            return "NPC loadouts";
        }
    }

    /**
     * Entity loot tables: bread for everyone, with looting for civilians. Silver coins wait for core. A custom provider
     * because vanilla's loot table provider has a fixed name that core's block loot provider already uses.
     */
    static final class NpcLoot implements DataProvider {
        private final PackOutput output;
        private final CompletableFuture<HolderLookup.Provider> registries;

        NpcLoot(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            this.output = output;
            this.registries = registries;
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return registries.thenCompose(provider -> {
                PackOutput.PathProvider paths = output.createRegistryElementsPathProvider(Registries.LOOT_TABLE);
                List<CompletableFuture<?>> writes = new java.util.ArrayList<>();
                new NpcLootTables(provider).generate((key, builder) -> {
                    LootTable table = builder.setParamSet(LootContextParamSets.ENTITY).build();
                    writes.add(DataProvider.saveStable(
                            cache,
                            provider,
                            LootTable.DIRECT_CODEC,
                            table,
                            paths.json(key.location())));
                });
                return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
            });
        }

        @Override
        public String getName() {
            return "NPC Loot Tables";
        }
    }

    private static final class NpcLootTables implements LootTableSubProvider {
        private final HolderLookup.Provider registries;

        NpcLootTables(HolderLookup.Provider registries) {
            this.registries = registries;
        }

        @Override
        public void generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output) {
            for (EntityType<?> civilian : List.of(
                    NpcEntityTypes.HOBBIT.get(), NpcEntityTypes.BREE_HOBBIT.get(), NpcEntityTypes.BREE_MAN.get())) {
                output.accept(civilian.getDefaultLootTable(), withCoins(bread(true)));
            }
            for (EntityType<?> fighter : List.of(
                    NpcEntityTypes.HOBBIT_BOUNDER.get(),
                    NpcEntityTypes.HOBBIT_SHIRRIFF.get(),
                    NpcEntityTypes.BREE_GUARD.get())) {
                output.accept(fighter.getDefaultLootTable(), withCoins(bread(false)));
            }
        }

        /** Adds a pool of 0-2 silver coins that only drops when a player made the kill. */
        private static LootTable.Builder withCoins(LootTable.Builder table) {
            return table.withPool(LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1.0f))
                    .when(net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition.killedByPlayer())
                    .add(LootItem.lootTableItem(CoreItems.SILVER_COIN.get())
                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0f, 2.0f)))));
        }

        /** 0-1 bread; civilians gain up to +1 per looting level. */
        private LootTable.Builder bread(boolean looting) {
            LootItem.Builder<?> entry = LootItem.lootTableItem(Items.BREAD)
                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0f, 1.0f)));
            if (looting) {
                entry.apply(EnchantedCountIncreaseFunction.lootingMultiplier(registries, ConstantValue.exactly(1.0f)));
            }
            return LootTable.lootTable().withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0f)).add(entry));
        }
    }
}
