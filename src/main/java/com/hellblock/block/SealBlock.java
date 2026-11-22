package com.hellblock.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Печать. Тот самый один блок. Стадия 0..4 определяет внешний вид (см. blockstates/seal_block.json).
 * Лута у блока нет: выпадение целиком делает CorruptionManager.
 */
public class SealBlock extends Block {
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 4);

    public SealBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }
}
