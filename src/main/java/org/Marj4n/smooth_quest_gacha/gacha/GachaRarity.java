package org.Marj4n.smooth_quest_gacha.gacha;

public enum GachaRarity {

    COMMON("Common"),
    UNCOMMON("Uncommon"),
    RARE("Rare"),
    EPIC("Epic"),
    LEGENDARY("Legendary");

    private final String displayName;

    GachaRarity(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}