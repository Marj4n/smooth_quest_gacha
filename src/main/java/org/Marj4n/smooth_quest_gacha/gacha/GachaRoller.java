package org.Marj4n.smooth_quest_gacha.gacha;

import net.minecraft.util.math.random.Random;

public final class GachaRoller {

    private GachaRoller() {
    }

    public static GachaEntry roll(
            GachaPool pool,
            Random random
    ) {

        if (pool == null || pool.isEmpty()) {
            return null;
        }

        int totalWeight = pool.getTotalWeight();

        if (totalWeight <= 0) {
            return null;
        }

        int roll = random.nextInt(totalWeight);

        int current = 0;

        for (GachaEntry entry : pool.getEntries()) {

            current += entry.getWeight();

            if (roll < current) {
                return entry;
            }
        }

        // Safety fallback
        return pool.getEntries().get(
                pool.getEntries().size() - 1
        );
    }
}