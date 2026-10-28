package io.github.sethwillemsvg.lotrrevival.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Picks and publishes the running world's {@link WorldScale}.
 *
 * <p><b>How a world gets its scale.</b> The mod's own data is {@link WorldScale#DEFAULT} (half). Two optional
 * built-in data packs, {@code mod/lotrrevival:datapacks/world_scale_legacy} and {@code ..._quarter}, override the
 * three NovoAtlas map_infos, the painted-height density function and the {@code lotrrevival:world_scale} registry
 * entry. A player enables one on the Create World screen (More, Data Packs) and vanilla stores the choice in the
 * world, so it stays fixed. Datapack registries load only when the world loads, so the terrain and the Java side
 * (read from the same registry) can never disagree, even after {@code /reload}.
 *
 * <p>The scale is published here before any level exists ({@link ServerAboutToStartEvent}, highest priority), so
 * roads, waypoints, the landing point and respawn rules all convert map pixels with the same value.
 */
public final class WorldScales {
    /** {@code lotrrevival:world_scale}: one entry, {@link #ENTRY}. Synced so clients can convert map pixels too. */
    public static final ResourceKey<Registry<Setting>> REGISTRY =
            ResourceKey.createRegistryKey(LOTRRevival.id("world_scale"));

    /** The single registry entry, {@code data/lotrrevival/lotrrevival/world_scale/middle_earth.json}. */
    public static final ResourceKey<Setting> ENTRY = ResourceKey.create(REGISTRY, LOTRRevival.id("middle_earth"));

    /** The scales that come as optional data packs; the default needs none. */
    public static final List<WorldScale> PACK_SCALES = List.of(WorldScale.LEGACY, WorldScale.QUARTER);

    /** The registry value: {@code {"scale": "half"}}. */
    public record Setting(WorldScale scale) {
        public static final Codec<Setting> CODEC = RecordCodecBuilder.create(i -> i.group(
                        WorldScale.CODEC.fieldOf("scale").forGetter(Setting::scale))
                .apply(i, Setting::new));
    }

    /** {@code lotrrevival:world_scale} attachment on the overworld. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WorldScaleLock>> LOCK =
            ModRegistries.ATTACHMENT_TYPES.register(
                    "world_scale",
                    () -> AttachmentType.builder(() -> WorldScaleLock.EMPTY)
                            .serialize(WorldScaleLock.CODEC)
                            .build());

    private static volatile WorldScale current = WorldScale.DEFAULT;

    /** Set at server start when the world was made at a different scale than the one now active. */
    private static volatile Optional<WorldScale> mismatch = Optional.empty();

    private WorldScales() {}

    /** The running world's scale; {@link WorldScale#DEFAULT} when no server is running. */
    public static WorldScale current() {
        return current;
    }

    /** Replaces the published scale. Tests restore the previous value in a {@code finally}. */
    public static void publish(WorldScale scale) {
        current = scale;
    }

    /** The scale named by the data packs of {@code access}, or the default when the entry is missing. */
    public static WorldScale fromRegistry(RegistryAccess access) {
        return access.registry(REGISTRY)
                .flatMap(registry -> registry.getOptional(ENTRY))
                .map(Setting::scale)
                .orElse(WorldScale.DEFAULT);
    }

    /** Pack id of the optional pack that selects {@code scale}, as {@code /datapack} and server.properties use it. */
    public static String packId(WorldScale scale) {
        return "mod/" + packLocation(scale);
    }

    private static ResourceLocation packLocation(WorldScale scale) {
        return LOTRRevival.id("datapacks/" + scale.packName());
    }

