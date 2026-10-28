package io.github.sethwillemsvg.lotrrevival.progression.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.Fellowship;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipData;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipDamage;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipOption;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipResult;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipRole;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipRules;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.Fellowships;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Fellowships: lead limit, roles, size, invites, damage rules and persistence. */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FellowshipTest {
    private FellowshipTest() {}

    /** Removes the fellowships the test made and the mock players, and restores the service hooks. */
    private static void cleanUp(MinecraftServer server, List<UUID> owners, ServerPlayer... players) {
        Fellowships.resetHooks();
        FellowshipData data = FellowshipData.get(server);
        for (Fellowship fellowship : data.all()) {
            if (owners.contains(fellowship.owner())) {
                Fellowships.disbandNow(server, fellowship);
            }
        }
        for (ServerPlayer player : players) {
            server.getPlayerList().remove(player);
        }
    }

    private static Fellowship make(GameTestHelper helper, ServerPlayer owner, String name) {
        FellowshipResult result = Fellowships.create(owner, name, false);
        helper.assertTrue(result.ok(), "create " + name + ": " + result.message().getString());
        return FellowshipData.get(owner.getServer()).of(owner.getUUID()).stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static void join(GameTestHelper helper, ServerPlayer owner, Fellowship fellowship, ServerPlayer joiner) {
        FellowshipResult invite = Fellowships.invite(owner, fellowship.id(), joiner.getUUID());
        helper.assertTrue(invite.ok(), "invite: " + invite.message().getString());
        FellowshipResult accept = Fellowships.accept(joiner, fellowship.id());
        helper.assertTrue(accept.ok(), "accept: " + accept.message().getString());
    }

    private static boolean arrowCancelled(GameTestHelper helper, ServerPlayer shooter, ServerPlayer target) {
        AbstractArrow arrow = EntityType.ARROW.create(helper.getLevel());
        arrow.setOwner(shooter);
        DamageSource source = helper.getLevel().damageSources().arrow(arrow, shooter);
        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(target, new DamageContainer(source, 5.0f));
        NeoForge.EVENT_BUS.post(event);
        return event.isCanceled();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void preventPvpCancelsMemberArrowsUntilToggledOff(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer member = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        try {
            Fellowships.setAchievementCounter(p -> 0);
            Fellowship fellowship = make(helper, owner, "Test Company");
            join(helper, owner, fellowship, member);

            helper.assertTrue(arrowCancelled(helper, owner, member), "member arrow cancelled while Prevent PvP is on");
            helper.assertFalse(arrowCancelled(helper, owner, stranger), "stranger arrow lands");
            helper.assertTrue(FellowshipRules.preventsPvp(server, owner.getUUID(), member.getUUID()), "query agrees");

            FellowshipResult off = Fellowships.setOption(owner, fellowship.id(), FellowshipOption.PREVENT_PVP, false);
            helper.assertTrue(off.ok(), "owner toggles the option");
            helper.assertFalse(arrowCancelled(helper, owner, member), "arrow lands once the option is off");
            helper.assertFalse(
                    Fellowships.setOption(member, fellowship.id(), FellowshipOption.PREVENT_PVP, true).ok(),
                    "plain member cannot toggle");
        } finally {
            cleanUp(server, List.of(owner.getUUID()), owner, member, stranger);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void hiredUnitsAreProtectedFromCoMembersOnly(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer member = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        try {
            Fellowships.setAchievementCounter(p -> 0);
            Fellowship fellowship = make(helper, owner, "Test Riders");
            join(helper, owner, fellowship, member);

            FellowshipDamage.Side ownersUnit = new FellowshipDamage.Side(owner.getUUID(), true);
            FellowshipDamage.Side membersUnit = new FellowshipDamage.Side(member.getUUID(), true);
            FellowshipDamage.Side memberSelf = new FellowshipDamage.Side(member.getUUID(), false);
            FellowshipDamage.Side strangerSelf = new FellowshipDamage.Side(stranger.getUUID(), false);

            helper.assertTrue(FellowshipDamage.blocks(server, memberSelf, ownersUnit), "member cannot hurt co-member's unit");
            helper.assertTrue(FellowshipDamage.blocks(server, ownersUnit, memberSelf), "unit cannot hurt co-member");
            helper.assertTrue(FellowshipDamage.blocks(server, membersUnit, ownersUnit), "units of co-members spare each other");
            helper.assertFalse(FellowshipDamage.blocks(server, strangerSelf, ownersUnit), "a stranger can hurt the unit");
            helper.assertFalse(
                    FellowshipDamage.blocks(server, new FellowshipDamage.Side(owner.getUUID(), true), new FellowshipDamage.Side(owner.getUUID(), false)),
                    "same owner is not covered");

            Fellowships.setOption(owner, fellowship.id(), FellowshipOption.PREVENT_UNIT_FRIENDLY_FIRE, false);
            helper.assertFalse(FellowshipDamage.blocks(server, memberSelf, ownersUnit), "option off: unit can be hurt");
            helper.assertTrue(FellowshipDamage.blocks(server, memberSelf, new FellowshipDamage.Side(owner.getUUID(), false)), "PvP option still on");
        } finally {
            cleanUp(server, List.of(owner.getUUID()), owner, member, stranger);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void leadLimitIsOnePlusAchievementsOverTwenty(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        try {
            helper.assertTrue(Fellowships.leadLimit(0) == 1, "0 achievements: 1");
            helper.assertTrue(Fellowships.leadLimit(19) == 1, "19 achievements: 1");
            helper.assertTrue(Fellowships.leadLimit(20) == 2, "20 achievements: 2");
            helper.assertTrue(Fellowships.leadLimit(40) == 3, "40 achievements: 3");

            Fellowships.setAchievementCounter(p -> 19);
            make(helper, player, "Test One");
            FellowshipResult second = Fellowships.create(player, "Test Two", false);
            helper.assertFalse(second.ok(), "19 achievements cannot lead a second fellowship");

            Fellowships.setAchievementCounter(p -> 20);
            FellowshipResult allowed = Fellowships.create(player, "Test Two", false);
            helper.assertTrue(allowed.ok(), "20 achievements can: " + allowed.message().getString());
            helper.assertFalse(Fellowships.create(player, "Test Three", false).ok(), "but not a third");

            helper.assertFalse(Fellowships.create(player, "test one", false).ok(), "case-insensitive name clash refused");
            helper.assertFalse(Fellowships.validName("bad/name").isPresent(), "slash is not allowed");
            helper.assertFalse(Fellowships.validName("   ").isPresent(), "blank is not allowed");
            helper.assertFalse(Fellowships.validName("x".repeat(25)).isPresent(), "25 characters is too long");
            helper.assertTrue(Fellowships.validName("  O'Neil-the_3rd ").isPresent(), "punctuation allowed, trimmed");
        } finally {
            cleanUp(server, List.of(player.getUUID()), player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void fullFellowshipRefusesAcceptanceAndInvites(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        ServerPlayer third = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        try {
            Fellowships.setAchievementCounter(p -> 0);
            Fellowships.setMaxSize(() -> 3);
            Fellowship fellowship = make(helper, owner, "Test Full");
            helper.assertTrue(Fellowships.invite(owner, fellowship.id(), first.getUUID()).ok(), "invite first");
            helper.assertTrue(Fellowships.invite(owner, fellowship.id(), second.getUUID()).ok(), "invite second");
            helper.assertTrue(Fellowships.accept(first, fellowship.id()).ok(), "first accepts (size 2)");

            Fellowships.setMaxSize(() -> 2);
            FellowshipResult refused = Fellowships.accept(second, fellowship.id());
            helper.assertFalse(refused.ok(), "acceptance re-checks the size");
            helper.assertTrue(fellowship.invites().containsKey(second.getUUID()), "the invite stays pending");
            helper.assertFalse(Fellowships.invite(owner, fellowship.id(), third.getUUID()).ok(), "no new invites past the limit");

            Fellowships.setMaxSize(() -> -1);
            helper.assertTrue(Fellowships.accept(second, fellowship.id()).ok(), "no limit: accepted now");
            helper.assertTrue(fellowship.size() == 3, "three members");
            helper.assertFalse(Fellowships.accept(third, fellowship.id()).ok(), "no invite, no entry");
        } finally {
            cleanUp(server, List.of(owner.getUUID()), owner, first, second, third);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void rolesGateRenameLeaveRemoveAndTransfer(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer admin = helper.makeMockServerPlayerInLevel();
        ServerPlayer member = helper.makeMockServerPlayerInLevel();
        ServerPlayer outsider = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        try {
            Fellowships.setAchievementCounter(p -> 0);
            Fellowship fellowship = make(helper, owner, "Test Roles");
            join(helper, owner, fellowship, admin);
            join(helper, owner, fellowship, member);
            helper.assertTrue(Fellowships.promote(owner, fellowship.id(), admin.getUUID()).ok(), "owner promotes");
            helper.assertTrue(fellowship.role(admin.getUUID()).orElseThrow() == FellowshipRole.ADMIN, "now admin");

            helper.assertFalse(Fellowships.rename(admin, fellowship.id(), "Test Renamed").ok(), "admin cannot rename");
            helper.assertFalse(Fellowships.disband(admin, fellowship.id()).ok(), "admin cannot disband");
            helper.assertFalse(Fellowships.promote(admin, fellowship.id(), member.getUUID()).ok(), "admin cannot promote");
            helper.assertFalse(Fellowships.leave(owner, fellowship.id()).ok(), "owner cannot leave");
            helper.assertTrue(fellowship.owner().equals(owner.getUUID()), "still the owner");
            helper.assertFalse(Fellowships.remove(admin, fellowship.id(), owner.getUUID()).ok(), "nobody removes the owner");
            helper.assertFalse(Fellowships.remove(member, fellowship.id(), admin.getUUID()).ok(), "member cannot remove");
            helper.assertFalse(Fellowships.invite(outsider, fellowship.id(), outsider.getUUID()).ok(), "outsider cannot invite");
            helper.assertFalse(
                    Fellowships.setOption(admin, fellowship.id(), FellowshipOption.SHOW_MAP, false).ok(),
                    "admin cannot toggle map locations");
            helper.assertTrue(
                    Fellowships.setOption(admin, fellowship.id(), FellowshipOption.PREVENT_PVP, false).ok(),
                    "admin can toggle PvP");
            helper.assertTrue(Fellowships.setIcon(admin, fellowship.id(), new ItemStack(Items.DIAMOND, 5)).ok(), "admin sets icon");
            helper.assertTrue(fellowship.icon().getCount() == 1 && fellowship.icon().is(Items.DIAMOND), "icon is count 1");
            helper.assertTrue(Fellowships.setIcon(admin, fellowship.id(), ItemStack.EMPTY).ok(), "empty hand clears");
            helper.assertTrue(fellowship.icon().isEmpty(), "icon cleared");

            helper.assertTrue(Fellowships.rename(owner, fellowship.id(), "Test Renamed").ok(), "owner renames");
            helper.assertTrue(fellowship.name().equals("Test Renamed"), "new name stored");

            // An admin removes a plain member but not another admin.
            helper.assertTrue(Fellowships.remove(admin, fellowship.id(), member.getUUID()).ok(), "admin removes a member");
            helper.assertFalse(fellowship.isMember(member.getUUID()), "member gone");
            helper.assertTrue(Fellowships.leave(admin, fellowship.id()).ok(), "an admin may leave");

            join(helper, owner, fellowship, member);
            helper.assertTrue(Fellowships.transfer(owner, fellowship.id(), member.getUUID()).ok(), "transfer");
            helper.assertTrue(fellowship.owner().equals(member.getUUID()), "new owner");
            helper.assertTrue(fellowship.role(owner.getUUID()).orElseThrow() == FellowshipRole.ADMIN, "old owner is an admin");
            helper.assertTrue(Fellowships.disband(member, fellowship.id()).ok(), "new owner disbands");
            helper.assertTrue(FellowshipData.get(server).find(fellowship.id()).isEmpty(), "gone");
            helper.assertFalse(Fellowships.accept(outsider, fellowship.id()).ok(), "accepting a disbanded fellowship fails");
        } finally {
            cleanUp(server, List.of(owner.getUUID(), member.getUUID()), owner, admin, member, outsider);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void transferRespectsTheHeirsLeadLimit(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer heir = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        try {
            Fellowships.setAchievementCounter(p -> 0);
            Fellowship fellowship = make(helper, owner, "Test Crown");
            make(helper, heir, "Test Heir");
            join(helper, owner, fellowship, heir);
            helper.assertFalse(Fellowships.transfer(owner, fellowship.id(), heir.getUUID()).ok(), "heir already leads their one");
            Fellowships.setAchievementCounter(p -> 20);
            helper.assertTrue(Fellowships.transfer(owner, fellowship.id(), heir.getUUID()).ok(), "heir has room with 20");
        } finally {
            cleanUp(server, List.of(owner.getUUID(), heir.getUUID()), owner, heir);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    @SuppressWarnings("removal")
    public static void fellowshipDataRoundTripsThroughNbt(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer member = helper.makeMockServerPlayerInLevel();
        ServerPlayer invitee = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = helper.getLevel().getServer();
        try {
            Fellowships.setAchievementCounter(p -> 0);
            Fellowship fellowship = make(helper, owner, "Test Saved");
            join(helper, owner, fellowship, member);
            Fellowships.invite(owner, fellowship.id(), invitee.getUUID());
            Fellowships.setIcon(owner, fellowship.id(), new ItemStack(Items.EMERALD));
            Fellowships.setOption(owner, fellowship.id(), FellowshipOption.SHOW_MAP, false);

            FellowshipData data = FellowshipData.get(server);
            CompoundTag saved = data.save(new CompoundTag(), server.registryAccess());
            FellowshipData copy = FellowshipData.load(saved, server.registryAccess());
            Fellowship loaded = copy.find(fellowship.id()).orElseThrow();
            helper.assertTrue(loaded.name().equals("Test Saved"), "name");
            helper.assertTrue(loaded.owner().equals(owner.getUUID()), "owner");
            helper.assertTrue(loaded.members().contains(member.getUUID()), "member");
            helper.assertTrue(loaded.invites().containsKey(invitee.getUUID()), "invite survives");
            helper.assertTrue(loaded.invites().get(invitee.getUUID()).equals(owner.getUUID()), "inviter kept");
            helper.assertTrue(loaded.icon().is(Items.EMERALD), "icon");
            helper.assertFalse(loaded.option(FellowshipOption.SHOW_MAP), "option off survives");
            helper.assertTrue(loaded.option(FellowshipOption.PREVENT_PVP), "option on survives");
            List<UUID> viewers = new ArrayList<>(loaded.allMembers());
            helper.assertTrue(viewers.size() == 2, "two members");
        } finally {
            cleanUp(server, List.of(owner.getUUID()), owner, member, invitee);
        }
        helper.succeed();
    }
}
