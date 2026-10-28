package io.github.sethwillemsvg.lotrrevival.npc.hire;

import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Datagen for the hiring system: unit lists and lang. */
public final class HireDataGen {
    private HireDataGen() {}

    /** Called once from {@code NpcDataGen.gather}. */
    public static void gather(GatherDataEvent event) {
        event.getGenerator()
                .addProvider(event.includeServer(), new UnitListProvider(event.getGenerator().getPackOutput()));
        ModLangProvider.contribute(lang -> {
            lang.add("npc.lotrrevival.unit.deserted", "%s has deserted you.");
            lang.add("npc.lotrrevival.unit.dismissed", "%s has left your service.");
            io.github.sethwillemsvg.lotrrevival.npc.hire.purchase.HirePurchaseLang.addTo(lang);
        });
    }

    /** {@code npc_units/<trader type>.json}: the units each unit trader hires out. */
    static final class UnitListProvider implements DataProvider {
        private final PackOutput.PathProvider paths;

        UnitListProvider(PackOutput output) {
            this.paths = output.createPathProvider(PackOutput.Target.DATA_PACK, NpcUnitData.DIRECTORY);
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            Map<String, Map<NpcRole, UnitList>> tables = Map.of("hobbit_shirriff", shirriff());
            return CompletableFuture.allOf(tables.entrySet().stream()
                    .map(entry -> DataProvider.saveStable(
                            cache,
                            NpcUnitData.TABLE_CODEC
                                    .encodeStart(JsonOps.INSTANCE, entry.getValue())
                                    .getOrThrow(),
                            paths.json(LOTRRevival.id(entry.getKey()))))
                    .toArray(CompletableFuture[]::new));
        }

        /** Shirriff: base +50, Bounders for 20 coins. Mounted and farmhand entries are not shipped yet. */
        private static Map<NpcRole, UnitList> shirriff() {
            return Map.of(
                    NpcRole.UNIT_TRADER,
                    new UnitList(
                            50.0f,
                            List.of(UnitEntry.warrior("bounder", LOTRRevival.id("hobbit_bounder"), 20, 0.0f))));
        }

        @Override
        public String getName() {
            return "NPC unit lists";
        }
    }
}
