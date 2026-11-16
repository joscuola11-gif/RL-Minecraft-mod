package com.rlfennec.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.rlfennec.RLFennec;
import com.rlfennec.entity.BoostPadEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * Draws a boost pad from cached vertex buffers (plate, glowing parts, columns of light, the orb), with an animated orb
 * that spins and floats, pulsing light columns, and a dark plate while the pad is empty.
 */
public class BoostPadRenderer extends EntityRenderer<BoostPadEntity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SOLID = RLFennec.id("textures/entity/solid.png");
    private static final Vector3f LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();

    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};
    private static final float[] EMPTY_GLOW = {0.16F, 0.08F, 0.03F};

    private record Buffers(VertexBuffer solid, VertexBuffer glow, VertexBuffer beam, VertexBuffer orb, VertexBuffer halo) {
        void close() {
            for (VertexBuffer b : new VertexBuffer[] {solid, glow, beam, orb, halo}) {
                if (b != null) {
                    b.close();
                }
            }
        }
    }

    private static Buffers big;
    private static Buffers small;
    private static MipTexture texture;
    private static boolean failed;

    public BoostPadRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(BoostPadEntity entity) {
        return SOLID;
    }

    @Override
    public boolean shouldRender(BoostPadEntity pad, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true; // (the culling box is already generous; this keeps far pads in view across the arena)
    }

    public static void reset() {
        if (big != null) {
            big.close();
        }
        if (small != null) {
            small.close();
        }
        if (texture != null) {
            texture.close();
        }
        big = small = null;
        texture = null;
        failed = false;
    }

    private static boolean ensureLoaded() {
        if (big != null) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            texture = MipTexture.load(Minecraft.getInstance().getResourceManager(), SOLID);
            big = upload(BoostPadGeometry.bigPad());
            small = upload(BoostPadGeometry.smallPad());
            return true;
        } catch (Exception e) {
            LOGGER.error("Could not build the boost pads", e);
            failed = true;
            return false;
        }
    }

    private static Buffers upload(BoostPadGeometry.Pad pad) {
        return new Buffers(upload(pad.solid), upload(pad.glow), upload(pad.beam), upload(pad.orb), upload(pad.halo));
    }

    private static VertexBuffer upload(BoostPadGeometry.Mesh mesh) {
        if (mesh.vertexCount == 0) {
            return null;
        }
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(mesh.vertexCount * 36 + 64)) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
            for (int i = 0; i < mesh.vertexCount; i++) {
                int o = i * BoostPadGeometry.FLOATS_PER_VERTEX;
                float[] d = mesh.data;
                builder.addVertex(d[o], d[o + 1], d[o + 2])
                        .setColor(d[o + 6], d[o + 7], d[o + 8], d[o + 9])
                        .setUv(0.5F, 0.5F)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(d[o + 3], d[o + 4], d[o + 5]);
            }
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(builder.buildOrThrow());
            VertexBuffer.unbind();
            return buffer;
        }
    }

    @Override
    public void render(BoostPadEntity pad, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (!ensureLoaded()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Buffers b = pad.isBig() ? big : small;
        boolean active = pad.isActive();
        float time = pad.level().getGameTime() + partialTick;

        mc.gameRenderer.lightTexture().turnOnLightLayer();
        mc.gameRenderer.overlayTexture().setupOverlayColor();
        RenderSystem.setShader(GameRenderer::getRendertypeEntityCutoutNoCullShader);
        RenderSystem.setShaderTexture(0, texture.id());
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        poseStack.pushPose();
        draw(b.solid(), poseStack, WHITE, 1.0F);
        draw(b.glow(), poseStack, active ? WHITE : EMPTY_GLOW, 1.0F);

        if (active) {
            if (pad.isBig()) {
                poseStack.pushPose();
                float bob = Mth.sin(time * 0.09F) * 0.22F;
                poseStack.translate(0.0F, (float) BoostPadGeometry.BIG_ORB_HEIGHT + bob, 0.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(time * 3.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(18.0F));
                draw(b.orb(), poseStack, WHITE, 1.0F);
                poseStack.popPose();
            }

            // translucent light: columns and the halo around the orb
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            float pulse = 0.78F + 0.22F * Mth.sin(time * 0.17F);
            draw(b.beam(), poseStack, WHITE, pulse);
            if (pad.isBig()) {
                poseStack.pushPose();
                poseStack.translate(0.0F, (float) BoostPadGeometry.BIG_ORB_HEIGHT + Mth.sin(time * 0.09F) * 0.22F, 0.0F);
                draw(b.halo(), poseStack, WHITE, 0.8F + 0.2F * Mth.sin(time * 0.23F));
                poseStack.popPose();
            }
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
        poseStack.popPose();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        mc.gameRenderer.overlayTexture().teardownOverlayColor();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
        if (mc.level != null && mc.level.effects().constantAmbientLight()) {
            com.mojang.blaze3d.platform.Lighting.setupNetherLevel();
        } else {
            com.mojang.blaze3d.platform.Lighting.setupLevel();
        }
    }

    private static void draw(VertexBuffer buffer, PoseStack poseStack, float[] tint, float alpha) {
        if (buffer == null) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        Matrix3f toModel = pose.normal().transpose(new Matrix3f());
        RenderSystem.setShaderLights(toModel.transform(LIGHT_0, new Vector3f()), toModel.transform(LIGHT_1, new Vector3f()));
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], alpha);
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.pose());
        buffer.bind();
        buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
    }
}
