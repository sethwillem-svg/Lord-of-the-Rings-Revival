package io.github.sethwillemsvg.lotrrevival.combat;

import io.github.sethwillemsvg.lotrrevival.client.anim.ClientAttack;
import io.github.sethwillemsvg.lotrrevival.client.combat.CombatClientModule;
import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Combat wiring (specs/addon/combat-parry-warning.md): config, attachments, payloads (registrar version 2), and the server
 * listeners of AttackTiming, Parry and CombatStats. Client wiring is in client.combat.CombatClientModule.
 */
public final class CombatModule {
    public static final String PAYLOAD_VERSION = "2";

    private CombatModule() {}

    public static void init(IEventBus modBus) {
        ModContainer container = ModLoadingContext.get().getActiveContainer();
        container.registerConfig(ModConfig.Type.COMMON, CombatConfig.SPEC);
        modBus.addListener((ModConfigEvent.Loading e) -> {
            if (e.getConfig().getSpec() == CombatConfig.SPEC) CombatConfig.logInUse();
        });
        modBus.addListener((ModConfigEvent.Reloading e) -> {
            if (e.getConfig().getSpec() == CombatConfig.SPEC) CombatConfig.logInUse();
        });
        ParryState.register(modBus);
        modBus.addListener(CombatModule::payloads);
        AttackTiming.register();
        Parry.register();
        CombatStats.register();
        if (FMLEnvironment.dist.isClient()) CombatClientModule.init(modBus, container);
        LotrLog.info("combat", "combat module registered (payload version {})", PAYLOAD_VERSION);
    }

    private static void payloads(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar(PAYLOAD_VERSION);
        // client handlers live in client classes; the lambdas only run on a client, so a dedicated server never loads them
        r.playToClient(NpcAttackPayload.TYPE, NpcAttackPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientAttack.handle(p)));
        r.playToClient(NpcAttackEndPayload.TYPE, NpcAttackEndPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientAttack.end(p)));
        r.playToClient(ParryFxPayload.TYPE, ParryFxPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientAttack.fx(p)));
        r.playToServer(ParryPayload.TYPE, ParryPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (player != null) Parry.press(player);
        }));
    }
}
