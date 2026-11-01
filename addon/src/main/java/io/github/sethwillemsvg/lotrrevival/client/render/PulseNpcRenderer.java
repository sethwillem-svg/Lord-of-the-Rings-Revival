package io.github.sethwillemsvg.lotrrevival.client.render;

import com.arcanc.pulselib.content.renderer.PRenderQueue;
import com.arcanc.pulselib.content.renderer.plan.PGeometryData;
import com.arcanc.pulselib.content.renderer.plan.PInstanceHeader;
import com.arcanc.pulselib.util.PRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import io.github.sethwillemsvg.lotrrevival.client.render.PulseGeometry.Quad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.sevenstars.middleearth.client.renderer.armor.ArmorRenderer;
import net.sevenstars.middleearth.entity.npcs.NpcEntity;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Middle-earth NPCs drawn through PulseLib's render backend: static meshes kept on the GPU (PulseLib's geometry arena), and per
 * NPC only one transform per piece. Posing is the same as the other renderers (NpcBipedModel + our clips).
 *
 *  - Skin: one mesh per (layer, body part), shared by every NPC with that layer, batched across NPCs; faces fully transparent in
 *    a layer are left out. All layers share Middle-earth's character atlas.
 *  - Armour: each armour model's body parts (vanilla's or an item's custom NeoForge model) recorded once at rest, one mesh per
 *    (part, texture), moved with the NPC's posed body parts; layer tints per instance; trims from the trim atlas. Not drawn: the
 *    enchantment glint, and animations a custom armour model plays on its own sub-parts. Armour Middle-earth draws with its own
 *    renderers (most of it: helmet variants, hoods, capes, custom pieces) still goes through those renderers and Minecraft's
 *    buffers, as in their mod.
 *  - Held items: what Minecraft's item renderer draws for an item in hand, recorded once per item look, attached to the posed
 *    hand. Not drawn: glint; items whose look changes every frame (compass, clock) keep their first look.
 */
