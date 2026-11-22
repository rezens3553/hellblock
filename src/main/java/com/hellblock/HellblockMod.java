package com.hellblock;

import com.hellblock.network.FinalePayload;
import com.hellblock.network.StagePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HellblockMod implements ModInitializer {
    public static final String MOD_ID = "hellblock";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        HellConfig.load();
        ModBlocks.init();
        ModEntities.init();

        PayloadTypeRegistry.playS2C().register(StagePayload.TYPE, StagePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FinalePayload.TYPE, FinalePayload.CODEC);

        CorruptionManager.init();
        HellCommands.register();

        LOGGER.info("Hellblock загружен. Блок смотрит на тебя.");
    }
}
