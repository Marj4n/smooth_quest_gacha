package org.Marj4n.smooth_quest_gacha.integration.ftbquests;

import org.Marj4n.smooth_quest_gacha.SmoothQuestGacha;
import org.Marj4n.smooth_quest_gacha.config.GachaConfigManager;
import org.Marj4n.smooth_quest_gacha.gacha.GachaEntry;
import org.Marj4n.smooth_quest_gacha.gacha.GachaPool;
import org.Marj4n.smooth_quest_gacha.gacha.GachaRoller;
import org.Marj4n.smooth_quest_gacha.network.OpenGachaPacket;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GachaReward extends Reward {

    private static final String DEFAULT_POOL = "default";
    private static final int DEFAULT_ROLLS = 1;

    private String poolId = DEFAULT_POOL;
    private int rolls = DEFAULT_ROLLS;

    public GachaReward(
            long id,
            Quest quest
    ) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FTBQuestsIntegration.GACHA_REWARD;
    }

    /*
     * Save into FTB Quest data.
     */
    @Override
    public void writeData(NbtCompound nbt) {

        super.writeData(nbt);

        nbt.putString(
                "pool",
                poolId
        );

        nbt.putInt(
                "rolls",
                rolls
        );
    }

    /*
     * Load from FTB Quest data.
     */
    @Override
    public void readData(NbtCompound nbt) {

        super.readData(nbt);

        if (nbt.contains("pool")) {

            String loadedPool =
                    nbt.getString("pool");

            if (!loadedPool.isBlank()) {
                poolId = loadedPool;
            }
        }

        if (nbt.contains("rolls")) {

            rolls = Math.max(
                    1,
                    nbt.getInt("rolls")
            );
        }
    }

    /*
     * Synchronize reward settings to clients.
     */
    @Override
    public void writeNetData(PacketByteBuf buffer) {

        super.writeNetData(buffer);

        buffer.writeString(
                poolId,
                256
        );

        buffer.writeVarInt(
                rolls
        );
    }

    @Override
    public void readNetData(PacketByteBuf buffer) {

        super.readNetData(buffer);

        poolId =
                buffer.readString(256);

        rolls =
                Math.max(
                        1,
                        buffer.readVarInt()
                );
    }

    /*
     * Fields displayed by FTB Quests editor.
     */
    @Override
    public void fillConfigGroup(
            ConfigGroup group
    ) {

        super.fillConfigGroup(group);

        List<String> availablePools =
                new ArrayList<>(
                        GachaConfigManager.getPools().keySet()
                );

        availablePools.sort(
                Comparator.naturalOrder()
        );

        // Keep the currently saved value visible even if the pool was
        // removed from the config after this quest was created.
        if (!availablePools.contains(poolId)) {
            availablePools.add(poolId);
        }

        // A completely empty config should never make the editor unusable.
        if (availablePools.isEmpty()) {
            availablePools.add(DEFAULT_POOL);
        }

        String defaultPool =
                availablePools.contains(DEFAULT_POOL)
                        ? DEFAULT_POOL
                        : availablePools.get(0);

        NameMap<String> poolNames =
                NameMap.of(
                                defaultPool,
                                availablePools
                        )
                        .id(value -> value)
                        .name(value -> {

                            GachaPool pool =
                                    GachaConfigManager.getPool(value);

                            if (pool == null) {
                                return Text.literal(
                                        value + " (Missing)"
                                );
                            }

                            int entries =
                                    pool.getEntries().size();

                            return Text.literal(
                                    value
                                            + " ("
                                            + entries
                                            + (entries == 1
                                            ? " reward)"
                                            : " rewards)")
                            );
                        })
                        .create();

        group.addEnum(
                "pool",
                poolId,
                value -> poolId = value,
                poolNames,
                defaultPool
        );

        group.addInt(
                "rolls",
                rolls,
                value ->
                        rolls =
                                Math.max(
                                        1,
                                        value
                                ),
                DEFAULT_ROLLS,
                1,
                64
        );
    }

    @Override
    public void claim(
            ServerPlayerEntity player,
            boolean notify
    ) {

        GachaPool pool = GachaConfigManager.getPool(poolId);

        if (pool == null || pool.isEmpty()) {
            SmoothQuestGacha.LOGGER.warn(
                    "Player {} tried to claim Smooth Gacha with missing or empty pool '{}'.",
                    player.getName().getString(),
                    poolId
            );
            return;
        }

        List<GachaEntry> results = new ArrayList<>();
        int rollCount = Math.min(Math.max(1, rolls), OpenGachaPacket.MAX_RESULTS);

        for (int i = 0; i < rollCount; i++) {
            GachaEntry result = GachaRoller.roll(pool, player.getRandom());

            if (result == null) {
                SmoothQuestGacha.LOGGER.warn(
                        "Gacha pool '{}' returned no result for player {}.",
                        poolId,
                        player.getName().getString()
                );
                continue;
            }

            ItemStack reward = result.getItem().copy();

            if (!player.getInventory().insertStack(reward)) {
                player.dropItem(reward, false);
            }

            results.add(result);

            SmoothQuestGacha.LOGGER.info(
                    "Player {} rolled {} x{} [{}] from pool '{}'.",
                    player.getName().getString(),
                    result.getItem().getItem().toString(),
                    result.getItem().getCount(),
                    result.getRarity(),
                    poolId
            );
        }

        // One packet for the whole pull. This prevents Rolls > 1 from opening
        // several screens on top of each other and lets the client paginate
        // eight reels at a time.
        OpenGachaPacket.send(player, results, pool.getEntries());
    }
}
