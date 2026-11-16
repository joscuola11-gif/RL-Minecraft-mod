package com.rlfennec.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rlfennec.RLFennec;
import com.rlfennec.entity.FennecEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class FennecRenderer extends EntityRenderer<FennecEntity> {
    private static final ResourceLocation TEXTURE = RLFennec.id("textures/entity/fennec_chassis.png");

    public FennecRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(FennecEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(FennecEntity car, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // The whole orientation (yaw, pitch, roll, flips) is one rotation, interpolated as a quaternion so that
        // flips and loops never jump. It turns around the centre of mass.
        float pivot = FennecModel.pivotHeight();
        // a car lying on its roof, side or nose is raised so that it rests on the road
        poseStack.translate(0.0F, pivot + car.getVisualLift(partialTick), 0.0F);
        poseStack.mulPose(car.getOrientation(partialTick));

        float hurt = car.getHurtTime() - partialTick;
        float damage = Math.max(car.getDamage() - partialTick, 0.0F);
        if (hurt > 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(hurt) * hurt * damage / 10.0F * car.getHurtDir()));
        }

        poseStack.translate(0.0F, -pivot, 0.0F);

        FennecModel.render(car, partialTick, poseStack, packedLight);
        poseStack.popPose();

        super.render(car, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
