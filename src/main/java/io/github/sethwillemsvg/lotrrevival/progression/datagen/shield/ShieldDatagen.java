package io.github.sethwillemsvg.lotrrevival.progression.datagen.shield;

import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.datagen.ModLangProvider;
import io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldDefinition;
import io.github.sethwillemsvg.lotrrevival.progression.shield.ShieldUnlock;
import java.util.List;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Datagen for shields: the four achievement shields and their lang. Textures live in assets (hand-made). */
public final class ShieldDatagen {
    /** One achievement shield: path, achievements needed, English name. */
    private record Row(String path, int achievements, String name) {
        ResourceLocation id() {
            return LOTRRevival.id(path);
        }

        ShieldDefinition toDefinition(int sort) {
            return new ShieldDefinition(
                    new ShieldUnlock.AchievementCount(achievements),
                    LOTRRevival.id("textures/entity/shield/" + path + ".png"),
                    false,
                    sort);
        }
    }

    private static final List<Row> ROWS = List.of(
            new Row("wanderers_buckler", 25, "Wanderer's Buckler"),
            new Row("lorekeepers_roundel", 50, "Lorekeeper's Roundel"),
            new Row("vanguard_bulwark", 100, "Vanguard Bulwark"),
            new Row("high_kings_aegis", 200, "High King's Aegis"));

    private ShieldDatagen() {}

    /** Adds the {@code lotrrevival:shield} entries to the shared registry builder. */
    public static void addRegistries(RegistrySetBuilder registries) {
        registries.add(ShieldDefinition.REGISTRY, context -> {
            for (int n = 0; n < ROWS.size(); n++) {
                Row row = ROWS.get(n);
                context.register(ResourceKey.create(ShieldDefinition.REGISTRY, row.id()), row.toDefinition(n));
            }
        });
    }

    /** Adds shield names and the strings of the Shields page. */
    public static void registerLang() {
        ModLangProvider.contribute(lang -> {
            for (Row row : ROWS) {
                lang.add(ShieldDefinition.nameKey(row.id()), row.name());
            }
            lang.add("gui.lotrrevival.menu.shields.header", "Shields");
            lang.add("gui.lotrrevival.menu.shields.none", "No shields to show.");
            lang.add("gui.lotrrevival.menu.shields.select", "Select");
            lang.add("gui.lotrrevival.menu.shields.remove", "Remove");
            lang.add("gui.lotrrevival.menu.shields.previous", "<");
            lang.add("gui.lotrrevival.menu.shields.next", ">");
            lang.add("gui.lotrrevival.menu.shields.counter", "%1$s / %2$s");
            lang.add("gui.lotrrevival.menu.shields.state.selected", "Selected");
            lang.add("gui.lotrrevival.menu.shields.state.unlocked", "Unlocked");
            lang.add("gui.lotrrevival.menu.shields.state.locked", "Locked");
            lang.add("gui.lotrrevival.menu.shields.current", "Current: %s");
            lang.add("gui.lotrrevival.menu.shields.current.none", "None");
            lang.add("gui.lotrrevival.menu.shields.lock.achievement_count", "Earn %s achievements");
            lang.add("gui.lotrrevival.menu.shields.lock.reserved", "Not available yet");
        });
    }
}
