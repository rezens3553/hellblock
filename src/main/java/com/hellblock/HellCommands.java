package com.hellblock;

import com.hellblock.entity.ArchdemonEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Отладочные команды для записи видео:
 *   /hell phase <0-4>   - прыгнуть сразу в стадию
 *   /hell count <n>     - выставить число сломанных блоков
 *   /hell demon         - вызвать демона рядом (в 48 блоках)
 *   /hell finale        - запустить финал немедленно
 *   /hell reset         - сбросить скверну в 0
 *   /hell init          - поставить блок-печать на (0,64,0) и телепортировать к нему
 */
public final class HellCommands {
    private HellCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("hell")
                        .then(Commands.literal("phase")
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, 4))
                                        .executes(ctx -> phase(ctx, IntegerArgumentType.getInteger(ctx, "n")))))
                        .then(Commands.literal("count")
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, 1000000))
                                        .executes(ctx -> count(ctx, IntegerArgumentType.getInteger(ctx, "n")))))
                        .then(Commands.literal("demon").executes(HellCommands::demon))
                        .then(Commands.literal("finale").executes(HellCommands::finale))
                        .then(Commands.literal("reset").executes(ctx -> count(ctx, 0)))
                        .then(Commands.literal("init").executes(HellCommands::init))));
    }

    private static int phase(CommandContext<CommandSourceStack> ctx, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        int target = HellConfig.INSTANCE.phaseThresholds[n];
        return count(ctx, target);
    }

    private static int count(CommandContext<CommandSourceStack> ctx, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();
        CorruptionManager.setCount(level, player, n);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Скверна: " + CorruptionManager.count() + " (стадия " + CorruptionManager.stage() + ")"), false);
        return 1;
    }

    private static int demon(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = ctx.getSource().getLevel();
        ArchdemonEntity existing = CorruptionManager.findDemon(level);
        if (existing != null) {
            existing.discard();
        }
        CorruptionManager.spawnDemon(level, player);
        ctx.getSource().sendSuccess(() -> Component.literal("Демон вызван."), false);
        return 1;
    }

    private static int finale(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CorruptionManager.triggerFinale(ctx.getSource().getPlayerOrException());
        return 1;
    }

    private static int init(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        CorruptionManager.initWorld(ctx.getSource().getServer().overworld());
        CorruptionManager.teleportToSeal(player);
        CorruptionManager.sendStage(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Блок-печать установлен на 0 64 0."), false);
        return 1;
    }
}
