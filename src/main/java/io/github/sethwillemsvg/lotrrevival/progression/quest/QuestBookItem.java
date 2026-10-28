package io.github.sethwillemsvg.lotrrevival.progression.quest;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;

/**
 * The quest book. Using it opens the quest log on the client. The screen lives in client code, so this common
 * class only calls a hook that the client sets up; a dedicated server never touches client classes.
 */
public class QuestBookItem extends Item {
    @Nullable
    private static volatile Runnable clientOpener;

    public QuestBookItem(Properties properties) {
        super(properties);
    }

    /** Called from client setup only. */
    public static void setClientOpener(Runnable opener) {
        clientOpener = opener;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            Runnable opener = clientOpener;
            if (opener != null) {
                opener.run();
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
