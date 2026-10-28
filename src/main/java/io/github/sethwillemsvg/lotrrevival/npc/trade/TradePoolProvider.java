package io.github.sethwillemsvg.lotrrevival.npc.trade;

import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingItems;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/**
 * Writes {@code npc_trades/<type>.json} with the G2 pools (vanilla placeholders, swapped for core items as they land).
 * Prices are base prices before the +-25% roll.
 */
public final class TradePoolProvider implements DataProvider {
    private final PackOutput.PathProvider paths;

    public TradePoolProvider(PackOutput output) {
        this.paths = output.createPathProvider(PackOutput.Target.DATA_PACK, NpcTradeData.DIRECTORY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<ResourceLocation, Map<NpcRole, TradePool>> tables = Map.of(
                LOTRRevival.id("hobbit"), table(hobbitInnkeeper(), hobbitFarmer()),
                LOTRRevival.id("bree_hobbit"), table(breeInnkeeper(), breeFarmer()),
                LOTRRevival.id("bree_man"), table(breeInnkeeper(), breeFarmer()));
        return CompletableFuture.allOf(tables.entrySet().stream()
                .map(entry -> DataProvider.saveStable(
                        cache,
                        TradePool.TABLE_CODEC.encodeStart(JsonOps.INSTANCE, entry.getValue()).getOrThrow(),
                        paths.json(entry.getKey())))
                .toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "NPC trade pools";
    }

    private static Map<NpcRole, TradePool> table(TradePool innkeeper, TradePool farmer) {
        Map<NpcRole, TradePool> roles = new EnumMap<>(NpcRole.class);
        roles.put(NpcRole.INNKEEPER, innkeeper);
        roles.put(NpcRole.FARMER, farmer);
        return roles;
    }

    private static TradePool hobbitInnkeeper() {
        return new TradePool(
                List.of(
                        TradeEntry.item(Items.BREAD, 5),
                        TradeEntry.item(Items.BAKED_POTATO, 5),
                        TradeEntry.item(Items.COOKED_CHICKEN, 5),
                        TradeEntry.item(Items.COOKED_PORKCHOP, 6),
                        TradeEntry.item(Items.COOKED_MUTTON, 6),
                        TradeEntry.item(Items.COOKIE, 4, 5),
                        TradeEntry.item(Items.MUSHROOM_STEW, 10),
                        TradeEntry.item(Items.RABBIT_STEW, 10),
                        TradeEntry.item(Items.PUMPKIN_PIE, 10),
                        TradeEntry.item(Items.CAKE, 14),
                        TradeEntry.item(BrewingItems.WOODEN_MUG.get(), 3)),
                List.of(
                        TradeEntry.item(Items.WHEAT, 1),
                        TradeEntry.item(Items.POTATO, 1),
                        TradeEntry.item(Items.CARROT, 1),
                        TradeEntry.item(Items.BEETROOT, 1),
                        TradeEntry.item(Items.EGG, 1),
                        TradeEntry.item(Items.SWEET_BERRIES, 1),
                        TradeEntry.item(Items.APPLE, 2),
                        TradeEntry.item(Items.SUGAR, 2),
                        TradeEntry.item(Items.HONEY_BOTTLE, 3)));
    }

    private static TradePool hobbitFarmer() {
        return new TradePool(
                List.of(
                        TradeEntry.item(Items.WHEAT, 2),
                        TradeEntry.item(Items.POTATO, 2),
                        TradeEntry.item(Items.CARROT, 2),
                        TradeEntry.item(Items.BEETROOT, 2),
                        TradeEntry.item(Items.EGG, 2),
                        TradeEntry.item(Items.APPLE, 3),
                        TradeEntry.item(Items.PUMPKIN, 3),
                        TradeEntry.item(Items.MELON_SLICE, 4, 2),
                        TradeEntry.item(Items.BREAD, 5),
                        TradeEntry.item(Items.HAY_BLOCK, 15),
                        TradeEntry.item(Items.WHEAT_SEEDS, 4, 1)),
                List.of(
                        TradeEntry.item(Items.BONE_MEAL, 1),
                        TradeEntry.item(Items.BONE, 1),
                        TradeEntry.item(Items.FEATHER, 1),
                        TradeEntry.item(Items.STRING, 1),
                        TradeEntry.item(Items.LEATHER, 2),
                        TradeEntry.item(Items.ROTTEN_FLESH, 4, 1)));
    }

    private static TradePool breeInnkeeper() {
        return new TradePool(
                List.of(
                        TradeEntry.item(Items.BREAD, 5),
                        TradeEntry.item(Items.BAKED_POTATO, 5),
                        TradeEntry.item(Items.COOKED_BEEF, 7),
                        TradeEntry.item(Items.COOKED_PORKCHOP, 6),
                        TradeEntry.item(Items.MUSHROOM_STEW, 10),
                        TradeEntry.item(Items.RABBIT_STEW, 10),
                        TradeEntry.item(Items.PUMPKIN_PIE, 10),
                        TradeEntry.item(Items.CAKE, 14),
                        TradeEntry.item(BrewingItems.WOODEN_MUG.get(), 3)),
                List.of(
                        TradeEntry.item(Items.WHEAT, 1),
                        TradeEntry.item(Items.POTATO, 1),
                        TradeEntry.item(Items.CARROT, 1),
                        TradeEntry.item(Items.EGG, 1),
                        TradeEntry.item(Items.APPLE, 2),
                        TradeEntry.item(Items.SUGAR, 2),
                        TradeEntry.item(Items.HONEY_BOTTLE, 3),
                        TradeEntry.item(Items.COD, 2),
                        TradeEntry.item(Items.SALMON, 2)));
    }

    private static TradePool breeFarmer() {
        return new TradePool(
                List.of(
                        TradeEntry.item(Items.WHEAT, 2),
                        TradeEntry.item(Items.POTATO, 2),
                        TradeEntry.item(Items.CARROT, 2),
                        TradeEntry.item(Items.BEETROOT, 2),
                        TradeEntry.item(Items.EGG, 2),
                        TradeEntry.item(Items.APPLE, 3),
                        TradeEntry.item(Items.PUMPKIN, 3),
                        TradeEntry.item(Items.BREAD, 5),
                        TradeEntry.item(Items.HAY_BLOCK, 15),
                        TradeEntry.item(Items.WHEAT_SEEDS, 4, 1)),
                List.of(
                        TradeEntry.item(Items.BONE_MEAL, 1),
                        TradeEntry.item(Items.BONE, 1),
                        TradeEntry.item(Items.FEATHER, 1),
                        TradeEntry.item(Items.STRING, 1),
                        TradeEntry.item(Items.LEATHER, 2),
                        TradeEntry.item(Items.BEEF, 2),
                        TradeEntry.item(Items.PORKCHOP, 2)));
    }
}
