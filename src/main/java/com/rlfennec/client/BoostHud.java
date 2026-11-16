package com.rlfennec.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rlfennec.RLFennec;
import com.rlfennec.entity.FennecEntity;
import com.rlfennec.physics.CarPhysics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** The round boost gauge in the bottom-right corner, like in Rocket League. */
public final class BoostHud implements LayeredDraw.Layer {
    public static final ResourceLocation ID = RLFennec.id("boost_meter");

    private static final int ORANGE = 0xFFFF8A1F;
    private static final int HOT = 0xFFFFC23D;
    private static final int EMPTY = 0xFFFF3B30;
    private static final int TRACK = 0x99202020;
    private static final int DISC = 0x8C000000;

    /** The gauge is an open ring: starts bottom-left and sweeps clockwise over the top to the bottom-right. */
    private static final float START_DEG = 225.0F;
    private static final float SWEEP_DEG = 270.0F;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof FennecEntity car)) {
            return;
        }

        float boost = car.getBoostAmount();
        boolean boosting = car.isBoosting();
        float fraction = Mth.clamp(boost / CarPhysics.MAX_BOOST, 0.0F, 1.0F);

        float outer = 36.0F;
        float inner = 27.0F;
        float cx = graphics.guiWidth() - outer - 18.0F;
        float cy = graphics.guiHeight() - outer - 18.0F;
        Matrix4f pose = graphics.pose().last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        disc(pose, cx, cy, outer + 5.0F, DISC);
        arc(pose, cx, cy, inner, outer, START_DEG, SWEEP_DEG, TRACK);
        if (fraction > 0.0F) {
            int color = boosting ? HOT : (boost < 12.0F ? EMPTY : ORANGE);
            arc(pose, cx, cy, inner, outer, START_DEG, SWEEP_DEG * fraction, color);
        }
        RenderSystem.disableBlend();

        int shown = Mth.ceil(boost - 0.001F);
        String text = Integer.toString(Mth.clamp(shown, 0, 100));
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0.0F);
        graphics.pose().scale(2.2F, 2.2F, 1.0F);
        graphics.drawCenteredString(mc.font, text, 0, -5, shown <= 0 ? EMPTY : 0xFFFFFFFF);
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy + 15.0F, 0.0F);
        graphics.pose().scale(0.7F, 0.7F, 1.0F);
        graphics.drawCenteredString(mc.font, "BOOST", 0, 0, 0xFFB0B0B0);
        graphics.pose().popPose();
    }

    private static void disc(Matrix4f pose, float cx, float cy, float radius, int argb) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        builder.addVertex(pose, cx, cy, 0.0F).setColor(argb);
        for (int i = 0; i <= 48; i++) {
            double a = Math.PI * 2.0D * i / 48.0D;
            builder.addVertex(pose, cx + (float) Math.sin(a) * radius, cy - (float) Math.cos(a) * radius, 0.0F).setColor(argb);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /** Angles are in degrees, measured clockwise from 12 o'clock. */
    private static void arc(Matrix4f pose, float cx, float cy, float inner, float outer, float startDeg, float sweepDeg, int argb) {
        int steps = Math.max(2, (int) Math.ceil(sweepDeg / 3.0F));
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(startDeg + sweepDeg * i / steps);
            float sx = (float) Math.sin(a);
            float sy = -(float) Math.cos(a);
            builder.addVertex(pose, cx + sx * outer, cy + sy * outer, 0.0F).setColor(argb);
            builder.addVertex(pose, cx + sx * inner, cy + sy * inner, 0.0F).setColor(argb);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }
}
