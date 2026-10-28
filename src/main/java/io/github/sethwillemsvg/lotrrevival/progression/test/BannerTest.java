package io.github.sethwillemsvg.lotrrevival.progression.test;

import com.mojang.authlib.GameProfile;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.alignment.Alignment;
import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentSource;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingBlocks;
import io.github.sethwillemsvg.lotrrevival.core.faction.Factions;
import io.github.sethwillemsvg.lotrrevival.progression.achievement.AchievementService;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerBases;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerBlocks;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerComponents;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerDesign;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerEntry;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerIndex;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerPermission;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerPlacement;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerProtection;
import io.github.sethwillemsvg.lotrrevival.progression.banner.BannerSettings;
import io.github.sethwillemsvg.lotrrevival.progression.banner.FactionBannerBlockEntity;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Banners: ranges, the allowed rule, placement rules, enforcement events, spawns, explosions and drops. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BannerTest {
    private static final ResourceLocation GONDOR = BannerDesign.idFor(Factions.GONDOR);

    // ------------------------------------------------------------------ fixtures

    /** A survival mock player (the helper's mock is always creative). Remove it with {@link #remove}. */
    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "banner-mock"), false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    /** Puts a gold base and a standing banner at {@code at}, owned by {@code owner}, with the given settings. */
    private static FactionBannerBlockEntity raise(GameTestHelper helper, BlockPos at, BannerSettings settings) {
        ServerLevel level = helper.getLevel();
        level.setBlock(at.below(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        level.setBlock(at, BannerBlocks.STANDING.get().defaultBlockState(), 3);
        FactionBannerBlockEntity banner = (FactionBannerBlockEntity) level.getBlockEntity(at);
        banner.initialise(GONDOR, settings);
        return banner;
    }

    private static void takeDown(GameTestHelper helper, BlockPos at) {
        ServerLevel level = helper.getLevel();
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(at.below(), Blocks.AIR.defaultBlockState(), 3);
        BannerIndex.get(level).remove(at);
    }

    private static BannerSettings ownedBy(ServerPlayer owner) {
        return BannerSettings.forOwner(owner);
    }

    private static boolean breakCancelled(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    private static PlayerInteractEvent.RightClickBlock rightClick(ServerLevel level, ServerPlayer player, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(
                player, InteractionHand.MAIN_HAND, pos, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    // ------------------------------------------------------------------ data

    @GameTest(template = "empty")
    public static void designsAndTags(GameTestHelper helper) {
        var designs = helper.getLevel().registryAccess().registryOrThrow(BannerDesign.REGISTRY);
        for (var faction : Factions.REAL) {
            BannerDesign design = designs.get(BannerDesign.idFor(faction));
            helper.assertTrue(design != null, "a design for " + faction.location());
            helper.assertTrue(design.faction().equals(faction), "design faction matches " + faction.location());
        }
        helper.assertTrue(BannerBases.rangeOf(Blocks.GOLD_BLOCK.defaultBlockState()) == 32, "gold is 32");
        helper.assertTrue(BannerBases.rangeOf(Blocks.IRON_BLOCK.defaultBlockState()) == 0, "iron is decorative");

        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(classify(level, pos, Blocks.ENDER_CHEST) == BannerPermission.PERSONAL_CONTAINERS, "ender chest");
        helper.assertTrue(classify(level, pos, Blocks.OAK_DOOR) == BannerPermission.DOORS, "door");
        helper.assertTrue(classify(level, pos, Blocks.OAK_TRAPDOOR) == BannerPermission.DOORS, "trapdoor");
        helper.assertTrue(classify(level, pos, Blocks.OAK_FENCE_GATE) == BannerPermission.DOORS, "fence gate");
        helper.assertTrue(classify(level, pos, Blocks.RED_BED) == BannerPermission.BEDS, "bed");
        helper.assertTrue(classify(level, pos, Blocks.LEVER) == BannerPermission.SWITCHES, "lever");
        helper.assertTrue(classify(level, pos, Blocks.STONE_BUTTON) == BannerPermission.SWITCHES, "button");
        helper.assertTrue(classify(level, pos, Blocks.CAKE) == BannerPermission.FOOD, "cake");
        helper.assertTrue(classify(level, pos, BrewingBlocks.BARREL.get()) == BannerPermission.FOOD, "brewing barrel");
        helper.assertTrue(classify(level, pos, Blocks.CRAFTING_TABLE) == BannerPermission.TABLES, "crafting table");
        helper.assertTrue(classify(level, pos, Blocks.ANVIL) == BannerPermission.TABLES, "anvil");
        helper.assertTrue(classify(level, pos, Blocks.CHEST) == BannerPermission.CONTAINERS, "chest");
        helper.assertTrue(classify(level, pos, Blocks.FURNACE) == BannerPermission.CONTAINERS, "furnace");
        helper.assertTrue(classify(level, pos, Blocks.STONE) == BannerPermission.FULL, "stone needs Full");
        helper.succeed();
    }

    private static BannerPermission classify(ServerLevel level, BlockPos pos, Block block) {
        return BannerProtection.classify(level, pos, block.defaultBlockState());
    }

    @GameTest(template = "empty")
    public static void settingsRoundTrip(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        try {
            BannerSettings settings = ownedBy(player)
                    .withMode(BannerSettings.Mode.WHITELIST)
                    .withThreshold(250)
                    .withEntries(List.of(
                            BannerSettings.Entry.player(UUID.randomUUID(), "friend"),
                            new BannerSettings.Entry(
                                    BannerSettings.Kind.FELLOWSHIP,
                                    UUID.randomUUID(),
                                    "company",
                                    BannerPermission.setOf(BannerPermission.DOORS, BannerPermission.FOOD))))
                    .withDefaults(BannerPermission.setOf(BannerPermission.SWITCHES))
                    .withSelfProtection(false)
                    .withCustomRange(12)
                    .withWhitelistSize(40);
            var encoded = BannerSettings.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, settings).getOrThrow();
            BannerSettings decoded = BannerSettings.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
            helper.assertTrue(decoded.equals(settings), "settings survive a round trip: " + decoded);

            // Out-of-range values are clamped on decode.
            BannerSettings clamped = settings.withThreshold(5_000_000).withCustomRange(1_000).withWhitelistSize(999_999);
            helper.assertTrue(clamped.threshold() == 10_000, "threshold clamped");
            helper.assertTrue(clamped.customRange() == 64, "range clamped");
            helper.assertTrue(clamped.whitelistSize() == 4_000, "slots clamped");
            helper.assertTrue(settings.withWhitelistSize(2).entries().size() == 1, "entries trimmed to the slots");
            helper.assertTrue(BannerSettings.unowned().defaults().isEmpty(), "no default permissions");
            helper.succeed();
        } finally {
            remove(helper, player);
        }
    }

    // ------------------------------------------------------------------ ranges

    @GameTest(template = "empty")
    public static void goldCubeEdge(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            FactionBannerBlockEntity banner = raise(helper, at, ownedBy(owner));
            ServerLevel level = helper.getLevel();
            helper.assertTrue(banner.effectiveRange() == 32, "gold base gives 32, got " + banner.effectiveRange());
            helper.assertTrue(BannerProtection.query(level, at.offset(32, 32, 32)).size() == 1, "corner at 32 is inside");
            helper.assertTrue(BannerProtection.query(level, at.offset(-32, -32, -32)).size() == 1, "far corner at -32 is inside");
            helper.assertTrue(BannerProtection.query(level, at.offset(33, 0, 0)).isEmpty(), "33 on x is outside");
            helper.assertTrue(BannerProtection.query(level, at.offset(0, 33, 0)).isEmpty(), "33 on y is outside");
            helper.assertTrue(BannerProtection.query(level, at.offset(0, 0, -33)).isEmpty(), "33 on z is outside");

            // An operator range overrides the base block; a wrong base block means decorative.
            banner.setSettings(banner.settings().withCustomRange(5));
            helper.assertTrue(BannerProtection.query(level, at.offset(5, 5, 5)).size() == 1, "custom range 5 inside");
            helper.assertTrue(BannerProtection.query(level, at.offset(6, 0, 0)).isEmpty(), "custom range 5 outside");
            banner.setSettings(banner.settings().withCustomRange(0));
            level.setBlock(at.below(), Blocks.DIRT.defaultBlockState(), 3);
            banner.reindex();
            helper.assertTrue(banner.effectiveRange() == 0, "dirt base is decorative");
            helper.assertTrue(BannerProtection.query(level, at).isEmpty(), "decorative banner is not in the index");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            remove(helper, owner);
        }
    }

    @GameTest(template = "empty")
    public static void baseBlockLossPopsTheBanner(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            raise(helper, at, ownedBy(owner));
            ServerLevel level = helper.getLevel();
            helper.assertTrue(BannerIndex.get(level).get(at) != null, "indexed after raising");
            level.setBlock(at.below(), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(at).isAir(), "banner pops off without a base");
            helper.assertTrue(BannerIndex.get(level).get(at) == null, "and leaves the index");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            remove(helper, owner);
        }
    }

    // ------------------------------------------------------------------ the allowed rule

    @GameTest(template = "empty")
    public static void factionModeThreshold(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer visitor = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos target = at.offset(5, 0, 5);
        try {
            raise(helper, at, ownedBy(owner).withThreshold(50));
            ServerLevel level = helper.getLevel();
            Alignment.set(visitor, Factions.GONDOR, 0.0f, AlignmentSource.command());
            helper.assertTrue(breakCancelled(level, target, visitor), "no alignment: break refused");
            Alignment.set(visitor, Factions.GONDOR, 49.0f, AlignmentSource.command());
            helper.assertTrue(breakCancelled(level, target, visitor), "49 is below the threshold of 50");
            Alignment.set(visitor, Factions.GONDOR, 50.0f, AlignmentSource.command());
            helper.assertFalse(breakCancelled(level, target, visitor), "50 reaches the threshold");
            helper.assertFalse(breakCancelled(level, target, owner), "the owner may always act");
            helper.assertTrue(BannerProtection.deniedBy(level, target, visitor, BannerPermission.FULL).isEmpty(), "allowed");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            remove(helper, owner);
            remove(helper, visitor);
        }
    }

    @GameTest(template = "empty")
    public static void whitelistWithFellowshipEntry(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer member = survivalPlayer(helper);
        ServerPlayer stranger = survivalPlayer(helper);
        ServerPlayer listed = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos target = at.offset(3, 0, 3);
        UUID fellowship = UUID.randomUUID();
        try {
            // Everyone has maximum alignment: whitelist mode must ignore it.
            for (ServerPlayer player : List.of(member, stranger, listed)) {
                Alignment.set(player, Factions.GONDOR, 5000.0f, AlignmentSource.command());
            }
            BannerSettings settings = ownedBy(owner)
                    .withMode(BannerSettings.Mode.WHITELIST)
                    .withEntries(List.of(
                            BannerSettings.Entry.fellowship(fellowship, "the company"),
                            BannerSettings.Entry.player(listed.getUUID(), "listed")));
            raise(helper, at, settings);
            ServerLevel level = helper.getLevel();

            helper.assertTrue(breakCancelled(level, target, member), "before the fellowship exists the member is refused");
            BannerProtection.setFellowships((id, player) -> id.equals(fellowship) && player.equals(member.getUUID()));
            helper.assertFalse(breakCancelled(level, target, member), "a fellowship member is admitted");
            helper.assertTrue(breakCancelled(level, target, stranger), "a stranger is refused despite alignment");
            helper.assertFalse(breakCancelled(level, target, listed), "a listed player is admitted");
            helper.succeed();
        } finally {
            BannerProtection.setFellowships(io.github.sethwillemsvg.lotrrevival.progression.ProgressionCoreHooks.MEMBERSHIP);
            takeDown(helper, at);
            for (ServerPlayer player : List.of(owner, member, stranger, listed)) {
                remove(helper, player);
            }
        }
    }

    @GameTest(template = "empty")
    public static void overlappingBannersIntersect(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer visitor = survivalPlayer(helper);
        BlockPos a = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos b = a.offset(40, 0, 0);
        try {
            // A whitelist banner with an empty list: only its owner is allowed.
            raise(helper, a, ownedBy(owner).withMode(BannerSettings.Mode.WHITELIST));
            // A faction banner: the visitor qualifies for it by alignment, and for A by nothing.
            raise(helper, b, ownedBy(visitor));
            ServerLevel level = helper.getLevel();
            Alignment.set(visitor, Factions.GONDOR, 10.0f, AlignmentSource.command());
            BlockPos both = a.offset(20, 0, 0);
            helper.assertTrue(BannerProtection.query(level, both).size() == 2, "both banners cover the middle");
            helper.assertTrue(breakCancelled(level, both, visitor), "the intersection follows the stricter banner");
            helper.assertFalse(breakCancelled(level, b.offset(10, 0, 0), visitor), "outside A the visitor's own land is open");
            helper.succeed();
        } finally {
            takeDown(helper, b);
            takeDown(helper, a);
            remove(helper, owner);
            remove(helper, visitor);
        }
    }

    // ------------------------------------------------------------------ placement

    @GameTest(template = "empty")
    public static void placementRules(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer placer = survivalPlayer(helper);
        BlockPos existing = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos near = existing.offset(40, 0, 0);
        BlockPos far = existing.offset(70, 0, 0);
        try {
            raise(helper, existing, ownedBy(owner).withMode(BannerSettings.Mode.WHITELIST));
            ServerLevel level = helper.getLevel();
            // Gold under the planned spots (the checks read the block under the banner for the range).
            for (BlockPos spot : List.of(near, far)) {
                level.setBlock(spot.below(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            }

            Alignment.set(placer, Factions.GONDOR, 0.0f, AlignmentSource.command());
            Optional<net.minecraft.network.chat.Component> noStanding = BannerPlacement.refusal(level, near, GONDOR, 32, placer);
            helper.assertTrue(noStanding.isPresent() && key(noStanding.get()).equals("banner.lotrrevival.need_alignment"),
                    "alignment 0 is refused: " + noStanding);

            Alignment.set(placer, Factions.GONDOR, 1.0f, AlignmentSource.command());
            Optional<net.minecraft.network.chat.Component> overlap = BannerPlacement.refusal(level, near, GONDOR, 32, placer);
            helper.assertTrue(overlap.isPresent() && key(overlap.get()).equals("banner.lotrrevival.already_protected"),
                    "overlapping a denied area is refused: " + overlap);
            helper.assertTrue(BannerPlacement.refusal(level, far, GONDOR, 32, placer).isEmpty(),
                    "70 blocks away does not overlap");
            helper.assertTrue(BannerPlacement.refusal(level, near, GONDOR, 32, owner).isPresent(),
                    "the owner needs alignment too");
            Alignment.set(owner, Factions.GONDOR, 1.0f, AlignmentSource.command());
            helper.assertTrue(BannerPlacement.refusal(level, near, GONDOR, 32, owner).isEmpty(),
                    "overlapping your own land is fine");
            helper.assertTrue(BannerPlacement.refusal(level, near, GONDOR, 0, placer).isEmpty(),
                    "a decorative banner has no alignment or overlap rule");

            // The two blocks above must be replaceable.
            level.setBlock(far.above(2), Blocks.STONE.defaultBlockState(), 3);
            Optional<net.minecraft.network.chat.Component> blocked = BannerPlacement.refusal(level, far, GONDOR, 32, placer);
            helper.assertTrue(blocked.isPresent() && key(blocked.get()).equals("banner.lotrrevival.need_space"),
                    "a solid block two above is refused: " + blocked);
            level.setBlock(far.above(2), Blocks.AIR.defaultBlockState(), 3);
            helper.succeed();
        } finally {
            takeDown(helper, existing);
            for (BlockPos spot : List.of(near, far)) {
                helper.getLevel().setBlock(spot.below(), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(spot.above(2), Blocks.AIR.defaultBlockState(), 3);
            }
            remove(helper, owner);
            remove(helper, placer);
        }
    }

    private static String key(net.minecraft.network.chat.Component component) {
        return component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
                ? translatable.getKey()
                : "";
    }

    @GameTest(template = "empty")
    public static void placedBannerBecomesOwnedAndEarnsTheAchievement(GameTestHelper helper) {
        ServerPlayer placer = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        AchievementService.Sink previous = AchievementService.setSink(new AchievementService.Sink() {
            @Override
            public void toast(ServerPlayer player, ResourceLocation id) {}

            @Override
            public void broadcast(ServerPlayer player, net.minecraft.network.chat.Component line) {}
        });
        try {
            ServerLevel level = helper.getLevel();
            level.setBlock(at.below(), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            BlockState state = BannerBlocks.STANDING.get().defaultBlockState();
            level.setBlock(at, state, 3);
            ItemStack stack = BannerBlocks.stackOf(GONDOR);
            ((FactionBannerBlockEntity) level.getBlockEntity(at)).applyComponentsFromItemStack(stack);
            BannerBlocks.STANDING.get().setPlacedBy(level, at, state, placer, stack);

            FactionBannerBlockEntity banner = (FactionBannerBlockEntity) level.getBlockEntity(at);
            helper.assertTrue(banner.settings().isOwner(placer.getUUID()), "the placer owns the banner");
            helper.assertTrue(banner.design().equals(GONDOR), "design kept from the item");
            BannerEntry entry = BannerIndex.get(level).get(at);
            helper.assertTrue(entry != null && entry.settings().isOwner(placer.getUUID()), "indexed with the owner");

            AdvancementHolder holder = level.getServer().getAdvancements().get(LOTRRevival.id("general/banner_protector"));
            helper.assertTrue(holder != null, "the achievement exists");
            helper.assertTrue(placer.getAdvancements().getOrStartProgress(holder).isDone(), "the first protecting banner earns it");
            helper.succeed();
        } finally {
            AchievementService.setSink(previous);
            AchievementService.forget(placer);
            takeDown(helper, at);
            remove(helper, placer);
        }
    }

    // ------------------------------------------------------------------ enforcement

    @GameTest(template = "empty")
    public static void doorsVersusContainers(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer visitor = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos door = at.offset(3, 0, 0);
        BlockPos chest = at.offset(0, 0, 3);
        try {
            raise(helper, at, ownedBy(owner)
                    .withMode(BannerSettings.Mode.WHITELIST)
                    .withDefaults(BannerPermission.setOf(BannerPermission.DOORS)));
            ServerLevel level = helper.getLevel();
            level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState(), 3);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);

            helper.assertTrue(rightClick(level, visitor, door).getUseBlock() != TriState.FALSE, "a door may be used");
            helper.assertTrue(rightClick(level, visitor, chest).getUseBlock() == TriState.FALSE, "a chest may not");
            helper.assertTrue(rightClick(level, owner, chest).getUseBlock() != TriState.FALSE, "the owner may open it");

            // With only Containers, the chest opens but the door does not.
            FactionBannerBlockEntity banner = (FactionBannerBlockEntity) level.getBlockEntity(at);
            banner.setSettings(banner.settings().withDefaults(BannerPermission.setOf(BannerPermission.CONTAINERS)));
            helper.assertTrue(rightClick(level, visitor, chest).getUseBlock() != TriState.FALSE, "containers now open");
            helper.assertTrue(rightClick(level, visitor, door).getUseBlock() == TriState.FALSE, "doors no longer");

            // Full opens everything.
            banner.setSettings(banner.settings().withDefaults(BannerPermission.setOf(BannerPermission.FULL)));
            helper.assertTrue(rightClick(level, visitor, door).getUseBlock() != TriState.FALSE, "Full opens doors");
            helper.assertFalse(breakCancelled(level, door, visitor), "and lets blocks be broken");
            helper.succeed();
        } finally {
            helper.getLevel().setBlock(door, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(chest, Blocks.AIR.defaultBlockState(), 3);
            takeDown(helper, at);
            remove(helper, owner);
            remove(helper, visitor);
        }
    }

    @GameTest(template = "empty")
    public static void sneakingWithAnItemNeedsFull(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer visitor = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos door = at.offset(3, 0, 0);
        try {
            raise(helper, at, ownedBy(owner)
                    .withMode(BannerSettings.Mode.WHITELIST)
                    .withDefaults(BannerPermission.setOf(BannerPermission.DOORS)));
            ServerLevel level = helper.getLevel();
            level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState(), 3);

            visitor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT));
            PlayerInteractEvent.RightClickBlock plain = rightClick(level, visitor, door);
            helper.assertTrue(plain.getUseBlock() != TriState.FALSE, "standing: the door still opens");
            helper.assertTrue(plain.getUseItem() == TriState.FALSE, "but the block in hand is not placed");

            visitor.setShiftKeyDown(true);
            PlayerInteractEvent.RightClickBlock sneaking = rightClick(level, visitor, door);
            helper.assertTrue(sneaking.getUseBlock() == TriState.FALSE, "sneaking with a block needs Full, not Doors");
            helper.assertTrue(sneaking.getUseItem() == TriState.FALSE, "and does not place it");

            // Sneaking with empty hands is an ordinary right-click on the door.
            visitor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(rightClick(level, visitor, door).getUseBlock() != TriState.FALSE, "empty hands: the door opens");

            visitor.setShiftKeyDown(false);
            helper.succeed();
        } finally {
            helper.getLevel().setBlock(door, Blocks.AIR.defaultBlockState(), 3);
            takeDown(helper, at);
            remove(helper, owner);
            remove(helper, visitor);
        }
    }

    @GameTest(template = "empty")
    public static void placingIsRefusedWithoutFull(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer visitor = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos target = at.offset(4, 0, 4);
        try {
            raise(helper, at, ownedBy(owner).withMode(BannerSettings.Mode.WHITELIST));
            ServerLevel level = helper.getLevel();
            net.neoforged.neoforge.common.util.BlockSnapshot snapshot =
                    net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, target);
            BlockEvent.EntityPlaceEvent denied =
                    new BlockEvent.EntityPlaceEvent(snapshot, Blocks.STONE.defaultBlockState(), visitor);
            NeoForge.EVENT_BUS.post(denied);
            helper.assertTrue(denied.isCanceled(), "placing without Full is refused");
            BlockEvent.EntityPlaceEvent allowed =
                    new BlockEvent.EntityPlaceEvent(snapshot, Blocks.STONE.defaultBlockState(), owner);
            NeoForge.EVENT_BUS.post(allowed);
            helper.assertFalse(allowed.isCanceled(), "the owner may place");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            remove(helper, owner);
            remove(helper, visitor);
        }
    }

    @GameTest(template = "empty")
    public static void selfProtectionAndDrops(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer member = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            ServerLevel level = helper.getLevel();
            Alignment.set(member, Factions.GONDOR, 100.0f, AlignmentSource.command());
            FactionBannerBlockEntity banner = raise(helper, at, ownedBy(owner));
            helper.assertTrue(breakCancelled(level, at, member), "self-protection: a member may not break the banner");
            helper.assertFalse(breakCancelled(level, at.offset(2, 0, 2), member), "but may break other blocks");
            helper.assertFalse(breakCancelled(level, at, owner), "the owner may break it");

            banner.setSettings(banner.settings().withSelfProtection(false));
            helper.assertFalse(breakCancelled(level, at, member), "with self-protection off a member may");

            // Breaking by the owner keeps settings and owner on the dropped item; by a member only the design.
            banner.setSettings(banner.settings().withThreshold(77).withMode(BannerSettings.Mode.WHITELIST));
            BlockState state = level.getBlockState(at);
            state.getBlock().playerWillDestroy(level, at, state, owner);
            List<ItemStack> ownerDrops = Block.getDrops(state, level, at, banner);
            helper.assertTrue(ownerDrops.size() == 1, "one drop, got " + ownerDrops.size());
            ItemStack dropped = ownerDrops.get(0);
            helper.assertTrue(dropped.is(BannerBlocks.ITEM.get()), "the banner item");
            helper.assertTrue(GONDOR.equals(dropped.get(BannerComponents.DESIGN.get())), "keeps the design");
            BannerSettings kept = dropped.get(BannerComponents.PROTECTION.get());
            helper.assertTrue(kept != null && kept.isOwner(owner.getUUID()) && kept.threshold() == 77,
                    "keeps the owner's settings: " + kept);

            state.getBlock().playerWillDestroy(level, at, state, member);
            ItemStack memberDrop = Block.getDrops(state, level, at, banner).get(0);
            helper.assertTrue(memberDrop.get(BannerComponents.PROTECTION.get()) == null, "a member's drop carries no settings");

            // Structure banners protect against everyone and drop without settings.
            banner.setSettings(BannerSettings.forStructure());
            helper.assertTrue(breakCancelled(level, at, owner), "structure banner: even the old owner may not");
            helper.assertTrue(breakCancelled(level, at.offset(1, 0, 1), member), "structure banner protects its land from all");
            state.getBlock().playerWillDestroy(level, at, state, owner);
            helper.assertTrue(Block.getDrops(state, level, at, banner).get(0).get(BannerComponents.PROTECTION.get()) == null,
                    "structure banners drop without settings");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            remove(helper, owner);
            remove(helper, member);
        }
    }

    @GameTest(template = "empty")
    public static void warningIsRateLimited(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer visitor = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            raise(helper, at, ownedBy(owner).withMode(BannerSettings.Mode.WHITELIST));
            ServerLevel level = helper.getLevel();
            BannerProtection.resetWarnings();
            BannerEntry banner = BannerIndex.get(level).get(at);
            helper.assertTrue(BannerProtection.warn(visitor, banner), "the first warning is sent");
            helper.assertFalse(BannerProtection.warn(visitor, banner), "the second in the same tick is not");
            helper.assertTrue(BannerProtection.warn(owner, banner), "another player has their own timer");
            helper.succeed();
        } finally {
            BannerProtection.resetWarnings();
            takeDown(helper, at);
            remove(helper, owner);
            remove(helper, visitor);
        }
    }

    // ------------------------------------------------------------------ spawns and explosions

    @GameTest(template = "empty")
    public static void naturalMonsterSpawnsAreDenied(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            raise(helper, at, ownedBy(owner));
            ServerLevel level = helper.getLevel();
            helper.assertTrue(spawnResult(level, EntityType.ZOMBIE.create(level), at.offset(10, 0, 10), MobSpawnType.NATURAL)
                            == MobSpawnEvent.PositionCheck.Result.FAIL,
                    "a zombie inside is denied");
            helper.assertTrue(spawnResult(level, EntityType.ZOMBIE.create(level), at.offset(32, 0, 0), MobSpawnType.NATURAL)
                            == MobSpawnEvent.PositionCheck.Result.FAIL,
                    "denied at the edge");
            helper.assertTrue(spawnResult(level, EntityType.ZOMBIE.create(level), at.offset(33, 0, 0), MobSpawnType.NATURAL)
                            == MobSpawnEvent.PositionCheck.Result.DEFAULT,
                    "allowed just outside");
            helper.assertTrue(spawnResult(level, EntityType.ZOMBIE.create(level), at.offset(10, 0, 10), MobSpawnType.SPAWN_EGG)
                            == MobSpawnEvent.PositionCheck.Result.DEFAULT,
                    "spawn eggs and commands are not natural spawns");
            helper.assertTrue(spawnResult(level, EntityType.COW.create(level), at.offset(10, 0, 10), MobSpawnType.NATURAL)
                            == MobSpawnEvent.PositionCheck.Result.DEFAULT,
                    "animals are not hostile mobs");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            remove(helper, owner);
        }
    }

    private static MobSpawnEvent.PositionCheck.Result spawnResult(
            ServerLevel level, net.minecraft.world.entity.Mob mob, BlockPos pos, MobSpawnType type) {
        mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        MobSpawnEvent.PositionCheck event = new MobSpawnEvent.PositionCheck(mob, level, type, null);
        NeoForge.EVENT_BUS.post(event);
        return event.getResult();
    }

    @GameTest(template = "empty")
    public static void explosionsRespectProtection(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        ServerPlayer visitor = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos inside = at.offset(4, 0, 4);
        BlockPos outside = at.offset(40, 0, 0);
        try {
            raise(helper, at, ownedBy(owner).withMode(BannerSettings.Mode.WHITELIST));
            ServerLevel level = helper.getLevel();

            // No responsible player: nothing inside is removed, the banner itself is never blown up.
            helper.assertTrue(
                    detonate(level, null, List.of(inside, at, outside)).equals(List.of(outside)),
                    "an anonymous explosion keeps protected blocks and the banner");
            // A player without Full does not get to blow things up either.
            helper.assertTrue(
                    detonate(level, visitor, List.of(inside, outside)).equals(List.of(outside)),
                    "a visitor's explosion is filtered");
            // The owner (Full) may, but still never takes the banner with them.
            helper.assertTrue(
                    detonate(level, owner, List.of(inside, at, outside)).equals(List.of(inside, outside)),
                    "the owner's explosion removes blocks but not the banner");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            remove(helper, owner);
            remove(helper, visitor);
        }
    }

    private static List<BlockPos> detonate(ServerLevel level, ServerPlayer source, List<BlockPos> positions) {
        Explosion explosion = new Explosion(
                level, source, 0.0, 0.0, 0.0, 4.0F, false, Explosion.BlockInteraction.DESTROY, positions);
        NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, explosion, new java.util.ArrayList<>()));
        return List.copyOf(explosion.getToBlow());
    }

    @GameTest(template = "empty")
    public static void factionRelationsDecideEnemySpawns(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            raise(helper, at, ownedBy(owner)); // a Gondor banner
            ServerLevel level = helper.getLevel();
            helper.assertTrue(BannerProtection.blocksFactionSpawn(level, at.offset(3, 0, 3), Factions.MORDOR),
                    "Mordor NPCs do not spawn in a Gondor banner");
            helper.assertTrue(BannerProtection.blocksInvasion(level, at.offset(3, 0, 3), Factions.MORDOR),
                    "Mordor invasions cannot start there");
            helper.assertTrue(BannerProtection.blocksNpcGriefing(level, at.offset(3, 0, 3), Factions.MORDOR),
                    "Mordor NPCs cannot break blocks there");
            helper.assertFalse(BannerProtection.blocksFactionSpawn(level, at.offset(3, 0, 3), Factions.GONDOR),
                    "Gondor NPCs may");
            helper.assertFalse(BannerProtection.blocksFactionSpawn(level, at.offset(40, 0, 0), Factions.MORDOR),
                    "outside the cube nothing is blocked");
            helper.succeed();
        } finally {
            takeDown(helper, at);
            remove(helper, owner);
        }
    }

    @GameTest(template = "empty")
    public static void placingTheItemClaimsOrIsRefused(GameTestHelper helper) {
        ServerPlayer first = survivalPlayer(helper);
        ServerPlayer second = survivalPlayer(helper);
        // Above the template's barrier layer, so the cell above the base is plain air.
        BlockPos base = helper.absolutePos(new BlockPos(1, 4, 1));
        BlockPos at = base.above();
        BlockPos otherBase = base.offset(20, 0, 0);
        BlockPos other = otherBase.above();
        AchievementService.Sink previous = AchievementService.setSink(new AchievementService.Sink() {
            @Override
            public void toast(ServerPlayer player, ResourceLocation id) {}

            @Override
            public void broadcast(ServerPlayer player, net.minecraft.network.chat.Component line) {}
        });
        try {
            ServerLevel level = helper.getLevel();
            level.setBlock(base, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            level.setBlock(otherBase, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            Alignment.set(first, Factions.GONDOR, 1.0f, AlignmentSource.command());
            Alignment.set(second, Factions.GONDOR, 1.0f, AlignmentSource.command());

            ItemStack stack = BannerBlocks.stackOf(GONDOR);
            first.setItemInHand(InteractionHand.MAIN_HAND, stack);
            net.minecraft.world.InteractionResult result = BannerBlocks.ITEM.get().place(new net.minecraft.world.item.context.BlockPlaceContext(
                    level, first, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(Vec3.atCenterOf(base).add(0, 0.5, 0), Direction.UP, base, false)));
            helper.assertTrue(result.consumesAction(), "the first banner is placed: " + result);
            helper.assertTrue(level.getBlockState(at).getBlock() == BannerBlocks.STANDING.get(), "a standing banner");
            FactionBannerBlockEntity placed = (FactionBannerBlockEntity) level.getBlockEntity(at);
            helper.assertTrue(placed.settings().isOwner(first.getUUID()), "owned by its placer");
            helper.assertTrue(placed.effectiveRange() == 32, "protecting 32 blocks");

            // Another player's whitelist-only land refuses an overlapping banner.
            placed.setSettings(placed.settings().withMode(BannerSettings.Mode.WHITELIST));
            ItemStack stackTwo = BannerBlocks.stackOf(GONDOR);
            second.setItemInHand(InteractionHand.MAIN_HAND, stackTwo);
            net.minecraft.world.InteractionResult refused = BannerBlocks.ITEM.get().place(new net.minecraft.world.item.context.BlockPlaceContext(
                    level, second, InteractionHand.MAIN_HAND, stackTwo,
                    new BlockHitResult(Vec3.atCenterOf(otherBase).add(0, 0.5, 0), Direction.UP, otherBase, false)));
            helper.assertFalse(refused.consumesAction(), "the overlapping banner is refused: " + refused);
            helper.assertTrue(level.getBlockState(other).isAir(), "nothing was placed");
            helper.succeed();
        } finally {
            AchievementService.setSink(previous);
            AchievementService.forget(first);
            AchievementService.forget(second);
            takeDown(helper, at);
            takeDown(helper, other);
            helper.getLevel().setBlock(base, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(otherBase, Blocks.AIR.defaultBlockState(), 3);
            remove(helper, first);
            remove(helper, second);
        }
    }

    @GameTest(template = "empty")
    public static void creativeBypassesEverything(GameTestHelper helper) {
        ServerPlayer owner = survivalPlayer(helper);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        try {
            raise(helper, at, ownedBy(owner).withMode(BannerSettings.Mode.WHITELIST));
            ServerLevel level = helper.getLevel();
            ServerPlayer creative = helper.makeMockServerPlayerInLevel(); // the helper's mock is always creative
            try {
                helper.assertTrue(creative.isCreative(), "the helper's mock reports creative");
                helper.assertTrue(BannerProtection.allows(level, at.offset(3, 0, 3), creative, BannerPermission.FULL),
                        "creative is allowed");
                helper.assertFalse(BannerProtection.selfProtected(level, at, creative), "and may break the banner");
            } finally {
                level.getServer().getPlayerList().remove(creative);
            }
            helper.succeed();
        } finally {
            takeDown(helper, at);
            remove(helper, owner);
        }
    }
}
