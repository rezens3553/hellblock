package com.hellblock.client;

import com.hellblock.HellblockMod;
import com.hellblock.entity.ArchdemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class ArchdemonRenderer extends HumanoidMobRenderer<ArchdemonEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private static final Identifier TEXTURE = HellblockMod.id("textures/entity/archdemon.png");

    public ArchdemonRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ArchdemonModel.LAYER)), 0.6F);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }

    @Override
    protected void scale(HumanoidRenderState state, PoseStack poseStack) {
        // демон выше обычного гуманоида
        poseStack.scale(1.5F, 1.5F, 1.5F);
    }
}
