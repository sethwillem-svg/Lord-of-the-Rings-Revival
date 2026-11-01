package io.github.sethwillemsvg.lotrrevival.bench;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.sevenstars.middleearth.entity.EntitiesME;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.entity.npcs.initializer.NpcEntityInitializer;
import net.sevenstars.middleearth.registries.DynamicRegistriesME;
import net.sevenstars.middleearth.resources.datas.npc_types.NpcType;

import java.util.ArrayList;
import java.util.List;

/**
 * /lotrbench spawn <count> [npc type] [ai]   a grid of Middle-earth NPCs in front of you, facing you (AI off by default, so
 *                                           every renderer sees the same scene; random types when none is given)
 * /lotrbench clear                          remove the NPCs it spawned
 */
public final class BenchCommands {
    private static final String TAG = "lotr_revival_bench";

    private BenchCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lotrbench").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 2000))
                                .executes(c -> spawn(c, null, false))
                                .then(Commands.argument("type", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(types(c.getSource().getLevel()), b))
                                        .executes(c -> spawn(c, ResourceLocationArgument.getId(c, "type"), false))
                                        .then(Commands.argument("ai", BoolArgumentType.bool())
                                                .executes(c -> spawn(c, ResourceLocationArgument.getId(c, "type"), BoolArgumentType.getBool(c, "ai")))))))
                .then(Commands.literal("clear").executes(BenchCommands::clear)));
    }

    private static List<ResourceLocation> types(ServerLevel level) {
        Registry<NpcType> reg = level.registryAccess().registryOrThrow(DynamicRegistriesME.NPC_TYPE);
        return new ArrayList<>(reg.keySet());
    }

    private static int spawn(CommandContext<CommandSourceStack> c, ResourceLocation type, boolean ai) {
        CommandSourceStack src = c.getSource();
        ServerLevel level = src.getLevel();
        int count = IntegerArgumentType.getInteger(c, "count");
        List<ResourceLocation> all = types(level);
        if (type != null && !all.contains(type)) { src.sendFailure(Component.literal("No NPC type " + type)); return 0; }
        if (all.isEmpty()) { src.sendFailure(Component.literal("Middle-earth has no NPC types loaded")); return 0; }
        Vec3 origin = src.getPosition();
        float yaw = src.getRotation().y;
        Vec3 fwd = Vec3.directionFromRotation(0, yaw), right = new Vec3(-fwd.z, 0, fwd.x);
        int cols = Mth.ceil(Math.sqrt(count)), made = 0;
        for (int i = 0; i < count; i++) {
            NpcEntity npc = EntitiesME.NPC.create(level);
            if (npc == null) break;
            int row = i / cols, col = i % cols;
            Vec3 p = origin.add(fwd.scale(4 + row * 1.5)).add(right.scale((col - (cols - 1) / 2.0) * 1.5));
            npc.moveTo(p.x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z)), p.z,
                    yaw + 180, 0);
            npc.setYHeadRot(yaw + 180);
            npc.setYBodyRot(yaw + 180);
            npc.prepareNpcIdentifier(type != null ? type : all.get(level.random.nextInt(all.size())));
            NpcEntityInitializer.initializeNpcEntity(level, npc);
            if (npc.isRemoved()) continue;
            npc.setNoAi(!ai);
            npc.setPersistenceRequired();
            npc.addTag(TAG);
            level.addFreshEntity(npc);
            made++;
        }
        int n = made;
        src.sendSuccess(() -> Component.literal("Spawned " + n + " NPCs. Switch renderer with /lotrrender theirs|vanilla|pulse"), true);
        return made;
    }

    private static int clear(CommandContext<CommandSourceStack> c) {
        int n = 0;
        for (Entity e : c.getSource().getLevel().getAllEntities()) {
            if (e.getTags().contains(TAG)) { e.discard(); n++; }
        }
        int m = n;
        c.getSource().sendSuccess(() -> Component.literal("Removed " + m + " NPCs"), true);
        return n;
    }
}
