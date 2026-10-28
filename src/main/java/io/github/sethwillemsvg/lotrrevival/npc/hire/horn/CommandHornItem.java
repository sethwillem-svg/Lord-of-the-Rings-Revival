package io.github.sethwillemsvg.lotrrevival.npc.hire.horn;

import io.github.sethwillemsvg.lotrrevival.client.npc.horn.HornScreens;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * The command horn: one item, the mode in a data component. A blank horn opens the mode picker; sneaking opens the
 * squadron rename screen; otherwise blowing takes {@value #USE_TICKS} ticks and then commands the squadron.
 */
public final class CommandHornItem extends Item {
    public static final int USE_TICKS = 40;
    /** Blowing is heard this far (volume 16 carries 256 blocks in vanilla; this is the loud placeholder). */
    private static final float VOLUME = 16.0f;

    public CommandHornItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
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
        if (HornComponents.modeOf(stack) == HornMode.BLANK) {
            if (level.isClientSide) {
                HornScreens.openMode(hand == InteractionHand.OFF_HAND);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide) {
            level.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(),
                    SoundSource.PLAYERS,
                    VOLUME,
                    1.0f);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            HornMode mode = HornComponents.modeOf(stack);
            HornCommands.apply(player, mode, HornComponents.squadronOf(stack));
            if (mode.afterUse() != mode) {
                stack.set(HornComponents.HORN_MODE.get(), mode.afterUse());
            }
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lotrrevival.command_horn.mode", Component.translatable(HornComponents.modeOf(stack).langKey())));
        SquadronTooltip.add(stack, tooltip);
    }
}
