package org.Marj4n.smooth_quest_gacha;

import org.Marj4n.smooth_quest_gacha.config.GachaConfigManager;
import org.Marj4n.smooth_quest_gacha.integration.ftbquests.FTBQuestsIntegration;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmoothQuestGacha implements ModInitializer {

    public static final String MOD_ID =
            "smooth_quest_gacha";

    public static final Logger LOGGER =
            LoggerFactory.getLogger(
                    "Smooth Quest Gacha"
            );

    @Override
    public void onInitialize() {

        GachaConfigManager.load();

        FTBQuestsIntegration.register();

        LOGGER.info(
                "Smooth Quest Gacha initialized!"
        );
    }
}