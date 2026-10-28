package io.github.sethwillemsvg.lotrrevival.progression.quest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Mutable pile of rewards for one completion. Reward types and {@code QuestCompletedEvent} listeners add to it;
 * {@link Quests} applies the result afterwards.
 */
public final class RewardBuilder {
    private float alignment;
    private int coins;
    private final List<ItemStack> items = new ArrayList<>();
    private boolean hired;

    public RewardBuilder(float alignment, int coins) {
        this.alignment = alignment;
        this.coins = coins;
    }

    public float alignment() {
        return alignment;
    }

    public int coins() {
        return coins;
    }

    public List<ItemStack> items() {
        return items;
    }

    public boolean hired() {
        return hired;
    }

    public void addAlignment(float amount) {
        alignment += amount;
    }

    public void addCoins(int amount) {
        coins += amount;
    }

    public void addItem(ItemStack stack) {
        if (!stack.isEmpty()) {
            items.add(stack.copy());
        }
    }

    public void setHired(boolean hired) {
        this.hired = hired;
    }
}
