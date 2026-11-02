package io.github.sethwillemsvg.lotrrevival.client.anim;

import com.arcanc.pulselib.api.plugin.IPulseClientPlugin;
import com.arcanc.pulselib.api.plugin.PulsePlugin;
import com.arcanc.pulselib.api.registration.PModelRegistrar;
import com.arcanc.pulselib.content.renderer.modelData.PModelData;
import com.arcanc.pulselib.data.gecko.PGeckoModelLoader;
import io.github.sethwillemsvg.lotrrevival.LotrRevival;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers the NPC rig with PulseLib: one GeckoLib model per race (assets/lotr_revival/geckolib/models/entity/
 * npc_biped_<race>.geo.json) with that race's clips (geckolib/animations/entity/npc_biped_<race>.animation.json), both
 * written by tools/anim-viewer/build_npc.js. PulseLib registers its GeckoLib loader itself.
 */
@PulsePlugin(PulsePlugin.Side.CLIENT)
public final class PulseClientPlugin implements IPulseClientPlugin {
    public static final List<String> RACES = List.of("man", "elf", "dwarf", "hobbit", "orc");
    public static final Map<String, PModelData> MODELS = new LinkedHashMap<>();

    static {
        for (String race : RACES) MODELS.put(race, PModelData.entity(LotrRevival.id("npc_biped_" + race), PGeckoModelLoader.INSTANCE.id()));
    }

    @Override
    public ResourceLocation getPluginId() {
        return LotrRevival.id("npc");
    }

    @Override
    public void registerModels(PModelRegistrar registrar) {
        MODELS.values().forEach(registrar::model);
    }
}
