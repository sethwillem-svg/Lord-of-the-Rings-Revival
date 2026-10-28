package io.github.sethwillemsvg.lotrrevival.progression.test;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.client.progression.fellowship.FellowshipPageModel;
import io.github.sethwillemsvg.lotrrevival.client.progression.fellowship.FellowshipPageModel.NameCheck;
import io.github.sethwillemsvg.lotrrevival.progression.fellowship.FellowshipRole;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The role rules and name hints of the Fellowships pages (plain data, no screen needed). */
@GameTestHolder(LOTRRevival.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FellowshipPageModelTest {
    @GameTest(template = "empty")
    public static void roleActions(GameTestHelper helper) {
        var owner = FellowshipPageModel.actions(FellowshipRole.OWNER);
        var admin = FellowshipPageModel.actions(FellowshipRole.ADMIN);
        var member = FellowshipPageModel.actions(FellowshipRole.MEMBER);
        helper.assertTrue(owner.rename() && owner.disband() && owner.toggleMap() && !owner.leave(), "owner actions");
        helper.assertTrue(admin.invite() && admin.toggleCombat() && !admin.toggleMap() && !admin.rename() && admin.leave(), "admin actions");
        helper.assertTrue(!member.invite() && !member.setIcon() && !member.toggleCombat() && member.leave(), "member actions");

        var adminOnMember = FellowshipPageModel.memberActions(FellowshipRole.ADMIN, FellowshipRole.MEMBER, true, false);
        var adminOnAdmin = FellowshipPageModel.memberActions(FellowshipRole.ADMIN, FellowshipRole.ADMIN, true, false);
        var ownerOnAdminOffline = FellowshipPageModel.memberActions(FellowshipRole.OWNER, FellowshipRole.ADMIN, false, false);
        var ownerOnSelf = FellowshipPageModel.memberActions(FellowshipRole.OWNER, FellowshipRole.OWNER, true, true);
        helper.assertTrue(adminOnMember.remove() && !adminOnMember.promote() && !adminOnMember.transfer(), "admin on member");
        helper.assertTrue(!adminOnAdmin.any(), "admin cannot touch admins");
        helper.assertTrue(ownerOnAdminOffline.remove() && ownerOnAdminOffline.demote() && !ownerOnAdminOffline.transfer(), "owner on offline admin");
        helper.assertTrue(!ownerOnSelf.any(), "nothing on self");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nameHints(GameTestHelper helper) {
        List<String> mine = List.of("Company of the Ring");
        helper.assertTrue(FellowshipPageModel.checkName("  ", mine, null) == NameCheck.EMPTY, "empty");
        helper.assertTrue(FellowshipPageModel.checkName("a".repeat(25), mine, null) == NameCheck.TOO_LONG, "too long");
        helper.assertTrue(FellowshipPageModel.checkName("Bad!", mine, null) == NameCheck.BAD_CHARS, "bad chars");
        helper.assertTrue(FellowshipPageModel.checkName("company of the ring", mine, null) == NameCheck.TAKEN, "taken, case-insensitive");
        helper.assertTrue(FellowshipPageModel.checkName("Company of the Ring", mine, "Company of the Ring") == NameCheck.OK, "own name may be kept");
        helper.assertTrue(FellowshipPageModel.checkName("Rangers-of_Ithilien's", mine, null) == NameCheck.OK, "valid");
        helper.succeed();
    }
}
