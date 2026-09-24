package org.Marj4n.smooth_quest_gacha.integration.ftbquests;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class FTBQuestsIntegration {

    public static RewardType GACHA_REWARD;

    private FTBQuestsIntegration() {
    }

    public static void register() {

        GACHA_REWARD = RewardTypes.register(
                new Identifier(
                        "smooth_quest_gacha",
                        "gacha"
                ),

                GachaReward::new,

                () -> Icon.getIcon(
                        "minecraft:item/ender_eye"
                )
        );

        GACHA_REWARD.setDisplayName(
                Text.literal("Smooth Gacha")
        );
    }
}