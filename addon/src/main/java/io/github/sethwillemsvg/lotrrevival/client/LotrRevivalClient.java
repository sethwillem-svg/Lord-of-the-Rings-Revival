package io.github.sethwillemsvg.lotrrevival.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import io.github.sethwillemsvg.lotrrevival.client.bench.AutoBench;
import io.github.sethwillemsvg.lotrrevival.client.bench.FrameStats;
import io.github.sethwillemsvg.lotrrevival.client.render.SwitchingNpcRenderer;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.sevenstars.middleearth.entity.EntitiesME;

import java.util.Arrays;
import java.util.Locale;

/** Client wiring: our NPC renderer replaces Middle-earth's (registered last), the benchmark HUD and /lotrrender. */
public final class LotrRevivalClient {
    private LotrRevivalClient() {}

    @EventBusSubscriber(modid = LotrRevival.MOD_ID, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(EntitiesME.NPC, SwitchingNpcRenderer::new);
        }

        @SubscribeEvent
        public static void gui(RegisterGuiLayersEvent e) {
            e.registerAboveAll(LotrRevival.id("bench_stats"), FrameStats::draw);
        }
    }

    @EventBusSubscriber(modid = LotrRevival.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void frame(RenderFrameEvent.Pre e) {
            FrameStats.frame();
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            AutoBench.tick();
        }

        /** /lotrrender theirs|vanilla|pulse_layers|pulse   switch the NPC renderer;   /lotrrender hud   toggle the frame-time meter */
        @SubscribeEvent
        public static void commands(RegisterClientCommandsEvent e) {
            e.getDispatcher().register(Commands.literal("lotrrender")
                    .then(Commands.literal("hud").executes(c -> {
                        FrameStats.visible = !FrameStats.visible;
                        return 1;
                    }))
                    .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                    Arrays.stream(SwitchingNpcRenderer.Mode.values()).map(SwitchingNpcRenderer.Mode::id), b))
                            .executes(c -> {
                                String m = StringArgumentType.getString(c, "mode").toUpperCase(Locale.ROOT);
                                try {
                                    SwitchingNpcRenderer.mode = SwitchingNpcRenderer.Mode.valueOf(m);
                                } catch (IllegalArgumentException ex) {
                                    c.getSource().sendFailure(Component.literal("Use theirs, vanilla, pulse_layers or pulse"));
                                    return 0;
                                }
                                FrameStats.reset();
                                c.getSource().sendSuccess(() -> Component.literal("NPC renderer: " + SwitchingNpcRenderer.mode.id()
                                        + " (frame stats reset; wait 5 s)"), false);
                                return 1;
                            })));
        }
    }
}
