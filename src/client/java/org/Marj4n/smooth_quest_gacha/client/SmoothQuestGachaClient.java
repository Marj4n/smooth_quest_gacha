package org.Marj4n.smooth_quest_gacha.client;

import org.Marj4n.smooth_quest_gacha.gacha.GachaRarity;
import org.Marj4n.smooth_quest_gacha.network.OpenGachaPacket;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SmoothQuestGachaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                OpenGachaPacket.ID,
                (client, handler, buf, responseSender) -> {
                    int count = Math.min(buf.readVarInt(), OpenGachaPacket.MAX_RESULTS);
                    List<GachaScreen.Result> results = new ArrayList<>(count);

                    for (int i = 0; i < count; i++) {
                        ItemStack stack = buf.readItemStack();
                        GachaRarity rarity = buf.readEnumConstant(GachaRarity.class);
                        results.add(new GachaScreen.Result(stack, rarity));
                    }

                    int poolCount = Math.min(buf.readVarInt(), OpenGachaPacket.MAX_POOL_PREVIEW);
                    List<GachaScreen.PoolSymbol> pool = new ArrayList<>(poolCount);
                    for (int i = 0; i < poolCount; i++) {
                        ItemStack stack = buf.readItemStack();
                        GachaRarity rarity = buf.readEnumConstant(GachaRarity.class);
                        int weight = Math.max(1, buf.readVarInt());
                        pool.add(new GachaScreen.PoolSymbol(stack, rarity, weight));
                    }

                    client.execute(() -> {
                        if (!results.isEmpty()) {
                            client.setScreen(new GachaScreen(results, pool));
                        }
                    });
                }
        );
    }
}
