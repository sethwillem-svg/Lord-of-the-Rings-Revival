package io.github.sethwillemsvg.lotrrevival.core;

import io.github.sethwillemsvg.lotrrevival.core.alignment.AlignmentAttachments;
import io.github.sethwillemsvg.lotrrevival.core.brewing.BrewingItems;
import io.github.sethwillemsvg.lotrrevival.core.command.LotrCommand;
import io.github.sethwillemsvg.lotrrevival.core.network.CorePayloads;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Core pod entry point. Owned by the Core pod. */
public final class CoreMod {
    private CoreMod() {}

    public static void init(IEventBus modBus) {
        // Core: combat foundation (#107). Runs every core.combat.gear.* holder; later gear tickets never edit CoreMod.
        io.github.sethwillemsvg.lotrrevival.core.combat.CombatItems.init(modBus);
        // End Core: combat foundation
        ModDataRegistries.register(modBus);
        AlignmentAttachments.init();
        modBus.addListener(CorePayloads::register);
        BrewingItems.init(modBus);
        io.github.sethwillemsvg.lotrrevival.core.item.CoreItems.init(modBus);
        io.github.sethwillemsvg.lotrrevival.core.coin.CoinItems.init(modBus);
        NeoForge.EVENT_BUS.addListener(LotrCommand::onRegisterCommands);
    }
}
