package io.github.sethwillemsvg.lotrrevival.npc.friendlyfire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sethwillemsvg.lotrrevival.core.registry.ModRegistries;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Per-player NPC preferences, the {@code lotrrevival:npc_player_prefs} attachment: the friendly-fire toggle, whether
 * the one-time friendly-fire hint was already shown, and whether the player is told when a hired unit dies. Saved and
 * copied on death.
 */
public record NpcPlayerPrefs(boolean allowFriendlyFire, boolean friendlyFireHintShown, boolean unitDeathMessages) {
    public static final NpcPlayerPrefs DEFAULT = new NpcPlayerPrefs(false, false, true);

    public static final Codec<NpcPlayerPrefs> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.BOOL.optionalFieldOf("allow_friendly_fire", false).forGetter(NpcPlayerPrefs::allowFriendlyFire),
                    Codec.BOOL
                            .optionalFieldOf("friendly_fire_hint_shown", false)
                            .forGetter(NpcPlayerPrefs::friendlyFireHintShown),
                    Codec.BOOL
                            .optionalFieldOf("unit_death_messages", true)
                            .forGetter(NpcPlayerPrefs::unitDeathMessages))
            .apply(instance, NpcPlayerPrefs::new));

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NpcPlayerPrefs>> ATTACHMENT =
            ModRegistries.ATTACHMENT_TYPES.register(
                    "npc_player_prefs",
                    () -> AttachmentType.builder(() -> DEFAULT)
                            .serialize(CODEC)
                            .copyOnDeath()
                            .build());

    /** Touch from {@code NpcMod.init} so the class loads before registration. */
    public static void init() {}

    public static NpcPlayerPrefs get(Player player) {
        return player.getData(ATTACHMENT);
    }

    public static void set(Player player, NpcPlayerPrefs prefs) {
        player.setData(ATTACHMENT, prefs);
    }

    public NpcPlayerPrefs withAllowFriendlyFire(boolean allow) {
        return new NpcPlayerPrefs(allow, friendlyFireHintShown, unitDeathMessages);
    }

    public NpcPlayerPrefs withHintShown() {
        return new NpcPlayerPrefs(allowFriendlyFire, true, unitDeathMessages);
    }

    public NpcPlayerPrefs withUnitDeathMessages(boolean on) {
        return new NpcPlayerPrefs(allowFriendlyFire, friendlyFireHintShown, on);
    }
}
