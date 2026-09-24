package org.Marj4n.smooth_quest_gacha.network;

import org.Marj4n.smooth_quest_gacha.SmoothQuestGacha;
import org.Marj4n.smooth_quest_gacha.gacha.GachaEntry;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

public final class OpenGachaPacket {

    public static final Identifier ID = new Identifier(SmoothQuestGacha.MOD_ID, "open_gacha");
    public static final int MAX_RESULTS = 64;
    public static final int MAX_POOL_PREVIEW = 256;

    private OpenGachaPacket() {
    }

    public static void send(ServerPlayerEntity player, List<GachaEntry> results, List<GachaEntry> poolEntries) {
        if (results == null || results.isEmpty()) {
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());

        int resultCount = Math.min(results.size(), MAX_RESULTS);
        buf.writeVarInt(resultCount);
        for (int i = 0; i < resultCount; i++) {
            GachaEntry entry = results.get(i);
            buf.writeItemStack(entry.getItem());
            buf.writeEnumConstant(entry.getRarity());
        }

        // Send the configured pool too. The client uses ONLY these items as
        // reel symbols, so the animation always matches the selected pool.
        int poolCount = poolEntries == null ? 0 : Math.min(poolEntries.size(), MAX_POOL_PREVIEW);
        buf.writeVarInt(poolCount);
        for (int i = 0; i < poolCount; i++) {
            GachaEntry entry = poolEntries.get(i);
            buf.writeItemStack(entry.getItem());
            buf.writeEnumConstant(entry.getRarity());
            buf.writeVarInt(entry.getWeight());
        }

        ServerPlayNetworking.send(player, ID, buf);
    }
}
