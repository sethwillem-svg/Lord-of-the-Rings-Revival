package io.github.sethwillemsvg.lotrrevival.npc.interact;

import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcEntity;
import io.github.sethwillemsvg.lotrrevival.npc.entity.NpcRole;
import io.github.sethwillemsvg.lotrrevival.npc.speech.NpcSpeech;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/** Right-click order: trade or hire when allowed, otherwise talk. Sneak-right-click always talks. */
public final class NpcInteraction {
    private NpcInteraction() {}

    public static void onRightClick(NpcEntity npc, ServerPlayer player) {
        if (io.github.sethwillemsvg.lotrrevival.npc.hire.command.UnitCommandMenus.openFor(npc, player)) {
            return;
        }
        if (!player.isShiftKeyDown() && npc.getRole().isTrader()) {
            NpcInteractEvent.Kind kind = npc.getRole() == NpcRole.UNIT_TRADER
                    ? NpcInteractEvent.Kind.HIRE
                    : NpcInteractEvent.Kind.TRADE;
            Optional<Float> min = NpcSpeech.accessMin(npc);
            if (min.isPresent()
                    && !NpcStanding.isHostile(npc, player)
                    && NpcStanding.alignment(npc, player) >= min.get()) {
                if (!NeoForge.EVENT_BUS.post(new NpcInteractEvent(npc, player, kind)).isCanceled()) {
                    NpcSpeech.talkWith(npc, player, NpcSpeech.TRADER_NONE);
                }
                return;
            }
        }
        if (!NeoForge.EVENT_BUS.post(new NpcInteractEvent(npc, player, NpcInteractEvent.Kind.TALK)).isCanceled()) {
            NpcSpeech.talk(npc, player);
        }
    }
}
