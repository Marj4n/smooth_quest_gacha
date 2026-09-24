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

    private OpenGachaPacket() {
    }

    public static void send(ServerPlayerEntity player, List<GachaEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        int size = Math.min(entries.size(), MAX_RESULTS);

        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeVarInt(size);

        for (int i = 0; i < size; i++) {
            GachaEntry entry = entries.get(i);
            buf.writeItemStack(entry.getItem());
            buf.writeEnumConstant(entry.getRarity());
        }

        ServerPlayNetworking.send(player, ID, buf);
    }
}
