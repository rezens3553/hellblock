package com.hellblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Обёртки над звуками. Перегрузки для SoundEvent и Holder<SoundEvent> нужны, потому что
 * часть констант в SoundEvents - Holder, а часть - обычный SoundEvent.
 */
public final class HellSound {
    private HellSound() {
    }

    /** Звук слышит только этот игрок, как будто он рядом (не привязан к позиции). */
    public static void play(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.playNotifySound(sound, SoundSource.AMBIENT, volume, pitch);
    }

    public static void play(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        play(player, sound.value(), volume, pitch);
    }

    /** Звук из конкретной точки мира (слышат все рядом). */
    public static void playAt(ServerLevel level, BlockPos pos, SoundEvent sound, float volume, float pitch) {
        level.playSound((Player) null, pos, sound, SoundSource.AMBIENT, volume, pitch);
    }

    public static void playAt(ServerLevel level, BlockPos pos, Holder<SoundEvent> sound, float volume, float pitch) {
        playAt(level, pos, sound.value(), volume, pitch);
    }
}
