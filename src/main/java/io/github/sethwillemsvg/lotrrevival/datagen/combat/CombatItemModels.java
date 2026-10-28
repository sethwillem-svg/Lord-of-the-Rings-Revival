package io.github.sethwillemsvg.lotrrevival.datagen.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearCatalog;
import io.github.sethwillemsvg.lotrrevival.core.combat.GearEntry;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * Item models for every {@link GearEntry}: handheld weapons and tools, a larger handheld for pikes
 * and lances, flat armour and slings, and pulling variants for bows and crossbows. Textures are
 * {@code item/<id>} (plus {@code _pulling_0..2}, {@code _arrow}, {@code _firework} for crossbows).
 */
final class CombatItemModels extends ItemModelProvider {
    /** Pike and lance models are drawn this much larger than a vanilla handheld item. */
    private static final float WIDE_SCALE = 1.5f;

    CombatItemModels(PackOutput output, ExistingFileHelper helper) {
        super(output, LOTRRevival.MOD_ID, helper);
    }

    @Override
    protected void registerModels() {
        wideParent();
        for (GearEntry entry : GearCatalog.entries()) {
            ResourceLocation texture = modLoc("item/" + entry.id());
            switch (entry.model()) {
                case HANDHELD -> withExistingParent(entry.id(), mcLoc("item/handheld")).texture("layer0", texture);
                case HANDHELD_WIDE -> getBuilder(entry.id())
                        .parent(new ModelFile.UncheckedModelFile(modLoc("item/handheld_wide")))
                        .texture("layer0", texture);
                case GENERATED -> withExistingParent(entry.id(), mcLoc("item/generated")).texture("layer0", texture);
                case BOW -> bow(entry.id(), texture);
                case CROSSBOW -> crossbow(entry.id(), texture);
            }
        }
    }

    private void bow(String id, ResourceLocation texture) {
        ItemModelBuilder base = withExistingParent(id, mcLoc("item/bow")).texture("layer0", texture);
        for (int i = 0; i < 3; i++) {
            withExistingParent(id + "_pulling_" + i, mcLoc("item/bow")).texture("layer0", modLoc("item/" + id + "_pulling_" + i));
        }
        base.override().predicate(mcLoc("pulling"), 1).model(sub(id + "_pulling_0")).end();
        base.override().predicate(mcLoc("pulling"), 1).predicate(mcLoc("pull"), 0.65f).model(sub(id + "_pulling_1")).end();
        base.override().predicate(mcLoc("pulling"), 1).predicate(mcLoc("pull"), 0.9f).model(sub(id + "_pulling_2")).end();
    }

    private void crossbow(String id, ResourceLocation texture) {
        ItemModelBuilder base = withExistingParent(id, mcLoc("item/crossbow")).texture("layer0", texture);
        for (int i = 0; i < 3; i++) {
            withExistingParent(id + "_pulling_" + i, mcLoc("item/crossbow")).texture("layer0", modLoc("item/" + id + "_pulling_" + i));
        }
        withExistingParent(id + "_arrow", mcLoc("item/crossbow")).texture("layer0", modLoc("item/" + id + "_arrow"));
        withExistingParent(id + "_firework", mcLoc("item/crossbow")).texture("layer0", modLoc("item/" + id + "_firework"));
        base.override().predicate(mcLoc("pulling"), 1).model(sub(id + "_pulling_0")).end();
        base.override().predicate(mcLoc("pulling"), 1).predicate(mcLoc("pull"), 0.58f).model(sub(id + "_pulling_1")).end();
        base.override().predicate(mcLoc("pulling"), 1).predicate(mcLoc("pull"), 1.0f).model(sub(id + "_pulling_2")).end();
        base.override().predicate(mcLoc("charged"), 1).model(sub(id + "_arrow")).end();
        base.override().predicate(mcLoc("charged"), 1).predicate(mcLoc("firework"), 1).model(sub(id + "_firework")).end();
    }

    private ModelFile sub(String path) {
        return new ModelFile.UncheckedModelFile(modLoc("item/" + path));
    }

    /** {@code lotrrevival:item/handheld_wide}: vanilla handheld with larger in-hand transforms. */
    private void wideParent() {
        ItemModelBuilder wide = getBuilder("item/handheld_wide").parent(new ModelFile.UncheckedModelFile(mcLoc("item/handheld")));
        float third = 0.85f * WIDE_SCALE;
        float first = 0.68f * WIDE_SCALE;
        wide.transforms()
                .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                .rotation(0, -90, 55).translation(0, 4.0f, 0.5f).scale(third).end()
                .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                .rotation(0, 90, -55).translation(0, 4.0f, 0.5f).scale(third).end()
                .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                .rotation(0, -90, 25).translation(1.13f, 3.2f, 1.13f).scale(first).end()
                .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                .rotation(0, 90, -25).translation(1.13f, 3.2f, 1.13f).scale(first).end()
                .end();
    }

    @Override
    public String getName() {
        return "Combat item models";
    }
}
