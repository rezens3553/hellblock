package com.hellblock.entity;

import com.hellblock.CorruptionManager;
import com.hellblock.HellConfig;
import com.hellblock.HellSound;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Архидемон. Парит и проходит сквозь стены (в пустоте под островом негде ходить).
 * Поведение как у Weeping Angel: замирает, когда на него смотрят, и подкрадывается, когда отвернулся.
 * Каждый сломанный игроком блок добавляет ему «банк шагов». Гасит факелы рядом. Неуязвим.
 */
public class ArchdemonEntity extends Monster {
    private double stepBank = 0.0;
    private int heartbeatCooldown = 0;

    public ArchdemonEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 666.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.FOLLOW_RANGE, 256.0);
    }

    /** Вызывается на каждый сломанный блок. */
    public void bankSteps(double blocks) {
        this.stepBank += blocks;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // неуязвим ко всему, кроме /kill и void
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount);
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel) {
            this.serverTick(serverLevel);
        }
    }

    private void serverTick(ServerLevel level) {
        this.noPhysics = true;
        this.setNoGravity(true);

        Player nearest = level.getNearestPlayer(this, 256.0);
        if (!(nearest instanceof ServerPlayer player) || player.isSpectator()) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        Vec3 toPlayer = player.position().add(0.0, 0.2, 0.0).subtract(this.position());
        double dist = toPlayer.length();

        // всегда смотрит на игрока
        float yaw = (float) (Mth.atan2(toPlayer.z, toPlayer.x) * (180.0 / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        this.setXRot((float) -(Mth.atan2(toPlayer.y, Math.sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z)) * (180.0 / Math.PI)));

        boolean seen = isSeenBy(player);
        if (seen) {
            // Weeping Angel: пока смотришь - не двигается
            this.setDeltaMovement(Vec3.ZERO);
        } else if (dist > 0.01) {
            int stage = CorruptionManager.stage();
            double speed = HellConfig.INSTANCE.demonBaseSpeed * (stage >= 4 ? 2.2 : 1.0);
            double bank = Math.min(this.stepBank, 1.0);
            this.stepBank -= bank;
            double move = Math.min(dist, speed + bank);
            Vec3 step = toPlayer.scale(move / dist);
            this.setPos(this.getX() + step.x, this.getY() + step.y, this.getZ() + step.z);
            this.setDeltaMovement(Vec3.ZERO);
        }

        // гасит свет вокруг
        if (this.tickCount % 10 == 0) {
            snuffLights(level);
        }

        // сердцебиение, чем ближе - тем чаще
        if (--this.heartbeatCooldown <= 0) {
            float volume = (float) Mth.clamp(1.3 - dist / 60.0, 0.15, 1.0);
            HellSound.play(player, SoundEvents.WARDEN_HEARTBEAT, volume, 0.8F);
            this.heartbeatCooldown = (int) Mth.clamp(dist * 0.6, 12.0, 60.0);
        }

        // дым и пепел
        if (this.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 1.2, this.getZ(), 2, 0.35, 0.7, 0.35, 0.01);
            level.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 2.0, this.getZ(), 4, 0.5, 0.8, 0.5, 0.0);
        }

        // касание = конец
        if (dist < 2.2 && HellConfig.INSTANCE.finaleOnTouch) {
            CorruptionManager.triggerFinale(player);
        }
    }

    /** Игрок смотрит на демона: угол < ~35 градусов от направления взгляда и есть прямая видимость. */
    private boolean isSeenBy(Player player) {
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 to = this.getEyePosition().subtract(player.getEyePosition());
        double len = to.length();
        if (len < 1.0E-3) {
            return true;
        }
        double dot = look.dot(to.scale(1.0 / len));
        return dot > 0.82 && player.hasLineOfSight(this);
    }

    private void snuffLights(ServerLevel level) {
        BlockPos center = this.blockPosition();
        int r = 6;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    Block b = state.getBlock();
                    if (b == Blocks.TORCH || b == Blocks.WALL_TORCH || b == Blocks.SOUL_TORCH || b == Blocks.SOUL_WALL_TORCH
                            || b == Blocks.REDSTONE_TORCH || b == Blocks.REDSTONE_WALL_TORCH
                            || b == Blocks.LANTERN || b == Blocks.SOUL_LANTERN) {
                        level.removeBlock(pos, false);
                        puff(level, pos);
                    } else if (b instanceof CampfireBlock && state.getValue(BlockStateProperties.LIT)) {
                        level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
                        puff(level, pos);
                    }
                }
            }
        }
    }

    private void puff(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.02);
        HellSound.playAt(level, pos, SoundEvents.FIRE_EXTINGUISH, 0.6F, 0.8F);
    }
}
