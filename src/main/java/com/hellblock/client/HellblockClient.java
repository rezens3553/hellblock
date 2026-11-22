package com.hellblock.client;

import com.hellblock.HellblockMod;
import com.hellblock.ModEntities;
import com.hellblock.network.FinalePayload;
import com.hellblock.network.StagePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class HellblockClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(ArchdemonModel.LAYER, ArchdemonModel::createLayer);
        EntityRenderers.register(ModEntities.ARCHDEMON, ArchdemonRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(StagePayload.TYPE, (payload, context) -> {
            ClientState.stage = payload.stage();
            ClientState.count = payload.count();
        });
        ClientPlayNetworking.registerGlobalReceiver(FinalePayload.TYPE, (payload, context) ->
                FinaleClient.start(payload.title(), payload.message(), payload.deleteWorld(), payload.delayMs()));

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, HellblockMod.id("corruption"), HellHud::render);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientState.stage = 0;
            ClientState.count = 0;
        });
    }
}
