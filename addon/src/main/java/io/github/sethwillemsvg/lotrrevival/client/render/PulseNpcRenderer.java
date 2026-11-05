package io.github.sethwillemsvg.lotrrevival.client.render;

import com.arcanc.pulselib.content.model.deformer.gpu.PGpuDeformerBuffers;
import com.arcanc.pulselib.content.renderer.PRenderQueue;
import com.arcanc.pulselib.content.renderer.plan.PGeometryData;
import com.arcanc.pulselib.content.renderer.plan.PInstanceHeader;
import com.arcanc.pulselib.util.PRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import io.github.sethwillemsvg.lotrrevival.client.anim.BowDraw;
import io.github.sethwillemsvg.lotrrevival.client.anim.NpcAnimatable;
import io.github.sethwillemsvg.lotrrevival.client.anim.NpcAnimator;
import io.github.sethwillemsvg.lotrrevival.client.anim.NpcPose;
import io.github.sethwillemsvg.lotrrevival.combat.Hands;
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
import net.minecraft.world.item.CrossbowItem;
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
 *  - Skin: per NPC look, one mesh per body part with all its skin layers in order (the GPU draws a mesh's triangles in order, so
 *    layers at the same depth stack like Minecraft's draw order, with no spacing); faces fully transparent in a layer are left
 *    out; head and hat have an eyes-closed variant for blinking. All layers share Middle-earth's character atlas. Note: PulseLib
 *    keeps every mesh until the world is left or resources reload, so this adds up with the number of NPC looks seen.
 *  - Armour: each armour model's body parts (vanilla's or an item's custom NeoForge model) recorded once at rest, one mesh per
 *    (part, texture), moved with the NPC's posed body parts; layer tints per instance; trims from the trim atlas. Not drawn: the
 *    enchantment glint, and animations a custom armour model plays on its own sub-parts.
 *  - Middle-earth armour (most of it: helmet variants, hoods, capes, custom pieces; drawn by its own ArmorRenderers): what its
 *    renderer draws is recorded once per armour look on a resting armour model, every face tagged with the body part that drew
 *    it (PartCapture, like PulseLib/GeckoLib bones bound to humanoid parts), then replayed on the posed parts. Capes follow the
 *    body parts they hang from; their own walk swing is replaced by our animation.
 *  - Held items: what Minecraft's item renderer draws for an item in hand, recorded once per item look, attached to the posed
 *    hand. Not drawn: glint; items whose look changes every frame (compass, clock) keep their first look. Items with their own
 *    renderer (banners, banner shields) are drawn the normal way: their pattern layers share one surface and need draw order.
 *    Both ways the item is first placed in the arm (WeaponGrip, measured from what HeldItems draws): weapons sized and
 *    seated on the fist, shields strapped to the forearm, face out from its back, bows held upright across it. A bow's look
 *    includes the drawn model its bow clip has reached (client.anim.BowDraw), so each of slack and pulling_0..2 is recorded
 *    once and shown as the clip draws.
 */
public class PulseNpcRenderer extends VanillaNpcRenderer {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int MAX_LAYERS = 16;
    private static final int HEAD = 0, HAT = 1, BODY = 2, RIGHT_ARM = 3, LEFT_ARM = 4, RIGHT_LEG = 5, LEFT_LEG = 6;
    private static final EquipmentSlot[] ARMOUR_SLOTS = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD};

    private final List<List<Quad>> bodyQuads = new ArrayList<>();
    private final HumanoidArmorModel<NpcEntity> innerArmour, outerArmour;
    /** Never posed: Middle-earth armour is recorded on these (at rest), then replayed on the posed body parts. */
    private final HumanoidArmorModel<NpcEntity> innerRest, outerRest;
    private final Map<ArmourKey, List<ArmourPiece>> meArmour = new HashMap<>();
    private final ItemInHandRenderer itemInHand;

    /** Per (body part, layers drawn): all of an NPC look's skin layers on that part, in order, in one mesh. */
    private final Map<List<Object>, PGeometryData> skinMeshes = new HashMap<>();
    private final Map<ModelPart, List<Quad>> armourQuads = new WeakHashMap<>();
    private final Map<ModelPart, Map<Object, PGeometryData>> armourMeshes = new WeakHashMap<>();
    private final Map<ItemKey, List<Piece>> itemPieces = new HashMap<>();
    /** Each held item look's faces in arm space (px), and where it sits (arm space, blocks; NONE_PLACE: as drawn). */
    private final Map<ItemKey, List<float[]>> itemQuads = new HashMap<>();
    private final Map<ItemKey, Matrix4f> itemPlace = new HashMap<>();
    private static final Matrix4f NONE_PLACE = new Matrix4f();
    private final java.util.Set<Class<?>> warned = new java.util.HashSet<>();
    private TextureAtlas atlasSeen;

    /** A held item's look: the stack, the hand, and for a bow or crossbow the drawn model shown (client.anim.BowDraw stage,
     *  0 for anything else). */
    private record ItemKey(int stackHash, ItemDisplayContext context, boolean left, int bowStage) {}

    private record ArmourKey(int stackHash, EquipmentSlot slot) {}

    /** A recorded piece of Middle-earth armour: the body part it follows, render type, mesh and colour. */
    private record ArmourPiece(int part, RenderType type, PGeometryData mesh, int color) {}

    /** One drawable piece of a held item: PulseLib render type, mesh and colour. */
    private record Piece(RenderType type, PGeometryData mesh, int color) {}

    public PulseNpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        ModelPart[] biped = new NpcBipedModel().parts;
        for (int i = 0; i < biped.length; i++) bodyQuads.add(PulseGeometry.slice(PulseGeometry.capture(biped[i]), NpcJoints.cuts(i)));
        innerArmour = new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        innerRest = new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        outerRest = new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
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
                    itemQuads.clear();
                    itemPlace.clear();
                    meArmour.clear();
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
                // elbows, knees and waist bend on the GPU: one upload of each part's joint angles, shared by its meshes
                float[] joints = NpcAnimator.joints(entity);
                PGpuDeformerBuffers.Submission[] bends = new PGpuDeformerBuffers.Submission[at.length];
                for (int i = 0; i < at.length; i++) bends[i] = at[i] == null ? PGpuDeformerBuffers.NONE : NpcJoints.submit(i, joints);
                skin(entity, atlas, at, bends, light, overlay);
                if (showEquipment(entity)) {
                    armour(entity, at, bends, light, poseStack, buffers);
                    items(entity, model, joints, poseStack, buffers, light);
                }
            }
        };
    }

    private static void submit(RenderType type, PGeometryData mesh, Matrix4f m, int color, int light, int overlay) {
        submit(type, mesh, m, color, light, overlay, PGpuDeformerBuffers.NONE);
    }

    private static void submit(RenderType type, PGeometryData mesh, Matrix4f m, int color, int light, int overlay,
                               PGpuDeformerBuffers.Submission bend) {
        if (mesh == null) return;
        PInstanceHeader header = bend.enabled()
                ? new PInstanceHeader(m, color, light, overlay, bend.operationOffset(), bend.valueOffset(), bend.operationCount())
                : new PInstanceHeader(m, color, light, overlay);
        PRenderQueue.submitEntityMesh(type, mesh, header);
    }

    // ---- skin ----
    private void skin(NpcEntity entity, TextureAtlas atlas, Matrix4f[] at, PGpuDeformerBuffers.Submission[] bends, int light, int overlay) {
        NpcSkin.Layers layers = NpcSkin.layers(entity, atlas);
        int n = Math.min(layers.sprites().size(), MAX_LAYERS);
        if (n == 0) return;
        List<TextureAtlasSprite> all = layers.sprites().subList(0, n);
        List<TextureAtlasSprite> blinking = null;   // the head without its eye layer, while blinking
        if (layers.blinking() && layers.eye() >= 0 && layers.eye() < n) {
            blinking = new ArrayList<>(all);
            blinking.remove(layers.eye());
        }
        RenderType type = PRenderTypes.RenderTypeProvider.trianglesCutout(atlas.location());
        for (int i = 0; i < at.length; i++) {
            if (at[i] == null) continue;
            List<TextureAtlasSprite> drawn = blinking != null && (i == HEAD || i == HAT) ? blinking : all;
            submit(type, skinMesh(drawn, i), at[i], 0xFFFFFFFF, light, overlay, bends[i]);
        }
    }

    private static final PGeometryData NONE = new PGeometryData(java.nio.ByteBuffer.allocate(0), java.nio.ByteBuffer.allocate(0),
            PulseGeometry.STRIDE, 0, PGeometryData.IndexType.UNSIGNED_INT);

    private PGeometryData skinMesh(List<TextureAtlasSprite> drawn, int part) {
        PGeometryData mesh = skinMeshes.computeIfAbsent(List.of(part, List.copyOf(drawn)),
                k -> Objects.requireNonNullElse(PulseGeometry.packLayers(bodyQuads.get(part), drawn), NONE));
        return mesh == NONE ? null : mesh;
    }

    // ---- armour ----
    @SuppressWarnings("unchecked")
    private void armour(NpcEntity entity, Matrix4f[] at, PGpuDeformerBuffers.Submission[] bends, int light, PoseStack poseStack,
                        MultiBufferSource buffers) {
        for (EquipmentSlot slot : ARMOUR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorItem item) || item.getEquipmentSlot() != slot) continue;
            boolean inner = slot == EquipmentSlot.LEGS;
            // Middle-earth draws most of its armour (helmet variants, hoods, capes, custom chest/legs/boots) with its own
            // renderers. Record what one draws once per armour look, with each piece tagged by the body part that drew it,
            // and replay it on PulseLib on the posed parts. If the recording is empty, draw it the normal way.
            ArmorRenderer own = ArmorRenderer.get(stack.getItem());
            if (own != null) {
                List<ArmourPiece> pieces = meArmour.computeIfAbsent(new ArmourKey(ItemStack.hashItemAndComponents(stack), slot),
                        k -> recordArmour(own, entity, stack, slot, inner));
                if (pieces.isEmpty()) {
                    HumanoidArmorModel<NpcEntity> context = inner ? innerArmour : outerArmour;
                    getModel().copyPropertiesTo(context);
                    own.render(poseStack, buffers, stack, entity, slot, light, (HumanoidModel<LivingEntity>) (HumanoidModel<?>) context);
                    continue;
                }
                for (ArmourPiece piece : pieces) {
                    Matrix4f m = at[piece.part];
                    if (m != null) submit(piece.type, piece.mesh, m, piece.color, light, OverlayTexture.NO_OVERLAY, bends[piece.part]);
                }
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
                    if (at[p] != null) submit(type, armourMesh(parts[p], p, texture, null, li), at[p], color, light, OverlayTexture.NO_OVERLAY, bends[p]);
            }
            ArmorTrim trim = stack.get(DataComponents.TRIM);
            if (trim != null) {
                TextureAtlas trims = Minecraft.getInstance().getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET);
                TextureAtlasSprite sprite = trims.getSprite(inner ? trim.innerTexture(material) : trim.outerTexture(material));
                RenderType type = PRenderTypes.RenderTypeProvider.trianglesCutout(Sheets.ARMOR_TRIMS_SHEET);
                for (int p : shown)
                    if (at[p] != null)
                        submit(type, armourMesh(parts[p], p, sprite, sprite, materialLayers.size()), at[p], 0xFFFFFFFF, light, OverlayTexture.NO_OVERLAY, bends[p]);
            }
        }
    }

    private PGeometryData armourMesh(ModelPart part, int index, Object textureKey, TextureAtlasSprite sprite, int layer) {
        Map<Object, PGeometryData> meshes = armourMeshes.computeIfAbsent(part, p -> new HashMap<>());
        Object key = List.of(textureKey, layer);
        PGeometryData mesh = meshes.get(key);
        if (mesh == null) {
            List<Quad> quads = armourQuads.computeIfAbsent(part, q -> PulseGeometry.slice(PulseGeometry.capture(q), NpcJoints.cuts(index)));
            mesh = Objects.requireNonNullElse(PulseGeometry.pack(quads, sprite), NONE);
            meshes.put(key, mesh);
        }
        return mesh == NONE ? null : mesh;
    }

    /**
     * Draws a Middle-earth armour piece once on the resting armour model into a recorder, with PartCapture tagging every face
     * by the body part that drew it (in that part's space). Packs one mesh per (body part, texture, colour, draw); later draws
     * of the same piece (dye overlays) are pushed out a little so they stay on top. Faces drawn outside a body part follow the
     * body.
     */
    @SuppressWarnings("unchecked")
    private List<ArmourPiece> recordArmour(ArmorRenderer own, NpcEntity entity, ItemStack stack, EquipmentSlot slot, boolean inner) {
        PulseGeometry.RecordingBuffers buffers = new PulseGeometry.RecordingBuffers();
        HumanoidArmorModel<NpcEntity> rest = inner ? innerRest : outerRest;
        // a model's state normally comes from the entity it is drawn for; this one is never drawn for one, and Minecraft models
        // start as "young" (baby scale and offset), which Middle-earth's renderers copy onto their armour models
        rest.young = false;
        rest.riding = false;
        rest.crouching = false;
        rest.attackTime = 0f;
        PartCapture.begin();
        try {
            own.render(new PoseStack(), buffers, stack, entity, slot, 0xF000F0, (HumanoidModel<LivingEntity>) (HumanoidModel<?>) rest);
        } catch (RuntimeException e) {
            LOG.warn("lotr_revival: could not record armour {} for PulseLib", stack.getItem(), e);
            return List.of();
        } finally {
            PartCapture.end();
        }
        buffers.flush();
        List<ArmourPiece> pieces = new ArrayList<>();
        buffers.byType.forEach((rt, rec) -> {
            String name = rt.toString();
            if (name.contains("glint")) return;
            ResourceLocation texture = PulseGeometry.texture(rt).orElse(null);
            if (texture == null) return;
            RenderType type = name.contains("translucent")
                    ? PRenderTypes.RenderTypeProvider.trianglesTranslucent(texture)
                    : PRenderTypes.RenderTypeProvider.trianglesCutout(texture);
            Map<List<Integer>, List<Quad>> groups = new LinkedHashMap<>();
            for (Quad q : rec.quads)
                groups.computeIfAbsent(List.of(q.part() < 0 ? BODY : q.part(), q.color(), Math.max(0, q.draw())), g -> new ArrayList<>()).add(q);
            groups.forEach((g, quads) -> {
                PGeometryData mesh = PulseGeometry.pack(PulseGeometry.slice(quads, NpcJoints.cuts(g.get(0))), null);
                if (mesh != null) pieces.add(new ArmourPiece(g.get(0), type, mesh, g.get(1)));
            });
        });
        if (DEBUG) {
            StringBuilder sb = new StringBuilder();
            buffers.byType.forEach((rt, rec) -> {
                int[] perPart = new int[8];
                org.joml.Vector3f min = new org.joml.Vector3f(Float.MAX_VALUE), max = new org.joml.Vector3f(-Float.MAX_VALUE);
                java.util.Set<Integer> colors = new java.util.HashSet<>();
                for (Quad q : rec.quads) {
                    perPart[q.part() + 1]++;
                    colors.add(q.color());
                    for (org.joml.Vector3f p : q.pos()) { min.min(p); max.max(p); }
                }
                sb.append("\n    ").append(rt.toString(), 0, Math.min(160, rt.toString().length()))
                        .append("\n      texture=").append(PulseGeometry.texture(rt).orElse(null))
                        .append(" quads per part (none,head,hat,body,rArm,lArm,rLeg,lLeg)=").append(java.util.Arrays.toString(perPart))
                        .append(" bounds=").append(min).append("..").append(max)
                        .append(" colors=").append(colors.stream().map(Integer::toHexString).toList());
            });
            LOG.info("lotr_revival: recorded {} {}: {} pieces{}", stack.getItem(), slot, pieces.size(), sb);
        }
        return pieces;
    }

    private static final boolean DEBUG = Boolean.getBoolean("lotr_revival.debug") || Integer.getInteger("lotr_revival.autotest", 0) > 0;

    // ---- held items ----
    private void items(NpcEntity entity, NpcBipedModel model, float[] joints, PoseStack poseStack, MultiBufferSource buffers, int light) {
        boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
        ItemStack main = Hands.weapon(entity), other = Hands.other(entity);   // a shield given as the main item goes to the off arm
        ItemStack left = rightHanded ? other : main;
        ItemStack right = rightHanded ? main : other;
        item(entity, model, joints, poseStack, buffers, right, HumanoidArm.RIGHT, light);
        item(entity, model, joints, poseStack, buffers, left, HumanoidArm.LEFT, light);
    }

    private void item(NpcEntity entity, NpcBipedModel model, float[] joints, PoseStack poseStack, MultiBufferSource buffers,
                      ItemStack stack, HumanoidArm arm, int light) {
        if (stack.isEmpty()) return;
        boolean left = arm == HumanoidArm.LEFT;
        ItemDisplayContext context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        poseStack.pushPose();
        NpcAnimatable animatable = NpcAnimatable.get(entity);
        NpcPose pose = animatable == null ? null : animatable.lastPose();
        if (pose != null) {
            // the weapon bone carries the arm, the elbow and the clip's nudge; the arm's rest frame then seats the item
            poseStack.mulPose(pose.weapon(left));
            poseStack.translate((left ? 5f : -5f) / 16f, 2f / 16f, 0f);
        } else {
            model.translateToHand(arm, poseStack);   // the hand frame Minecraft's held-item layer uses
        }
        // a bow shows the model its clip's draw has reached (BowDraw, from the clip's time: not the entity's using-item state);
        // a crossbow its real reload (the server times it), which keys the recording so the reload does not freeze
        String cls = WeaponGrip.classOf(stack);
        int bowStage = animatable != null && WeaponGrip.isBow(cls) ? animatable.bowStage()
                : "crossbow".equals(cls) ? crossbowStage(entity, stack) : BowDraw.SLACK;
        ItemKey key = new ItemKey(ItemStack.hashItemAndComponents(stack), context, left, bowStage);
        Matrix4f place = place(entity, stack, key);   // a weapon at its class's length, handle on the fist; a shield strapped to the forearm (WeaponGrip)
        if (place != NONE_PLACE) poseStack.mulPose(place);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        poseStack.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
        // Items with their own renderer (banners, banner shields, ...) paint several layers onto one surface, which only
        // works in draw order: draw those the normal way. Ordinary item models go to PulseLib.
        if (Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, entity.getId()).isCustomRenderer()) {
            HeldItems.render(itemInHand, entity, stack, context, left, poseStack, buffers, light, bowStage);
            poseStack.popPose();
            return;
        }
        Matrix4f m = new Matrix4f(poseStack.last().pose());
        poseStack.popPose();
        List<Piece> pieces = itemPieces.computeIfAbsent(key, k -> recordItem(entity, stack, context, left, k.bowStage()));
        for (Piece piece : pieces) submit(piece.type, piece.mesh, m, piece.color, light, OverlayTexture.NO_OVERLAY);
    }

    /** A crossbow's reload stage now (BowDraw.crossbowStage), from the entity's use of it, as Minecraft's predicates read it. */
    private static int crossbowStage(NpcEntity entity, ItemStack stack) {
        boolean reloading = entity.isUsingItem() && entity.getUseItem() == stack;
        float pull = reloading ? (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / (float) CrossbowItem.getChargeDuration(stack, entity) : 0f;
        return BowDraw.crossbowStage(reloading, CrossbowItem.isCharged(stack), pull);
    }

    /** Where a held item sits in the arm (WeaponGrip), cached per item look. A bow is measured on its slack model for every
     *  stage, so the drawn models (their limbs bent, an arrow across the grip) sit where the slack bow does. */
    private Matrix4f place(NpcEntity entity, ItemStack stack, ItemKey key) {
        return itemPlace.computeIfAbsent(key, k -> {
            ItemKey measured = new ItemKey(k.stackHash(), k.context(), k.left(), BowDraw.SLACK);
            List<float[]> quads = itemQuads.computeIfAbsent(measured, kk -> armQuads(entity, stack, kk.context(), kk.left(), kk.bowStage()));
            Matrix4f grip = WeaponGrip.place(WeaponGrip.classOf(stack), key.left(), quads);
            return grip == null ? NONE_PLACE : WeaponGrip.toBlocks(grip);
        });
    }

    /** The item's faces as Minecraft's held-item layer places them in the arm (arm space, px; x, y, z per vertex). */
    private List<float[]> armQuads(NpcEntity entity, ItemStack stack, ItemDisplayContext context, boolean left, int bowStage) {
        PoseStack ps = new PoseStack();
        ps.mulPose(Axis.XP.rotationDegrees(-90f));
        ps.mulPose(Axis.YP.rotationDegrees(180f));
        ps.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
        PulseGeometry.RecordingBuffers buffers = new PulseGeometry.RecordingBuffers();
        HeldItems.render(itemInHand, entity, stack, context, left, ps, buffers, 0xF000F0, bowStage);
        buffers.flush();
        List<float[]> out = new ArrayList<>();
        buffers.byType.forEach((rt, rec) -> {
            if (rt.toString().contains("glint")) return;
            for (Quad q : rec.quads) {
                float[] face = new float[q.pos().length * 3];
                for (int k = 0; k < q.pos().length; k++) {
                    org.joml.Vector3f p = q.pos()[k];
                    face[k * 3] = p.x * 16f; face[k * 3 + 1] = p.y * 16f; face[k * 3 + 2] = p.z * 16f;
                }
                out.add(face);
            }
        });
        return out;
    }

    /** Draws the item once into a recorder (hand space), then packs one mesh per (texture, colour). */
    private List<Piece> recordItem(NpcEntity entity, ItemStack stack, ItemDisplayContext context, boolean left, int bowStage) {
        PulseGeometry.RecordingBuffers buffers = new PulseGeometry.RecordingBuffers();
        HeldItems.render(itemInHand, entity, stack, context, left, new PoseStack(), buffers, 0xF000F0, bowStage);
        buffers.flush();
        List<Piece> pieces = new ArrayList<>();
        buffers.byType.forEach((rt, rec) -> {
            String name = rt.toString();
            if (name.contains("glint")) return;
            ResourceLocation texture = PulseGeometry.texture(rt).orElse(null);
            if (texture == null) return;
            // cutout even where Minecraft says "translucent": held items use item_entity_translucent_cull although almost all
            // are opaque, and PulseLib's translucent path (weighted OIT, four layers) cost about 7-10% of the frame in Spark
            RenderType type = PRenderTypes.RenderTypeProvider.trianglesCutout(texture);
            Map<Integer, List<Quad>> byColor = new LinkedHashMap<>();
            for (Quad q : rec.quads) byColor.computeIfAbsent(q.color(), c -> new ArrayList<>()).add(q);
            byColor.forEach((color, quads) -> {
                PGeometryData mesh = PulseGeometry.pack(quads, null);
                if (mesh != null) pieces.add(new Piece(type, mesh, color));
            });
        });
        return pieces;
    }
}
