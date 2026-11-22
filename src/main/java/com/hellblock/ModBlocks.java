package com.hellblock;

import com.hellblock.block.SealBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    public static Block SEAL_BLOCK;
    public static Item SEAL_ITEM;

    private ModBlocks() {
    }

    public static void init() {
        Identifier id = HellblockMod.id("seal_block");

        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        SEAL_BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey,
                new SealBlock(BlockBehaviour.Properties.of()
                        .strength(0.8F)
                        .sound(SoundType.STONE)
                        .lightLevel(state -> state.getValue(SealBlock.STAGE) * 3)
                        .noLootTable()
                        .setId(blockKey)));

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        SEAL_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(SEAL_BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
    }
}
