package com.hellblock.network;

import com.hellblock.HellblockMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер -> клиент: текущая стадия скверны (для красного оверлея и виньетки). */
public record StagePayload(int stage, int count) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StagePayload> TYPE =
            new CustomPacketPayload.Type<>(HellblockMod.id("stage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StagePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StagePayload::stage,
            ByteBufCodecs.VAR_INT, StagePayload::count,
            StagePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
