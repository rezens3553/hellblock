package com.hellblock.network;

import com.hellblock.HellblockMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер -> клиент: запустить финал (чёрный экран, крик, окно с сообщением, краш). */
public record FinalePayload(String title, String message, boolean deleteWorld, int delayMs) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FinalePayload> TYPE =
            new CustomPacketPayload.Type<>(HellblockMod.id("finale"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FinalePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FinalePayload::title,
            ByteBufCodecs.STRING_UTF8, FinalePayload::message,
            ByteBufCodecs.BOOL, FinalePayload::deleteWorld,
            ByteBufCodecs.VAR_INT, FinalePayload::delayMs,
            FinalePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
