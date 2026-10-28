package io.github.sethwillemsvg.lotrrevival.core.brewing;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** The barrel item. Carries the brew (data component) and shows it in the tooltip. */
public class BrewingBarrelItem extends BlockItem {
    public BrewingBarrelItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BarrelBrew brew = stack.get(BrewingComponents.BARREL_BREW.get());
        if (brew == null) {
            return;
        }
        Item drink = BuiltInRegistries.ITEM.getOptional(brew.drink()).orElse(null);
        if (drink == null) {
            return;
        }
        DrinkTier tier = DrinkTier.byIndex(brew.tier());
        if (brew.state() == BarrelState.BREWING) {
            tooltip.add(BrewingStatus.brewing(drink, DrinkTier.byIndex(
                    Math.min(DrinkTier.MAX_INDEX, brew.elapsed() / BrewingBarrelBlockEntity.TIER_TICKS))));
        } else {
            tooltip.add(BrewingStatus.full(drink, tier, brew.servings()));
        }
    }
}
