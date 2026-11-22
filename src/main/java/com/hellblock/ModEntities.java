package com.hellblock;

import com.hellblock.entity.ArchdemonEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static EntityType<ArchdemonEntity> ARCHDEMON;

    private ModEntities() {
    }

    public static void init() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, HellblockMod.id("archdemon"));
        ARCHDEMON = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
                EntityType.Builder.of(ArchdemonEntity::new, MobCategory.MONSTER)
                        .sized(0.9F, 2.9F)
                        .eyeHeight(2.6F)
                        .fireImmune()
                        .clientTrackingRange(12)
                        .build(key));
        FabricDefaultAttributeRegistry.register(ARCHDEMON, ArchdemonEntity.createAttributes());
    }
}
