package com.rlfennec.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.rlfennec.RLFennec;
import com.rlfennec.entity.FennecEntity;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.server.packs.resources.ResourceManager;
import com.mojang.math.Axis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * The Fennec mesh (58 851 vertices / 84 823 triangles), uploaded once to the GPU as one vertex
 * buffer per part and drawn with the vanilla entity shader. Doing it with cached buffers instead of
 * submitting 85k triangles to the vanilla buffer builder every frame keeps the CPU cost tiny.
 */
public final class FennecModel {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation MESH = RLFennec.id("models/entity/fennec.bin");
    private static final ResourceLocation TEX_CHASSIS = RLFennec.id("textures/entity/fennec_chassis.png");
    private static final ResourceLocation TEX_RIM = RLFennec.id("textures/entity/alpha_rim.png");
    private static final ResourceLocation TEX_SOLID = RLFennec.id("textures/entity/solid.png");

    // vanilla level lighting directions (see com.mojang.blaze3d.platform.Lighting)
    private static final Vector3f LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    private static final Vector3f NETHER_LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f NETHER_LIGHT_1 = new Vector3f(-0.2F, -1.0F, 0.7F).normalize();

    private static final float[] PAINT = {0.00F, 0.40F, 0.91F};
    private static final float[] TYRE = {0.07F, 0.07F, 0.07F};
    private static final float[] GLASS = {0.03F, 0.04F, 0.06F};
    private static final float[] BLACK_PAINT = {0.07F, 0.07F, 0.07F};
    private static final float[] HEADLIGHT = {1.00F, 0.92F, 0.94F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};

    /** Height of the centre of mass: the car pitches / rolls around this point. */
    private static final float PIVOT_HEIGHT = 0.45F;

    private static final String[] WHEELS = {"fr", "fl", "br", "bl"};

    private static Map<String, Part> parts;
    private static MipTexture chassisTexture;
    private static MipTexture rimTexture;
    private static MipTexture solidTexture;
    private static boolean failed;

    private FennecModel() {
    }

    private record Part(VertexBuffer buffer, float px, float py, float pz) {
    }

    /** Drops every GPU resource, they are rebuilt lazily (called on resource reload). */
    public static void reset() {
        if (parts != null) {
            parts.values().forEach(p -> p.buffer().close());
            parts = null;
        }
        for (MipTexture t : new MipTexture[] {chassisTexture, rimTexture, solidTexture}) {
            if (t != null) {
                t.close();
            }
        }
        chassisTexture = rimTexture = solidTexture = null;
        failed = false;
    }

