package com.hellblock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hellblock.block.SealBlock;
import com.hellblock.entity.ArchdemonEntity;
import com.hellblock.network.FinalePayload;
import com.hellblock.network.StagePayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Сердце мода. Считает сломанные блоки («скверну»), хранит стадию 0..4,
 * выдаёт лут, спавнит мобов, шепчет, пишет в чат от имени Сатаны, вызывает демона и финал.
 */
public final class CorruptionManager {
    /** Где стоит единственный блок. */
    public static final BlockPos SEAL_POS = new BlockPos(0, 64, 0);

    private static final Random RNG = new Random();

    private static int count = 0;
    private static boolean initialized = false;
    private static boolean finaleStarted = false;
    private static Path stateFile;
    private static long tickCounter = 0;

    private CorruptionManager() {
    }

    // ------------------------------------------------------------------ init

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(CorruptionManager::loadState);
        ServerLifecycleEvents.SERVER_STOPPING.register(CorruptionManager::saveState);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            server.execute(() -> onJoin(server, player));
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (initialized) {
                teleportToSeal(newPlayer);
                sendStage(newPlayer);
            }
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel level
                    && player instanceof ServerPlayer sp
                    && state.is(ModBlocks.SEAL_BLOCK)
                    && pos.equals(SEAL_POS)) {
                onSealBroken(level, sp);
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter % 20 == 0) {
                secondTick(server);
            }
        });
    }

    // ----------------------------------------------------------------- state

    public static int count() {
        return count;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /** Текущая стадия 0..4 по порогам из конфига. */
    public static int stage() {
        return stageFor(count);
    }

    public static int stageFor(int c) {
        int[] t = HellConfig.INSTANCE.phaseThresholds;
        int s = 0;
        for (int i = 0; i < t.length && i < 5; i++) {
            if (c >= t[i]) {
                s = i;
            }
        }
        return s;
    }

    private static void loadState(MinecraftServer server) {
        finaleStarted = false;
        count = 0;
        initialized = false;
        stateFile = server.getWorldPath(LevelResource.ROOT).resolve("hellblock_state.json");
        try {
            if (Files.exists(stateFile)) {
                try (Reader r = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
                    JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                    count = o.has("count") ? o.get("count").getAsInt() : 0;
                    initialized = o.has("initialized") && o.get("initialized").getAsBoolean();
                }
            }
        } catch (Exception e) {
            HellblockMod.LOGGER.error("Не удалось прочитать hellblock_state.json", e);
        }
    }

    private static void saveState(MinecraftServer server) {
        if (stateFile == null) {
            return;
        }
        try (Writer w = Files.newBufferedWriter(stateFile, StandardCharsets.UTF_8)) {
            JsonObject o = new JsonObject();
            o.addProperty("count", count);
            o.addProperty("initialized", initialized);
            w.write(o.toString());
        } catch (Exception e) {
            HellblockMod.LOGGER.error("Не удалось сохранить hellblock_state.json", e);
        }
    }

    // ------------------------------------------------------------ world init

    private static void onJoin(MinecraftServer server, ServerPlayer player) {
        ServerLevel level = server.overworld();
        if (!initialized && isEmptyAroundSeal(level)) {
            initWorld(level);
            teleportToSeal(player);
        }
        sendStage(player);
    }

    private static boolean isEmptyAroundSeal(ServerLevel level) {
        level.getChunk(SEAL_POS);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!level.getBlockState(SEAL_POS.offset(dx, dy, dz)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Ставит блок-печать на (0,64,0). Вызывается автоматически в пустом мире или командой /hell init. */
    public static void initWorld(ServerLevel level) {
        level.getChunk(SEAL_POS);
        count = 0;
        initialized = true;
        level.setBlock(SEAL_POS, ModBlocks.SEAL_BLOCK.defaultBlockState().setValue(SealBlock.STAGE, 0), 3);
        if (level.getServer() != null) {
            saveState(level.getServer());
        }
    }

    public static void teleportToSeal(ServerPlayer player) {
        player.connection.teleport(0.5, SEAL_POS.getY() + 1.0, 0.5, player.getYRot(), player.getXRot());
    }

    // --------------------------------------------------------------- network

    public static void sendStage(ServerPlayer player) {
        ServerPlayNetworking.send(player, new StagePayload(stage(), count));
    }

    // ------------------------------------------------------------ main logic

    private static void onSealBroken(ServerLevel level, ServerPlayer player) {
        int oldStage = stage();
        count++;
        int newStage = stage();

        level.setBlock(SEAL_POS, ModBlocks.SEAL_BLOCK.defaultBlockState().setValue(SealBlock.STAGE, newStage), 3);

        dropLoot(level, newStage);
        maybeSpawnMob(level, newStage);

        // сердцебиение в такт ломанию, чем глубже стадия - тем чаще и громче
        if (RNG.nextDouble() < 0.10 + 0.17 * newStage) {
            HellSound.play(player, SoundEvents.WARDEN_HEARTBEAT, 0.5F + 0.12F * newStage, 0.9F + 0.04F * newStage);
        }

        // демон «делает шаг» за каждый сломанный блок
        ArchdemonEntity demon = findDemon(level);
        if (demon != null) {
            demon.bankSteps(HellConfig.INSTANCE.demonStepPerBreak);
        }

        if (newStage != oldStage) {
            onStageChange(level, player, newStage);
        }
        if (count % 20 == 0) {
            saveState(level.getServer());
        }
    }

    private static void onStageChange(ServerLevel level, ServerPlayer player, int newStage) {
        sendStage(player);
        HellSound.play(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7F, 0.5F);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));

        String text = switch (newStage) {
            case 1 -> "Что-то треснуло...";
            case 2 -> "Земля под блоком дышит.";
            case 3 -> "Он проснулся.";
            case 4 -> "Он выходит.";
            default -> null;
        };
        if (text != null) {
            player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    /** Меняет счётчик вручную (команды /hell phase и /hell count) и обновляет блок. */
    public static void setCount(ServerLevel level, ServerPlayer player, int newCount) {
        count = Math.max(0, newCount);
        int stage = stage();
        if (level.getBlockState(SEAL_POS).is(ModBlocks.SEAL_BLOCK)) {
            level.setBlock(SEAL_POS, ModBlocks.SEAL_BLOCK.defaultBlockState().setValue(SealBlock.STAGE, stage), 3);
        }
        finaleStarted = false;
        sendStage(player);
        saveState(level.getServer());
    }

    // ------------------------------------------------------------------ loot

    private record Drop(Item item, int min, int max, int weight) {
    }

    private static Drop d(Item item, int min, int max, int weight) {
        return new Drop(item, min, max, weight);
    }

    private static final List<List<Drop>> POOLS = new ArrayList<>();

    static {
        // стадия 0: обычный One Block
        POOLS.add(List.of(
                d(Items.OAK_LOG, 2, 5, 14), d(Items.BIRCH_LOG, 2, 4, 6), d(Items.DIRT, 3, 8, 10),
                d(Items.COBBLESTONE, 4, 10, 14), d(Items.SAND, 3, 6, 6), d(Items.GRAVEL, 3, 6, 5),
                d(Items.APPLE, 1, 2, 5), d(Items.WHEAT_SEEDS, 2, 5, 5), d(Items.OAK_SAPLING, 1, 2, 6),
                d(Items.COAL, 2, 4, 7), d(Items.STICK, 2, 6, 4), d(Items.BREAD, 1, 3, 4),
                d(Items.CARROT, 1, 3, 3), d(Items.POTATO, 1, 3, 3), d(Items.RAW_IRON, 1, 2, 4),
                d(Items.WATER_BUCKET, 1, 1, 2), d(Items.BONE_MEAL, 2, 4, 3)));
        // стадия 1: трещины
        POOLS.add(List.of(
                d(Items.COBBLESTONE, 4, 10, 10), d(Items.COAL, 2, 5, 8), d(Items.RAW_IRON, 1, 3, 9),
                d(Items.RAW_COPPER, 2, 5, 6), d(Items.BONE, 1, 3, 7), d(Items.STRING, 1, 3, 6),
                d(Items.ROTTEN_FLESH, 1, 3, 6), d(Items.COOKED_BEEF, 1, 3, 6), d(Items.COOKED_PORKCHOP, 1, 3, 5),
                d(Items.OAK_LOG, 2, 4, 8), d(Items.IRON_INGOT, 1, 2, 4), d(Items.GOLD_INGOT, 1, 1, 2),
                d(Items.FLINT_AND_STEEL, 1, 1, 2), d(Items.LAVA_BUCKET, 1, 1, 2), d(Items.OBSIDIAN, 1, 2, 3),
                d(Items.WATER_BUCKET, 1, 1, 2)));
        // стадия 2: ад просачивается
        POOLS.add(List.of(
                d(Items.NETHERRACK, 4, 10, 12), d(Items.SOUL_SAND, 2, 5, 8), d(Items.MAGMA_BLOCK, 1, 3, 6),
                d(Items.GLOWSTONE_DUST, 2, 4, 6), d(Items.QUARTZ, 2, 4, 6), d(Items.GOLD_NUGGET, 3, 8, 6),
                d(Items.RAW_GOLD, 1, 3, 5), d(Items.BLAZE_ROD, 1, 1, 3), d(Items.NETHER_WART, 1, 3, 5),
                d(Items.COOKED_PORKCHOP, 1, 3, 6), d(Items.IRON_INGOT, 1, 3, 5), d(Items.COAL, 2, 5, 5),
                d(Items.LAVA_BUCKET, 1, 1, 2), d(Items.OBSIDIAN, 1, 3, 4), d(Items.ENDER_PEARL, 1, 1, 2),
                d(Items.DIAMOND, 1, 1, 1)));
        // стадия 3: он просыпается
        POOLS.add(List.of(
                d(Items.BASALT, 3, 8, 8), d(Items.BLACKSTONE, 3, 8, 8), d(Items.SOUL_SOIL, 2, 6, 6),
                d(Items.OBSIDIAN, 1, 3, 6), d(Items.GHAST_TEAR, 1, 1, 2), d(Items.WITHER_SKELETON_SKULL, 1, 1, 1),
                d(Items.DIAMOND, 1, 2, 3), d(Items.ANCIENT_DEBRIS, 1, 1, 2), d(Items.ENDER_PEARL, 1, 2, 4),
                d(Items.BLAZE_ROD, 1, 2, 5), d(Items.GOLD_INGOT, 1, 3, 5), d(Items.IRON_INGOT, 1, 3, 5),
                d(Items.COOKED_BEEF, 2, 4, 6), d(Items.GOLDEN_APPLE, 1, 1, 1), d(Items.END_PORTAL_FRAME, 1, 1, 1)));
        // стадия 4: охота
        POOLS.add(List.of(
                d(Items.NETHERITE_SCRAP, 1, 1, 3), d(Items.DIAMOND, 1, 2, 5), d(Items.OBSIDIAN, 2, 4, 6),
                d(Items.CRYING_OBSIDIAN, 1, 3, 4), d(Items.GOLDEN_APPLE, 1, 1, 3), d(Items.ENCHANTED_GOLDEN_APPLE, 1, 1, 1),
                d(Items.TOTEM_OF_UNDYING, 1, 1, 1), d(Items.ENDER_EYE, 1, 2, 4), d(Items.ENDER_PEARL, 1, 3, 5),
                d(Items.BLAZE_ROD, 1, 2, 5), d(Items.END_PORTAL_FRAME, 1, 1, 3), d(Items.COOKED_BEEF, 2, 5, 8),
                d(Items.WITHER_SKELETON_SKULL, 1, 1, 1), d(Items.ANCIENT_DEBRIS, 1, 1, 3)));
    }

    private static void dropLoot(ServerLevel level, int stage) {
        // чаще берём лут текущей стадии, но иногда - из прошлых (еда и дерево нужны всегда)
        int poolIndex = stage;
        if (stage > 0 && RNG.nextDouble() < 0.30) {
            poolIndex = RNG.nextInt(stage + 1);
        }
        List<Drop> pool = POOLS.get(poolIndex);
        int total = 0;
        for (Drop drop : pool) {
            total += drop.weight();
        }
        int roll = RNG.nextInt(total);
        for (Drop drop : pool) {
            roll -= drop.weight();
            if (roll < 0) {
                int amount = drop.min() + RNG.nextInt(drop.max() - drop.min() + 1);
                Block.popResource(level, SEAL_POS.above(), new ItemStack(drop.item(), amount));
                return;
            }
        }
    }

    // ------------------------------------------------------------------ mobs

    private static final List<List<EntityType<?>>> MOBS = List.of(
            List.of(EntityType.COW, EntityType.PIG, EntityType.CHICKEN, EntityType.SHEEP, EntityType.ZOMBIE),
            List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.PIG),
            List.of(EntityType.HUSK, EntityType.ZOMBIFIED_PIGLIN, EntityType.MAGMA_CUBE, EntityType.SKELETON, EntityType.CREEPER),
            List.of(EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN, EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.HUSK),
            List.of(EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.CREEPER, EntityType.SKELETON, EntityType.ZOMBIE));

    private static void maybeSpawnMob(ServerLevel level, int stage) {
        if (RNG.nextDouble() >= HellConfig.INSTANCE.mobChance) {
            return;
        }
        List<EntityType<?>> list = MOBS.get(stage);
        EntityType<?> type = list.get(RNG.nextInt(list.size()));
        try {
            type.spawn(level, SEAL_POS.above(), EntitySpawnReason.TRIGGERED);
        } catch (Exception e) {
            HellblockMod.LOGGER.warn("Не удалось заспавнить моба {}", type, e);
        }
    }

    // ---------------------------------------------------------------- demon

    public static ArchdemonEntity findDemon(ServerLevel level) {
        List<? extends ArchdemonEntity> list = level.getEntities(ModEntities.ARCHDEMON, e -> e.isAlive());
        return list.isEmpty() ? null : list.get(0);
    }

    public static ArchdemonEntity spawnDemon(ServerLevel level, ServerPlayer player) {
        ArchdemonEntity demon = ModEntities.ARCHDEMON.create(level, EntitySpawnReason.EVENT);
        if (demon == null) {
            return null;
        }
        double angle = RNG.nextDouble() * Math.PI * 2;
        double dist = HellConfig.INSTANCE.demonSpawnDistance;
        demon.setPos(player.getX() + Math.cos(angle) * dist, player.getY() + 2.0, player.getZ() + Math.sin(angle) * dist);
        level.addFreshEntity(demon);
        return demon;
    }

    /** Финал: чёрный экран, крик, окно с сообщением, краш игры. */
    public static void triggerFinale(ServerPlayer player) {
        if (finaleStarted) {
            return;
        }
        finaleStarted = true;
        HellConfig cfg = HellConfig.INSTANCE;

        ServerLevel level = player.level() instanceof ServerLevel sl ? sl : null;
        // откатываем в стадию 2, чтобы мир остался играбельным, если он не удалён
        if (level != null) {
            int target = cfg.phaseThresholds[2];
            count = target;
            if (level.getBlockState(SEAL_POS).is(ModBlocks.SEAL_BLOCK)) {
                level.setBlock(SEAL_POS, ModBlocks.SEAL_BLOCK.defaultBlockState().setValue(SealBlock.STAGE, 2), 3);
            }
            ArchdemonEntity demon = findDemon(level);
            if (demon != null) {
                demon.discard();
            }
            saveState(level.getServer());
        }
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0, false, false));
        HellSound.play(player, SoundEvents.WITHER_SPAWN, 1.0F, 0.6F);
        ServerPlayNetworking.send(player, new FinalePayload(
                cfg.finaleTitle, cfg.finaleMessage, cfg.deleteWorldOnFinale, cfg.finaleDelayMs));
    }

    // -------------------------------------------------------- ambient events

    private static void secondTick(MinecraftServer server) {
        if (!initialized || finaleStarted) {
            return;
        }
        ServerLevel level = server.overworld();
        HellConfig cfg = HellConfig.INSTANCE;
        int stage = stage();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level) {
                continue;
            }

            // шёпот и странные звуки
            if (stage >= 1 && stage <= 2 && RNG.nextInt(40) == 0) {
                String w = cfg.whispers[RNG.nextInt(cfg.whispers.length)];
                player.displayClientMessage(Component.literal(w).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
            }
            if (stage >= 1 && RNG.nextInt(45) == 0) {
                if (RNG.nextBoolean()) {
                    HellSound.play(player, SoundEvents.AMBIENT_CAVE, 0.8F, 0.7F);
                } else {
                    HellSound.play(player, SoundEvents.GHAST_AMBIENT, 0.5F, 0.5F);
                }
            }

            if (stage >= 3) {
                // фейковый чат от Сатаны
                if (RNG.nextInt(40) == 0) {
                    String[] pool = stage >= 4 ? cfg.fakeChatStage4 : cfg.fakeChatStage3;
                    String line = pool[RNG.nextInt(pool.length)].replace("%player%", player.getName().getString());
                    player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.DARK_RED));
                }
                // звуки за спиной: шаги, дверь, сундук
                if (RNG.nextInt(30) == 0) {
                    Vec3 back = player.getLookAngle().scale(-3.5);
                    BlockPos at = BlockPos.containing(player.getX() + back.x, player.getY(), player.getZ() + back.z);
                    switch (RNG.nextInt(4)) {
                        case 0 -> HellSound.playAt(level, at, SoundEvents.ZOMBIE_STEP, 1.0F, 0.6F);
                        case 1 -> HellSound.playAt(level, at, SoundEvents.WOODEN_DOOR_OPEN, 1.0F, 0.8F);
                        case 2 -> HellSound.playAt(level, at, SoundEvents.CHEST_OPEN, 1.0F, 0.7F);
                        default -> HellSound.play(player, SoundEvents.ENDERMAN_STARE, 0.6F, 0.6F);
                    }
                }
            }
            if (stage >= 4 && RNG.nextInt(25) == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                HellSound.play(player, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.2F);
            }

            // демон появляется на стадии из конфига
            if (stage >= cfg.demonSpawnStage && findDemon(level) == null) {
                spawnDemon(level, player);
                HellSound.play(player, SoundEvents.WARDEN_NEARBY_CLOSEST, 1.0F, 0.6F);
            }
        }
    }
}
