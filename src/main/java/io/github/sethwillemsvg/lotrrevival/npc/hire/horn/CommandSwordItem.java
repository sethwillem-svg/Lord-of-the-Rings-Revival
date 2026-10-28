package io.github.sethwillemsvg.lotrrevival.npc.hire.horn;

import io.github.sethwillemsvg.lotrrevival.client.npc.horn.HornScreens;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * The command sword: a weak sword (1 attack damage, normal attack speed) whose right-click orders the squadron onto
 * what the player aims at, or cancels sword orders when aiming at nothing. Sneaking opens the squadron rename screen.
 */
public final class CommandSwordItem extends Item {
    public CommandSwordItem(Properties properties) {
        super(properties.stacksTo(1).attributes(attributes()));
    }

    /** Attack damage 1 (the player's base alone) and the usual sword attack speed of 1.6. */
    public static ItemAttributeModifiers attributes() {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 0.0, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.4, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                HornScreens.openRename(hand == InteractionHand.OFF_HAND, HornComponents.squadronOf(stack));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            SwordOrders.use(serverPlayer, HornComponents.squadronOf(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        SquadronTooltip.add(stack, tooltip);
    }
}