    private static boolean ensureLoaded() {
        if (parts != null) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            chassisTexture = MipTexture.load(manager, TEX_CHASSIS);
            rimTexture = MipTexture.load(manager, TEX_RIM);
            solidTexture = MipTexture.load(manager, TEX_SOLID);
            try (InputStream in = manager.getResourceOrThrow(MESH).open()) {
                parts = parse(ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN));
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Could not load the Fennec model", e);
            failed = true;
            return false;
        }
    }

    private static Map<String, Part> parse(ByteBuffer buf) {
        int magic = buf.getInt();
        int version = buf.getInt();
        buf.getFloat(); // baked scale
        int count = buf.getInt();
        if (magic != 0x434E4E46 || version != 1) {
            throw new IllegalStateException("Bad fennec.bin header");
        }
        Map<String, Part> out = new LinkedHashMap<>();
        for (int g = 0; g < count; g++) {
            byte[] name = new byte[buf.getShort() & 0xFFFF];
            buf.get(name);
            float px = buf.getFloat();
            float py = buf.getFloat();
            float pz = buf.getFloat();
            int vertexCount = buf.getInt();
            int indexCount = buf.getInt();
            float[] v = new float[vertexCount * 8];
            for (int i = 0; i < v.length; i++) {
                v[i] = buf.getFloat();
            }
            int[] idx = new int[indexCount];
            for (int i = 0; i < indexCount; i++) {
                idx[i] = buf.getShort() & 0xFFFF;
            }
            out.put(new String(name, StandardCharsets.UTF_8), new Part(upload(v, idx), px, py, pz));
        }
        return out;
    }

    private static VertexBuffer upload(float[] v, int[] idx) {
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(idx.length * 36 + 64)) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
            for (int index : idx) {
                int o = index * 8;
                builder.addVertex(v[o], v[o + 1], v[o + 2])
                        .setColor(255, 255, 255, 255)
                        .setUv(v[o + 6], v[o + 7])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(v[o + 3], v[o + 4], v[o + 5]);
            }
            MeshData mesh = builder.buildOrThrow();
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
            return buffer;
        }
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the whole car. The pose stack must already contain the entity position and the
     * orientation of the car body (model space: +X forward, +Y up, +Z right, origin on the ground).
     */
    public static void render(FennecEntity car, float partialTick, PoseStack poseStack, int packedLight) {
        if (!ensureLoaded()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float[] light = sampleLight(mc, packedLight);

        mc.gameRenderer.lightTexture().turnOnLightLayer();
        mc.gameRenderer.overlayTexture().setupOverlayColor();
        RenderSystem.setShader(GameRenderer::getRendertypeEntityCutoutNoCullShader);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        float roll = car.getWheelRoll(partialTick) * Mth.RAD_TO_DEG;
        float steer = car.getSteerAngle(partialTick);

        // pass 1: the detailed chassis atlas
        RenderSystem.setShaderTexture(0, chassisTexture.id());
        drawBody(poseStack, "chassis", WHITE, light, false);

        // pass 2: flat coloured parts (they sample a white texel and are tinted)
        RenderSystem.setShaderTexture(0, solidTexture.id());
        drawBody(poseStack, "body", PAINT, light, false);
        drawBody(poseStack, "paint", BLACK_PAINT, light, false);
        drawBody(poseStack, "window", GLASS, light, false);
        drawBody(poseStack, "headlights", HEADLIGHT, light, true);
        for (String wheel : WHEELS) {
            drawWheel(poseStack, "wheel_" + wheel + "_tread", wheel, roll, steer, TYRE, light);
        }

        // pass 3: gold rims
        RenderSystem.setShaderTexture(0, rimTexture.id());
        for (String wheel : WHEELS) {
            drawWheel(poseStack, "wheel_" + wheel + "_rim", wheel, roll, steer, WHITE, light);
        }

        // restore the state the rest of the frame expects
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (mc.level != null && mc.level.effects().constantAmbientLight()) {
            Lighting.setupNetherLevel();
        } else {
            Lighting.setupLevel();
        }
        VertexBuffer.unbind();
        RenderSystem.enableCull();
        mc.gameRenderer.overlayTexture().teardownOverlayColor();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
    }

    private static void drawBody(PoseStack poseStack, String name, float[] tint, float[] light, boolean emissive) {
        Part part = parts.get(name);
        if (part == null) {
            return;
        }
        draw(part, poseStack, tint, light, emissive);
    }

    private static void drawWheel(PoseStack poseStack, String name, String wheel, float rollDeg, float steerDeg, float[] tint, float[] light) {
        Part part = parts.get(name);
        if (part == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(part.px(), part.py(), part.pz());
        if (wheel.charAt(0) == 'f') {
            // the front wheels turn towards the inside of the corner (positive = right turn)
            poseStack.mulPose(Axis.YP.rotationDegrees(-steerDeg));
        }
        poseStack.mulPose(Axis.ZP.rotationDegrees(-rollDeg));
        draw(part, poseStack, tint, light, false);
        poseStack.popPose();
    }

    private static void draw(Part part, PoseStack poseStack, float[] tint, float[] light, boolean emissive) {
        PoseStack.Pose pose = poseStack.last();

        // The baked normals are in model space but the shader expects world space normals, so rotate the
        // light directions into model space instead (the matrix is a pure rotation, its inverse is its transpose).
        Matrix3f toModel = pose.normal().transpose(new Matrix3f());
        boolean nether = Minecraft.getInstance().level != null && Minecraft.getInstance().level.effects().constantAmbientLight();
        Vector3f l0 = toModel.transform(nether ? NETHER_LIGHT_0 : LIGHT_0, new Vector3f());
        Vector3f l1 = toModel.transform(nether ? NETHER_LIGHT_1 : LIGHT_1, new Vector3f());
        RenderSystem.setShaderLights(l0, l1);

        float r = tint[0];
        float g = tint[1];
        float b = tint[2];
        if (!emissive) {
            r *= light[0];
            g *= light[1];
            b *= light[2];
        }
        RenderSystem.setShaderColor(r, g, b, 1.0F);

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.pose());
        part.buffer().bind();
        part.buffer().drawWithShader(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
    }

    /**
     * Light colour at the car's position, relative to a full-bright (15/15) vertex:
     * the buffer is baked at full brightness and this factor darkens it.
     */
    private static float[] sampleLight(Minecraft mc, int packedLight) {
        NativeImage map = mc.gameRenderer.lightTexture().lightPixels;
        int block = LightTexture.block(packedLight);
        int sky = LightTexture.sky(packedLight);
        int here = map.getPixelRGBA(block, sky);
        int full = map.getPixelRGBA(15, 15);
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            int shift = i * 8; // ABGR
            float a = (here >> shift & 0xFF) / 255.0F;
            float f = Math.max(1.0F / 255.0F, (full >> shift & 0xFF) / 255.0F);
            out[i] = Math.min(1.0F, a / f);
        }
        return out;
    }

    public static float pivotHeight() {
        return PIVOT_HEIGHT;
    }
}
