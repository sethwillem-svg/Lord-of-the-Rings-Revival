package io.github.sethwillemsvg.lotrrevival.world.waypoint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.LOTRRevival;
import io.github.sethwillemsvg.lotrrevival.core.faction.Faction;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModDataRegistries;
import io.github.sethwillemsvg.lotrrevival.world.MiddleEarthMap;
import io.github.sethwillemsvg.lotrrevival.world.WorldScale;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * A fixed waypoint ({@code lotrrevival:waypoint} registry).
 *
 * @param name display name
 * @param lore short description
 * @param u heightmap pixel x (64 blocks per pixel at Legacy scale, see {@code WorldScale})
 * @param v heightmap pixel y
 * @param region id of the {@code waypoint_region} it belongs to
 * @param faction owning faction, if any: the waypoint needs alignment of 0 or more with it
 * @param hidden not listed at all until its region is unlocked
 */
public record Waypoint(
        Component name,
        Component lore,
        double u,
        double v,
        ResourceLocation region,
        Optional<ResourceLocation> faction,
        boolean hidden) {
    public static final Codec<Waypoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ComponentSerialization.CODEC.fieldOf("name").forGetter(Waypoint::name),
                    ComponentSerialization.CODEC.fieldOf("lore").forGetter(Waypoint::lore),
                    Codec.DOUBLE.listOf(2, 2).fieldOf("pos").forGetter(w -> List.of(w.u(), w.v())),
                    ResourceLocation.CODEC.fieldOf("region").forGetter(Waypoint::region),
                    ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(Waypoint::faction),
                    Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Waypoint::hidden))
            .apply(i, (name, lore, pos, region, faction, hidden) ->
                    new Waypoint(name, lore, pos.get(0), pos.get(1), region, faction, hidden)));

    public Optional<ResourceKey<Faction>> factionKey() {
        return faction.map(id -> ResourceKey.create(ModDataRegistries.FACTION, id));
    }

    /** World block x of the waypoint at the running world's scale. */
    public double blockX() {
        return MiddleEarthMap.blockX(u);
    }

    /** World block z of the waypoint at the running world's scale. */
    public double blockZ() {
        return MiddleEarthMap.blockZ(v);
    }

    /** World block x of the waypoint at {@code scale}. */
    public double blockX(WorldScale scale) {
        return MiddleEarthMap.blockX(u, scale);
    }

    public double blockZ(WorldScale scale) {
        return MiddleEarthMap.blockZ(v, scale);
    }

    /** Resolves an id typed in a command: a bare {@code shire} means {@code lotrrevival:shire}. Null if malformed. */
    @Nullable
    public static ResourceLocation modId(String text) {
        ResourceLocation parsed = ResourceLocation.tryParse(text);
        if (parsed == null) {
            return null;
        }
        return text.indexOf(':') >= 0 ? parsed : LOTRRevival.id(parsed.getPath());
    }
}
