package org.Marj4n.smooth_quest_gacha.gacha;

import net.minecraft.item.ItemStack;

public class GachaEntry {

    private final ItemStack item;
    private final GachaRarity rarity;
    private final int weight;

    public GachaEntry(
            ItemStack item,
            GachaRarity rarity,
            int weight
    ) {
        this.item = item;
        this.rarity = rarity;
        this.weight = Math.max(1, weight);
    }

    public ItemStack getItem() {
        return item;
    }

    public GachaRarity getRarity() {
        return rarity;
    }

    public int getWeight() {
        return weight;
    }
}