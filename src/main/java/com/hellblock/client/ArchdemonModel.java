package com.hellblock.client;

import com.hellblock.HellblockMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Модель демона: гуманоид + рога + крылья. Текстура 128x128, развёртка тела как у игрока
 * (голова 0,0; торс 16,16; руки 40,16 / 32,48; ноги 0,16 / 16,48),
 * рога на (64,0) и (64,8), крылья на (0,64).
 */
public final class ArchdemonModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HellblockMod.id("archdemon"), "main");

    private ArchdemonModel() {
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();

        // рога
        PartDefinition head = root.getChild("head");
        PartDefinition hornL = head.addOrReplaceChild("horn_l",
                CubeListBuilder.create().texOffs(64, 0).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(3.0F, -7.5F, 0.0F, 0.0F, 0.0F, 0.30F));
        hornL.addOrReplaceChild("tip",
                CubeListBuilder.create().texOffs(64, 8).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, 0.45F));
        PartDefinition hornR = head.addOrReplaceChild("horn_r",
                CubeListBuilder.create().texOffs(64, 0).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(-3.0F, -7.5F, 0.0F, 0.0F, 0.0F, -0.30F));
        hornR.addOrReplaceChild("tip",
                CubeListBuilder.create().texOffs(64, 8).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, -0.45F));

        // крылья (тонкие плоскости на спине, развёрнуты назад и в стороны)
        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("wing_l",
                CubeListBuilder.create().texOffs(0, 64).addBox(0.0F, 0.0F, 0.0F, 14.0F, 16.0F, 1.0F),
                PartPose.offsetAndRotation(1.0F, 0.5F, 2.0F, 0.0F, -0.75F, 0.0F));
        body.addOrReplaceChild("wing_r",
                CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-14.0F, 0.0F, 0.0F, 14.0F, 16.0F, 1.0F),
                PartPose.offsetAndRotation(-1.0F, 0.5F, 2.0F, 0.0F, 0.75F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }
}
