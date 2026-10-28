package io.github.sethwillemsvg.lotrrevival.npc.datagen;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.tag.ModTags;
import io.github.sethwillemsvg.lotrrevival.npc.data.NpcRoleData;
import io.github.sethwillemsvg.lotrrevival.npc.data.NpcRoleStats;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntityTypes;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.name.NpcNames;
import io.github.sethwillemsvg.lotrrevival.npc.item.NpcItems;
import com.google.gson.JsonArray;
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
import net.minecraft.world.entity.EntityType;
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
            Map<ResourceLocation, Map<NpcRole, NpcRoleStats>> tables = Map.of(LOTRRevival.id("hobbit"), hobbit());
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
            roles.put(NpcRole.FARMER, NpcRoleStats.of(1.0f));
            roles.put(NpcRole.TRAVELLER, NpcRoleStats.of(1.0f));
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
            EntityType<?> hobbit = NpcEntityTypes.HOBBIT.get();
            tag(ModTags.EntityTypes.HOBBITS).add(hobbit);
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
            withExistingParent(NpcItems.HOBBIT_SPAWN_EGG.getId().getPath(), mcLoc("item/template_spawn_egg"));
        }

        @Override
        public String getName() {
            return "NPC Item Models";
        }
    }
}
