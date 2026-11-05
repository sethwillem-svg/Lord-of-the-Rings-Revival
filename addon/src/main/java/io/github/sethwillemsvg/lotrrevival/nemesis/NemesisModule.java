package io.github.sethwillemsvg.lotrrevival.nemesis;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nemesis.network.NemesisRosterPayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Wiring for the nemesis system (specs/addon/nemesis-events.md, N tickets): attachments, config, data loaders, listeners,
 * commands, payloads and the client key. {@code LotrRevival} calls {@link #init} once.
 */
public final class NemesisModule {
    private NemesisModule() {}

    public static void init(IEventBus modBus) {
        NemesisLink.register(modBus);
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, NemesisConfig.SPEC, "lotr_revival-nemesis.toml");
        modBus.addListener((RegisterPayloadHandlersEvent e) -> NemesisRosterPayloads.register(e));

        var bus = NeoForge.EVENT_BUS;
        bus.addListener((AddReloadListenerEvent e) -> {
            e.addListener(new NemesisTraits());
            e.addListener(new NemesisNames());
        });
        bus.addListener((ServerStartedEvent e) -> onStarted(e.getServer()));
        bus.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        bus.addListener(EventPriority.LOW, (LivingIncomingDamageEvent e) -> TraitEffects.onIncoming(e));
        bus.addListener((LivingIncomingDamageEvent e) -> DeathRules.onIncoming(e));
        bus.addListener(EventPriority.LOWEST, (LivingDamageEvent.Pre e) -> DeathRules.onDamagePre(e));
        bus.addListener((LivingDamageEvent.Post e) -> {
            TraitEffects.onDamagePost(e);
            Sergeants.onDamagePost(e);
        });
        bus.addListener(EventPriority.LOW, (LivingDeathEvent e) -> {
            if (e.isCanceled()) return;
            Promotion.onDeath(e);
            DeathRules.onDeath(e);
        });
        bus.addListener((EntityJoinLevelEvent e) -> Manifestation.onJoin(e));
        bus.addListener((PlayerInteractEvent.EntityInteract e) -> Manifestation.onInteract(e));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                Ambush.onLogin(p);
                NemesisPlayerData.checkSideSwitch(p, NemesisSavedData.get(p.getServer()));
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) Ambush.onLogout(p);
        });
        bus.addListener((RegisterCommandsEvent e) -> NemesisCommands.register(e.getDispatcher()));

        if (FMLEnvironment.dist.isClient()) io.github.sethwillemsvg.lotrrevival.client.nemesis.NemesisClient.init(modBus);
        LotrLog.info("nemesis", "module registered");
    }

    private static void onStarted(MinecraftServer server) {
        NemesisConfig.logInUse();
        Manifestation.onServerStarted(server);
        NemesisSavedData.get(server).logLoaded();
        LotrLog.info("nemesis", "traits in use: {}", NemesisTraits.count());
    }

    private static void tick(MinecraftServer server) {
        try {
            DeathRules.tick(server);
            if (!NemesisConfig.enabled()) return;
            Manifestation.tick(server);
            Ambush.tick(server);
            Politics.tick(server);
            if (server.getTickCount() % 600 == 0) NemesisStats.summary(server);
        } catch (RuntimeException e) {
            LotrLog.error("nemesis", "server tick failed", e);
        }
    }
}