    public static void init(IEventBus modBus) {
        ModDataRegistries.registerSynced(REGISTRY, Setting.CODEC);
        modBus.addListener(WorldScales::addPacks);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ServerAboutToStartEvent event) ->
                publish(fromRegistry(event.getServer().registryAccess())));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> checkLock(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            publish(WorldScale.DEFAULT);
            mismatch = Optional.empty();
        });
        NeoForge.EVENT_BUS.addListener(WorldScales::onLogin);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> registerCommand(event.getDispatcher()));
    }

    /** Offers the Legacy and Quarter packs; off by default (feature packs are never enabled automatically). */
    private static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        for (WorldScale scale : PACK_SCALES) {
            event.addPackFinders(
                    packLocation(scale),
                    PackType.SERVER_DATA,
                    Component.translatable("pack.lotrrevival." + scale.packName()),
                    PackSource.FEATURE,
                    false,
                    Pack.Position.TOP);
        }
    }

    private static void checkLock(MinecraftServer server) {
        var overworld = server.overworld();
        WorldScale active = current();
        WorldScaleLock before = overworld.getData(LOCK);
        WorldScaleLock lock = before.settle(active, before.madeWith().isEmpty() && hasMiddleEarthTerrain(server));
        if (!lock.equals(before)) {
            overworld.setData(LOCK, lock);
            LOTRRevival.LOGGER.info(
                    "Middle-earth world scale recorded as {}{}",
                    lock.madeWith().orElseThrow().id(),
                    lock.assumed() ? " (existing terrain from before world scales, assumed Legacy)" : "");
        }
        mismatch = lock.mismatch(active);
        mismatch.ifPresent(made -> LOTRRevival.LOGGER.error(
                "Middle-earth is running at {} scale but this world was made at {} scale; new chunks will not line"
                        + " up with old ones. {}",
                active.id(),
                made.id(),
                made == WorldScale.DEFAULT
                        ? "Disable the world-scale data packs and restart."
                        : "Enable only the data pack " + packId(made) + " and restart."));
    }

    /** Whether Middle-earth already has saved chunks in this world's folder. */
    private static boolean hasMiddleEarthTerrain(MinecraftServer server) {
        Path region = server.getWorldPath(LevelResource.ROOT)
                .resolve("dimensions")
                .resolve(LOTRRevival.MOD_ID)
                .resolve("middle_earth")
                .resolve("region");
        if (!Files.isDirectory(region)) {
            return false;
        }
        try (var files = Files.list(region)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith(".mca"));
        } catch (IOException e) {
            return false;
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.hasPermissions(Commands.LEVEL_GAMEMASTERS)) {
            mismatch.ifPresent(made -> player.sendSystemMessage(mismatchMessage(current(), made)));
        }
    }

    private static Component mismatchMessage(WorldScale active, WorldScale made) {
        Component fix = made == WorldScale.DEFAULT
                ? Component.translatable("message.lotrrevival.world_scale.fix_default")
                : Component.translatable("message.lotrrevival.world_scale.fix_pack", packId(made));
        return Component.translatable("message.lotrrevival.world_scale.mismatch", name(active), name(made), fix)
                .withStyle(ChatFormatting.RED);
    }

    private static Component name(WorldScale scale) {
        return Component.translatable("world_scale.lotrrevival." + scale.id());
    }

    /** {@code /lotr worldscale}: the active scale, what that means in blocks, and the scale the world was made at. */
    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lotr").then(Commands.literal("worldscale").executes(ctx -> {
            CommandSourceStack source = ctx.getSource();
            WorldScale scale = current();
            long bree = Math.round(Math.hypot(
                    MiddleEarthMap.blockX(1819.5, scale), MiddleEarthMap.blockZ(1466, scale)));
            source.sendSuccess(
                    () -> Component.translatable(
                            "commands.lotrrevival.worldscale",
                            name(scale),
                            String.valueOf((int) scale.heightBlocksPerPixel()),
                            String.valueOf(bree)),
                    false);
            source.getServer().overworld().getData(LOCK).madeWith().ifPresent(made -> source.sendSuccess(
                    () -> Component.translatable("commands.lotrrevival.worldscale.made_with", name(made)), false));
            mismatch.ifPresent(made -> source.sendSuccess(() -> mismatchMessage(scale, made), false));
            return (int) Math.round(scale.factor() * 100);
        })));
    }

    /** English strings; called from datagen. */
    public static void contributeLang() {
        ModLangProvider.contribute(lang -> {
            lang.add("pack.lotrrevival.world_scale_legacy", "Middle-earth: Legacy scale (1x)");
            lang.add("pack.lotrrevival.world_scale_quarter", "Middle-earth: Quarter scale (1/4)");
            lang.add("world_scale.lotrrevival.legacy", "Legacy (1x)");
            lang.add("world_scale.lotrrevival.half", "Half (1/2)");
            lang.add("world_scale.lotrrevival.quarter", "Quarter (1/4)");
            lang.add(
                    "commands.lotrrevival.worldscale",
                    "Middle-earth scale: %1$s. One map pixel is %2$s blocks; Hobbiton to Bree is about %3$s blocks.");
            lang.add("commands.lotrrevival.worldscale.made_with", "This world's Middle-earth was made at %s scale.");
            lang.add(
                    "message.lotrrevival.world_scale.mismatch",
                    "Middle-earth is running at %1$s scale, but this world was made at %2$s scale. New chunks will"
                            + " not line up with old ones. %3$s");
            lang.add(
                    "message.lotrrevival.world_scale.fix_default",
                    "Disable every Middle-earth world-scale data pack and reopen the world.");
            lang.add(
                    "message.lotrrevival.world_scale.fix_pack",
                    "Enable the data pack %s (and no other world-scale pack), then reopen the world.");
        });
    }
}
