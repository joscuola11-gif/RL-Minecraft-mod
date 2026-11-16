package com.rlfennec.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.rlfennec.RLFennec;
import com.rlfennec.entity.FennecEntity;
import com.rlfennec.physics.CarPhysics;
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

        float yaw = Mth.rotLerp(partialTick, car.yRotO, car.getYRot());
        float pitch = Mth.lerp(partialTick, car.xRotO, car.getXRot());

        // The mesh faces +X, an entity with yaw 0 faces +Z.
        poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F - yaw));

        // pitch / flips rotate around the centre of mass
        float pivot = FennecModel.pivotHeight();
        poseStack.translate(0.0F, pivot, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-pitch));

        float flip = car.getFlipProgress(partialTick);
        if (flip > 0.0F) {
            float angle = 360.0F * (flip * flip * (3.0F - 2.0F * flip)); // smoothstep
            switch (car.getFlipType()) {
                case CarPhysics.FLIP_FRONT -> poseStack.mulPose(Axis.ZP.rotationDegrees(-angle));
                case CarPhysics.FLIP_BACK -> poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
                case CarPhysics.FLIP_LEFT -> poseStack.mulPose(Axis.XP.rotationDegrees(-angle));
                case CarPhysics.FLIP_RIGHT -> poseStack.mulPose(Axis.XP.rotationDegrees(angle));
                default -> { }
            }
        }

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