public class PulseNpcRenderer extends VanillaNpcRenderer {
    private static final Logger LOG = LogUtils.getLogger();
    /** Stacked layers are pushed out a little each (PulseLib batches draws, so draw order can't layer them). */
    private static final float LAYER_GROW = 0.0015f;   // blocks per layer (0.024 px)
    private static final int MAX_LAYERS = 16;
    private static final int HEAD = 0, HAT = 1, BODY = 2, RIGHT_ARM = 3, LEFT_ARM = 4, RIGHT_LEG = 5, LEFT_LEG = 6;
    private static final EquipmentSlot[] ARMOUR_SLOTS = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD};

    private final List<List<Quad>> bodyQuads = new ArrayList<>();
    private final HumanoidArmorModel<NpcEntity> innerArmour, outerArmour;
    private final ItemInHandRenderer itemInHand;

    private final Map<TextureAtlasSprite, PGeometryData[][]> skinMeshes = new HashMap<>();
    private final Map<ModelPart, List<Quad>> armourQuads = new WeakHashMap<>();
    private final Map<ModelPart, Map<Object, PGeometryData>> armourMeshes = new WeakHashMap<>();
    private final Map<ItemKey, List<Piece>> itemPieces = new HashMap<>();
    private final java.util.Set<Class<?>> warned = new java.util.HashSet<>();
    private TextureAtlas atlasSeen;

    private record ItemKey(int stackHash, ItemDisplayContext context, boolean left) {}

    /** One drawable piece of a held item: PulseLib render type, mesh and colour. */
    private record Piece(RenderType type, PGeometryData mesh, int color) {}

    public PulseNpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        for (ModelPart part : new NpcBipedModel().parts) bodyQuads.add(PulseGeometry.capture(part));
        innerArmour = new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        outerArmour = new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        itemInHand = context.getItemInHandRenderer();
    }

    /** Armour and items are PulseLib meshes here, not Minecraft's layers. */
    @Override
    protected boolean vanillaEquipment() {
        return false;
    }

    @Override
    protected RenderLayer<NpcEntity, NpcBipedModel> skinLayer(RenderLayerParent<NpcEntity, NpcBipedModel> parent) {
        return new RenderLayer<>(parent) {
            @Override
            public void render(PoseStack poseStack, MultiBufferSource buffers, int light, NpcEntity entity, float limbSwing,
                               float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                TextureAtlas atlas = NpcSkin.atlas();
                if (atlas != atlasSeen) {   // resources reloaded: sprites, textures and item models are new
                    skinMeshes.clear();
                    armourQuads.clear();
                    armourMeshes.clear();
                    itemPieces.clear();
                    atlasSeen = atlas;
                }
                NpcBipedModel model = getParentModel();
                Matrix4f[] at = new Matrix4f[model.parts.length];
                for (int i = 0; i < at.length; i++) {
                    if (!model.parts[i].visible) continue;
                    poseStack.pushPose();
                    model.parts[i].translateAndRotate(poseStack);
                    at[i] = new Matrix4f(poseStack.last().pose());
                    poseStack.popPose();
                }
                int overlay = getOverlayCoords(entity, 0f);
                skin(entity, atlas, at, light, overlay);
                if (showEquipment(entity)) {
                    armour(entity, at, light, poseStack, buffers);
                    items(entity, model, poseStack, light);
                }
            }
        };
    }

    private static void submit(RenderType type, PGeometryData mesh, Matrix4f m, int color, int light, int overlay) {
        if (mesh != null) PRenderQueue.submitEntityMesh(type, mesh, new PInstanceHeader(m, color, light, overlay));
    }

    // ---- skin ----
    private void skin(NpcEntity entity, TextureAtlas atlas, Matrix4f[] at, int light, int overlay) {
        NpcSkin.Layers layers = NpcSkin.layers(entity, atlas);
        int n = Math.min(layers.sprites().size(), MAX_LAYERS);
        if (n == 0) return;
        RenderType type = PRenderTypes.RenderTypeProvider.trianglesCutout(atlas.location());
        for (int i = 0; i < at.length; i++) {
            if (at[i] == null) continue;
            for (int k = 0; k < n; k++)
                if (layers.drawn(k)) submit(type, skinMesh(layers.sprites().get(k), i, k), at[i], 0xFFFFFFFF, light, overlay);
        }
    }

    private static final PGeometryData NONE = new PGeometryData(java.nio.ByteBuffer.allocate(0), java.nio.ByteBuffer.allocate(0),
            PulseGeometry.STRIDE, 0, PGeometryData.IndexType.UNSIGNED_INT);

    private PGeometryData skinMesh(TextureAtlasSprite sprite, int part, int layer) {
        PGeometryData[][] byPart = skinMeshes.computeIfAbsent(sprite, s -> new PGeometryData[bodyQuads.size()][MAX_LAYERS]);
        if (byPart[part][layer] == null)
            byPart[part][layer] = Objects.requireNonNullElse(PulseGeometry.pack(bodyQuads.get(part), sprite, layer * LAYER_GROW), NONE);
        return byPart[part][layer] == NONE ? null : byPart[part][layer];
    }

    // ---- armour ----
    @SuppressWarnings("unchecked")
    private void armour(NpcEntity entity, Matrix4f[] at, int light, PoseStack poseStack, MultiBufferSource buffers) {
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorItem item) || item.getEquipmentSlot() != slot) continue;
            boolean inner = slot == EquipmentSlot.LEGS;
            // Middle-earth draws most of its armour (helmet variants, hoods, capes, custom chest/legs/boots) with its own
            // renderers: use them as their mod does (Minecraft's buffers, not PulseLib) until those pieces are ported.
            ArmorRenderer own = ArmorRenderer.get(stack.getItem());
            if (own != null) {
                HumanoidArmorModel<NpcEntity> context = inner ? innerArmour : outerArmour;
                getModel().copyPropertiesTo(context);
                own.render(poseStack, buffers, stack, entity, slot, light, (HumanoidModel<LivingEntity>) (HumanoidModel<?>) context);
                continue;
            }
            Model model = ClientHooks.getArmorModel(entity, stack, slot, inner ? innerArmour : outerArmour);
            if (!(model instanceof HumanoidModel<?> armour)) {
                if (warned.add(model.getClass())) LOG.warn("lotr_revival: armour model {} is not a HumanoidModel, not drawn with PulseLib", model.getClass().getName());
                continue;
            }
            ModelPart[] parts = {armour.head, armour.hat, armour.body, armour.rightArm, armour.leftArm, armour.rightLeg, armour.leftLeg};
            int[] shown = switch (slot) {
                case HEAD -> new int[] {HEAD, HAT};
                case CHEST -> new int[] {BODY, RIGHT_ARM, LEFT_ARM};
                case LEGS -> new int[] {BODY, RIGHT_LEG, LEFT_LEG};
                default -> new int[] {RIGHT_LEG, LEFT_LEG};
            };
            Holder<ArmorMaterial> material = item.getMaterial();
            IClientItemExtensions ext = IClientItemExtensions.of(stack);
            int fallback = ext.getDefaultDyeColor(stack);
            List<ArmorMaterial.Layer> materialLayers = material.value().layers();
            for (int li = 0; li < materialLayers.size(); li++) {
                ArmorMaterial.Layer layer = materialLayers.get(li);
                int color = ext.getArmorLayerTintColor(stack, entity, layer, li, fallback);
                if (color == 0) continue;
                ResourceLocation texture = ClientHooks.getArmorTexture(entity, stack, layer, inner, slot);
                RenderType type = PRenderTypes.RenderTypeProvider.trianglesCutout(texture);
                for (int p : shown)
                    if (at[p] != null) submit(type, armourMesh(parts[p], texture, null, li), at[p], color, light, OverlayTexture.NO_OVERLAY);
            }
            ArmorTrim trim = stack.get(DataComponents.TRIM);
            if (trim != null) {
                TextureAtlas trims = Minecraft.getInstance().getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET);
                TextureAtlasSprite sprite = trims.getSprite(inner ? trim.innerTexture(material) : trim.outerTexture(material));
                RenderType type = PRenderTypes.RenderTypeProvider.trianglesCutout(Sheets.ARMOR_TRIMS_SHEET);
                for (int p : shown)
                    if (at[p] != null)
                        submit(type, armourMesh(parts[p], sprite, sprite, materialLayers.size()), at[p], 0xFFFFFFFF, light, OverlayTexture.NO_OVERLAY);
            }
        }
    }

    private PGeometryData armourMesh(ModelPart part, Object textureKey, TextureAtlasSprite sprite, int layer) {
        Map<Object, PGeometryData> meshes = armourMeshes.computeIfAbsent(part, p -> new HashMap<>());
        Object key = List.of(textureKey, layer);
        PGeometryData mesh = meshes.get(key);
        if (mesh == null) {
            List<Quad> quads = armourQuads.computeIfAbsent(part, PulseGeometry::capture);
            mesh = Objects.requireNonNullElse(PulseGeometry.pack(quads, sprite, layer * LAYER_GROW), NONE);
            meshes.put(key, mesh);
        }
        return mesh == NONE ? null : mesh;
    }

    // ---- held items ----
    private void items(NpcEntity entity, NpcBipedModel model, PoseStack poseStack, int light) {
        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        ItemStack left = rightHanded ? entity.getOffhandItem() : entity.getMainHandItem();
        ItemStack right = rightHanded ? entity.getMainHandItem() : entity.getOffhandItem();
        item(entity, model, poseStack, right, HumanoidArm.RIGHT, light);
        item(entity, model, poseStack, left, HumanoidArm.LEFT, light);
    }

    private void item(NpcEntity entity, NpcBipedModel model, PoseStack poseStack, ItemStack stack, HumanoidArm arm, int light) {
        if (stack.isEmpty()) return;
        boolean left = arm == HumanoidArm.LEFT;
        ItemDisplayContext context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        // the hand frame Minecraft's held-item layer uses
        poseStack.pushPose();
        model.translateToHand(arm, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        poseStack.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        poseStack.popPose();
        List<Piece> pieces = itemPieces.computeIfAbsent(new ItemKey(ItemStack.hashItemAndComponents(stack), context, left),
                k -> recordItem(entity, stack, context, left));
        for (Piece piece : pieces) submit(piece.type, piece.mesh, m, piece.color, light, OverlayTexture.NO_OVERLAY);
    }

    /** Draws the item once into a recorder (hand space), then packs one mesh per (texture, colour). */
    private List<Piece> recordItem(NpcEntity entity, ItemStack stack, ItemDisplayContext context, boolean left) {
        PulseGeometry.RecordingBuffers buffers = new PulseGeometry.RecordingBuffers();
        itemInHand.renderItem(entity, stack, context, left, new PoseStack(), buffers, 0xF000F0);
        buffers.flush();
        List<Piece> pieces = new ArrayList<>();
        buffers.byType.forEach((rt, rec) -> {
            String name = rt.toString();
            if (name.contains("glint")) return;
            ResourceLocation texture = PulseGeometry.texture(rt).orElse(null);
            if (texture == null) return;
            RenderType type = name.contains("translucent")
                    ? PRenderTypes.RenderTypeProvider.trianglesTranslucent(texture)
                    : PRenderTypes.RenderTypeProvider.trianglesCutout(texture);
            Map<Integer, List<Quad>> byColor = new LinkedHashMap<>();
            for (Quad q : rec.quads) byColor.computeIfAbsent(q.color(), c -> new ArrayList<>()).add(q);
            byColor.forEach((color, quads) -> {
                PGeometryData mesh = PulseGeometry.pack(quads, null, 0f);
                if (mesh != null) pieces.add(new Piece(type, mesh, color));
            });
        });
        return pieces;
    }
}
