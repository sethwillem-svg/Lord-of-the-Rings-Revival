package io.github.sethwillemsvg.lotrrevival.progression.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

/** Fires on armour changes. Condition: required {@code set}, an item tag; matches when all four slots hold members. */
public final class WearArmorSetTrigger extends SimpleCriterionTrigger<WearArmorSetTrigger.TriggerInstance> {
    private static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player) {
        trigger(player, instance -> instance.matches(player));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, TagKey<Item> set)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                        EntityPredicate.ADVANCEMENT_CODEC
                                .optionalFieldOf("player")
                                .forGetter(TriggerInstance::player),
                        TagKey.codec(Registries.ITEM).fieldOf("set").forGetter(TriggerInstance::set))
                .apply(i, TriggerInstance::new));

        public boolean matches(ServerPlayer wearer) {
            for (EquipmentSlot slot : ARMOR) {
                if (!wearer.getItemBySlot(slot).is(set)) {
                    return false;
                }
            }
            return true;
        }
    }
}
