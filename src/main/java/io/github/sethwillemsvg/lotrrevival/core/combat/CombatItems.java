package io.github.sethwillemsvg.lotrrevival.core.combat;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.GeneralGear;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.GondorGear;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.MordorGear;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.RangedGear;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.RangerGear;
import io.github.sethwillemsvg.lotrrevival.core.combat.gear.ShireBreeGear;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Combat foundation entry point, called once from {@code CoreMod.init}: runs every set holder (which
 * create their gear through {@link Gear}), freezes the {@link GearCatalog}, registers the two
 * creative tabs and the lance slowdown.
 */
public final class CombatItems {
    private CombatItems() {}

    public static final String COMBAT_TAB_ID = "combat";
    public static final String MATERIALS_TAB_ID = "materials";

    /** {@code lotrrevival:combat}: all catalog gear in catalog order. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COMBAT_TAB =
            ModRegistries.CREATIVE_TABS.register(COMBAT_TAB_ID, () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.lotrrevival." + COMBAT_TAB_ID))
                    .icon(() -> GearCatalog.entries().isEmpty()
                            ? new ItemStack(Items.IRON_SWORD)
                            : new ItemStack(GearCatalog.entries().get(0).item().get()))
                    .displayItems((params, output) -> {
                        for (GearEntry entry : GearCatalog.entries()) {
                            output.accept(entry.item().get());
                        }
                    })
                    .build());

    /**
     * {@code lotrrevival:materials}: metals, ores, forges, tables, poison. Empty here; other tickets
     * add to it from a {@code BuildCreativeModeTabContentsEvent} handler checking {@link #MATERIALS_TAB_KEY}.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MATERIALS_TAB =
            ModRegistries.CREATIVE_TABS.register(MATERIALS_TAB_ID, () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.lotrrevival." + MATERIALS_TAB_ID))
                    .icon(() -> new ItemStack(Items.IRON_INGOT))
                    .build());

    public static final ResourceKey<CreativeModeTab> COMBAT_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, LOTRRevival.id(COMBAT_TAB_ID));
    public static final ResourceKey<CreativeModeTab> MATERIALS_TAB_KEY =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, LOTRRevival.id(MATERIALS_TAB_ID));

    public static void init(IEventBus modBus) {
        // Order is catalog and creative-tab order.
        GeneralGear.init();
        ShireBreeGear.init();
        RangerGear.init();
        GondorGear.init();
        MordorGear.init();
        RangedGear.init();
        GearCatalog.freeze();
        NeoForge.EVENT_BUS.register(LanceSlow.class);
        io.github.sethwillemsvg.lotrrevival.core.combat.throwing.ThrowingEntities.init();
        io.github.sethwillemsvg.lotrrevival.core.combat.poison.PoisonItems.init(modBus);
    }
}
