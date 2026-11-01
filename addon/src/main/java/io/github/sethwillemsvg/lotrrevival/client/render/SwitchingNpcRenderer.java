package io.github.sethwillemsvg.lotrrevival.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import net.sevenstars.middleearth.entity.npcs.renderer.NpcEntityRenderer;

import java.util.Locale;

/** Draws Middle-earth NPCs with one of three renderers, switchable in game with /lotrrender (for the benchmark). */
public class SwitchingNpcRenderer extends EntityRenderer<NpcEntity> {
    public enum Mode {
        /** Middle-earth's own renderer: skin layers plus 3D hair, ears, nose, feet, armour and held items. */
        THEIRS,
        /** Our skin layers through Minecraft's CPU model path, plus armour and held items. */
        VANILLA,
        /** The same skin layers, armour and held items as VANILLA, drawn through PulseLib's instanced GPU path. */
        PULSE;

        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    public static Mode mode = Mode.PULSE;

    private final NpcEntityRenderer theirs;
    private final VanillaNpcRenderer vanilla;
    private final PulseNpcRenderer pulse;

    public SwitchingNpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        theirs = new NpcEntityRenderer(context);
        vanilla = new VanillaNpcRenderer(context);
        pulse = new PulseNpcRenderer(context);
        shadowRadius = 0.5f;
    }

    private EntityRenderer<NpcEntity> current() {
        return switch (mode) {
            case THEIRS -> theirs;
            case VANILLA -> vanilla;
            case PULSE -> pulse;
        };
    }

    @Override
    public void render(NpcEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        current().render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public boolean shouldRender(NpcEntity entity, Frustum frustum, double x, double y, double z) {
        return current().shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public Vec3 getRenderOffset(NpcEntity entity, float partialTick) {
        return current().getRenderOffset(entity, partialTick);
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity entity) {
        return current().getTextureLocation(entity);
    }
}
