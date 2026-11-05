package io.github.sethwillemsvg.lotrrevival.nodemap;

import io.github.sethwillemsvg.lotrrevival.core.log.LotrLog;
import io.github.sethwillemsvg.lotrrevival.nodemap.camp.CampService;
import io.github.sethwillemsvg.lotrrevival.nodemap.command.NodeCommands;
import io.github.sethwillemsvg.lotrrevival.nodemap.data.NodeRegistries;
import io.github.sethwillemsvg.lotrrevival.nodemap.discovery.NodeDiscovery;
import io.github.sethwillemsvg.lotrrevival.nodemap.graph.NodeGraph;
import io.github.sethwillemsvg.lotrrevival.nodemap.net.NodeMapPayloads;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeMapAttachment;
import io.github.sethwillemsvg.lotrrevival.nodemap.state.NodeStanding;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.ArrivalFinder;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.CombatTagTracker;
import io.github.sethwillemsvg.lotrrevival.nodemap.travel.TravelService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wiring for the node map and fast travel (specs/addon/node-map-travel.md): config, datapack registries, the player
 * attachment, packets, commands and the server events. {@link #LIVE} is the real context; game tests build their own.
 * The client side (key, HUD, map layer) registers itself through @EventBusSubscriber classes in client.nodemap.
 */
public final class NodeMapModule {
    private static volatile NodeGraph graph = NodeGraph.EMPTY;
    public static final CombatTagTracker COMBAT = new CombatTagTracker();

    public static final NodeMapContext LIVE = new NodeMapContext("live", NodeMapModule::graph, NodeMapSettings::live, NodeStanding.LIVE,
            ArrivalFinder.MIDDLE_EARTH, COMBAT, true, NodeMapPayloads::sync);
    public static final NodeDiscovery DISCOVERY = new NodeDiscovery(LIVE);
    public static final TravelService TRAVEL = new TravelService(LIVE);
    public static final CampService CAMPS = new CampService(LIVE);

    private NodeMapModule() {}

    public static NodeGraph graph() {
        return graph;
    }

    public static void init(IEventBus modBus) {
        // TODO(spec Rules and numbers): the spec puts these in lotr_revival-common.toml; kept in its own file until core owns a
        // shared common config, so two systems registering the default common file cannot clash.
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, NodeMapConfig.SPEC, "lotr_revival-nodemap.toml");
        modBus.addListener((ModConfigEvent.Loading e) -> { if (e.getConfig().getSpec() == NodeMapConfig.SPEC) NodeMapConfig.logValues(); });
        modBus.addListener((ModConfigEvent.Reloading e) -> { if (e.getConfig().getSpec() == NodeMapConfig.SPEC) NodeMapConfig.logValues(); });
        NodeRegistries.register(modBus);
        NodeMapAttachment.register(modBus);
        NodeMapPayloads.register(modBus);
        TRAVEL.status = NodeMapPayloads::sendStatus;

        var bus = NeoForge.EVENT_BUS;
        bus.addListener((RegisterCommandsEvent e) -> NodeCommands.register(e.getDispatcher()));
        bus.addListener((ServerStartedEvent e) -> {
            try {
                graph = NodeRegistries.buildGraph(e.getServer().registryAccess(), NodeMapConfig.factors(), NodeMapConfig.discoverRadius(), "server");
            } catch (RuntimeException ex) {
                LotrLog.error("nodemap", "building the node graph failed; node map is empty", ex);
                graph = NodeGraph.EMPTY;
            }
        });
        bus.addListener((ServerStoppedEvent e) -> graph = NodeGraph.EMPTY);
        bus.addListener((ServerTickEvent.Post e) -> NodeMapStats.tick(e.getServer().overworld().getGameTime()));
        bus.addListener((PlayerTickEvent.Post e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            try {
                DISCOVERY.tick(sp);
                TRAVEL.tick(sp);
            } catch (RuntimeException ex) {
                LotrLog.error("travel", "player tick failed for " + LotrLog.id(sp.getUUID()), ex);
                TRAVEL.cancel(sp, "error", false);
            }
        });

        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> { if (e.getEntity() instanceof ServerPlayer sp) NodeMapPayloads.sync(sp); });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> { if (e.getEntity() instanceof ServerPlayer sp) NodeMapPayloads.sync(sp); });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            TRAVEL.cancel(sp, "dimension");
            NodeMapPayloads.sync(sp);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            TRAVEL.forget(sp);
            NodeMapPayloads.forget(sp);
        });
        bus.addListener((LivingDeathEvent e) -> { if (e.getEntity() instanceof ServerPlayer sp) TRAVEL.cancel(sp, "death"); });
        bus.addListener((LivingIncomingDamageEvent e) -> {
            LivingEntity victim = e.getEntity();
            if (victim.level().isClientSide()) return;
            long now = victim.level().getGameTime();
            boolean byLiving = e.getSource().getEntity() instanceof LivingEntity;
            if (victim instanceof ServerPlayer sp) {
                TRAVEL.cancel(sp, "damage");
                if (byLiving) COMBAT.tag(sp, now);
            }
            if (e.getSource().getEntity() instanceof ServerPlayer attacker && attacker != victim) COMBAT.tag(attacker, now);
        });
        bus.addListener((AttackEntityEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            TRAVEL.cancel(sp, "attack");
            if (e.getTarget() instanceof LivingEntity) COMBAT.tag(sp, sp.level().getGameTime());
        });
        bus.addListener((LivingEntityUseItemEvent.Start e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && e.getDuration() > 0) TRAVEL.cancel(sp, "use_item");
        });
        bus.addListener((EntityMountEvent e) -> {
            if (e.getEntityMounting() instanceof ServerPlayer sp && !sp.level().isClientSide()) TRAVEL.cancel(sp, e.isMounting() ? "mount" : "dismount");
        });
    }
}
