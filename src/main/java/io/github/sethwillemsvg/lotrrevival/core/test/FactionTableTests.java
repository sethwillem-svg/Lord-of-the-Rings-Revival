package io.github.sethwillemsvg.lotrrevival.core.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.event.FactionTableCraftEvent;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionShapedRecipe;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTableBlock;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTableMenu;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTableRecipes;
import io.github.sethwillemsvg.lotrrevival.core.table.FactionTables;
import io.github.sethwillemsvg.lotrrevival.core.table.TableRef;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Faction crafting table GameTests: the alignment gate, recipe scoping and the craft event. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FactionTableTests {
    private FactionTableTests() {}

    /** A mock player with a configurable creative flag (the helper's mock is always creative). */
    private static ServerPlayer mockPlayer(GameTestHelper helper, boolean creative) {
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "faction-table-mock"), false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return creative;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 0, 1.5));
        player.setPos(pos.x, pos.y, pos.z);
        return player;
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /** Opens menus without the network packet (mock connections have not negotiated NeoForge channels). */
    private static FactionTableBlock.MenuOpener installOpener() {
        return FactionTableBlock.setOpener((player, provider, pos) ->
                player.containerMenu = provider.createMenu(1, player.getInventory(), player));
    }

    private static void use(GameTestHelper helper, ServerPlayer player, FactionTables.Entry table) {
        BlockPos rel = new BlockPos(2, 1, 2);
        helper.setBlock(rel, table.block().get());
        BlockPos abs = helper.absolutePos(rel);
        BlockState state = helper.getLevel().getBlockState(abs);
        state.useWithoutItem(
                helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    private static RecipeHolder<CraftingRecipe> testRecipe(String id, TableRef table) {
        ShapedRecipePattern pattern = ShapedRecipePattern.of(Map.of('A', Ingredient.of(Items.DIAMOND)), "A");
        FactionShapedRecipe recipe = new FactionShapedRecipe(
                table, "", CraftingBookCategory.MISC, pattern, new ItemStack(Items.STICK), true);
        return new RecipeHolder<>(LOTRRevival.id("test/" + id), recipe);
    }

    private static CraftingInput diamond() {
        return CraftingInput.of(1, 1, List.of(new ItemStack(Items.DIAMOND)));
    }

    @GameTest(template = "empty")
    public static void table_gate_refuses_below_one_and_opens_at_one(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper, false);
        FactionTableBlock.MenuOpener previous = installOpener();
        try {
            Alignment.set(player, Factions.GONDOR, 0.5f, AlignmentSource.command());
            use(helper, player, FactionTables.GONDOR);
            helper.assertTrue(
                    !(player.containerMenu instanceof FactionTableMenu), "Gondor +0.5 must be refused (menu not opened)");

            Alignment.set(player, Factions.GONDOR, 1.0f, AlignmentSource.command());
            use(helper, player, FactionTables.GONDOR);
            helper.assertTrue(player.containerMenu instanceof FactionTableMenu, "Gondor +1.0 must open the table");

            // Losing the alignment while open closes the menu within 20 ticks.
            Alignment.set(player, Factions.GONDOR, 0.0f, AlignmentSource.command());
            for (int i = 0; i < 20; i++) {
                player.containerMenu.broadcastChanges();
            }
            helper.assertTrue(
                    !(player.containerMenu instanceof FactionTableMenu), "menu must close when the alignment is lost");

            // Another faction's alignment does not help.
            Alignment.set(player, Factions.MORDOR, 50.0f, AlignmentSource.command());
            use(helper, player, FactionTables.GONDOR);
            helper.assertTrue(
                    !(player.containerMenu instanceof FactionTableMenu), "Mordor alignment must not open the Gondor table");
        } finally {
            FactionTableBlock.setOpener(previous);
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void table_gate_creative_bypasses(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper, true);
        FactionTableBlock.MenuOpener previous = installOpener();
        try {
            use(helper, player, FactionTables.MORDOR);
            helper.assertTrue(
                    player.containerMenu instanceof FactionTableMenu, "a creative player must open any faction table");
        } finally {
            FactionTableBlock.setOpener(previous);
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void faction_recipes_are_scoped_to_their_table(GameTestHelper helper) {
        Block mordor = FactionTables.MORDOR.block().get();
        Block gondor = FactionTables.GONDOR.block().get();
        List<RecipeHolder<CraftingRecipe>> mordorOnly = List.of(testRecipe("mordor", TableRef.block(mordor)));
        helper.assertTrue(
                FactionTableRecipes.match(mordorOnly, diamond(), helper.getLevel(), mordor).isPresent(),
                "a Mordor recipe must match on the Mordor table");
        helper.assertTrue(
                FactionTableRecipes.match(mordorOnly, diamond(), helper.getLevel(), gondor).isEmpty(),
                "a Mordor recipe must not match on the Gondor table");

        List<RecipeHolder<CraftingRecipe>> group =
                List.of(testRecipe("group", TableRef.tag(LOTRRevival.id("crafting_tables"))));
        for (FactionTables.Entry entry : FactionTables.ALL) {
            helper.assertTrue(
                    FactionTableRecipes.match(group, diamond(), helper.getLevel(), entry.block().get()).isPresent(),
                    "a group-tag recipe must match on " + entry.name());
        }
        helper.assertTrue(
                FactionTableRecipes.match(group, diamond(), helper.getLevel(), net.minecraft.world.level.block.Blocks.CRAFTING_TABLE)
                        .isEmpty(),
                "a group-tag recipe must not match on a table outside the tag");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void faction_recipe_codec_round_trips(GameTestHelper helper) {
        RecipeHolder<CraftingRecipe> holder = testRecipe("codec", TableRef.tag(LOTRRevival.id("crafting_tables")));
        var codec = FactionTableRecipes.SHAPED_SERIALIZER.get().codec().codec();
        var json = codec.encodeStart(JsonOps.INSTANCE, (FactionShapedRecipe) holder.value()).getOrThrow();
        FactionShapedRecipe back = codec.parse(JsonOps.INSTANCE, json).getOrThrow();
        helper.assertTrue(back.table().equals(TableRef.tag(LOTRRevival.id("crafting_tables"))), "table must survive the codec");
        helper.assertTrue(back.getResultItem(null).is(Items.STICK), "result must survive the codec");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gondor_table_crafts_vanilla_recipes_and_fires_event_per_take(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper, false);
        Listener listener = new Listener();
        NeoForge.EVENT_BUS.register(listener);
        try {
            Alignment.set(player, Factions.GONDOR, 1.0f, AlignmentSource.command());
            FactionTableMenu menu = new FactionTableMenu(
                    1,
                    player.getInventory(),
                    ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2))),
                    FactionTables.GONDOR.block().get());
            menu.getSlot(1).set(new ItemStack(Items.OAK_PLANKS));
            menu.getSlot(4).set(new ItemStack(Items.OAK_PLANKS));
            ItemStack preview = menu.getSlot(FactionTableMenu.RESULT_SLOT).getItem();
            helper.assertTrue(preview.is(Items.STICK) && preview.getCount() == 4, "planks -> 4 sticks on the Gondor table");

            menu.clicked(FactionTableMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
            helper.assertTrue(listener.count == 1, "one take must fire the event once, fired " + listener.count);
            helper.assertTrue(listener.faction.equals(Factions.GONDOR), "event carries the table's faction");
            helper.assertTrue(listener.result.is(Items.STICK), "event carries the taken result");
            helper.assertTrue(listener.player == player, "event carries the player");
            helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "inputs are consumed");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            remove(helper, player);
        }
        helper.succeed();
    }

    /** Collects table craft events. */
    public static final class Listener {
        int count;
        ServerPlayer player;
        ResourceKey<Faction> faction;
        ItemStack result = ItemStack.EMPTY;
        final List<ItemStack> all = new ArrayList<>();

        @SubscribeEvent
        public void onCraft(FactionTableCraftEvent event) {
            count++;
            player = event.getPlayer();
            faction = event.getFaction();
            result = event.getResult();
            all.add(result);
        }
    }
}
