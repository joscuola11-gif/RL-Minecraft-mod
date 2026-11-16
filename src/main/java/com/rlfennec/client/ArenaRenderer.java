package com.rlfennec.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.rlfennec.RLFennec;
import com.rlfennec.world.ArenaWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Draws the arena (the real curved shell, the pitch and the glowing lines and lights) in the arena dimension, and keeps
 * that dimension free of rain.
 */
@EventBusSubscriber(modid = RLFennec.MOD_ID, value = Dist.CLIENT)
public final class ArenaRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation FLOOR = RLFennec.id("textures/arena/floor.png");

    private static VertexBuffer shell;
    private static VertexBuffer glow;
    private static VertexBuffer floor;
    private static MipTexture floorTexture;
    private static boolean failed;

    private ArenaRenderer() {
    }

    private static boolean ensureLoaded(Minecraft mc) {
        if (shell != null) {
            return true;
        }
        if (failed) {
            return false;
        }
        try {
            ArenaMesh mesh = ArenaMesh.build();
            floorTexture = MipTexture.load(mc.getResourceManager(), FLOOR);
            floor = uploadTextured(mesh.floor);
            glow = uploadFlat(mesh.glow);
            shell = uploadFlat(mesh.shell);
            return true;
        } catch (Exception e) {
            LOGGER.error("Could not build the arena", e);
            failed = true;
            return false;
        }
    }

    /** Unlit, textured (the pitch). */
    private static VertexBuffer uploadTextured(ArenaMesh.Mesh mesh) {
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(mesh.vertexCount * 24 + 64)) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < mesh.vertexCount; i++) {
                int o = i * ArenaMesh.FLOATS_PER_VERTEX;
                float[] d = mesh.data;
                builder.addVertex(d[o], d[o + 1], d[o + 2]).setUv(d[o + 6], d[o + 7]).setColor(d[o + 8], d[o + 9], d[o + 10], 1.0F);
            }
            return upload(builder.buildOrThrow());
        }
    }

    /** Unlit (lines and lights: they glow). */
    private static VertexBuffer uploadFlat(ArenaMesh.Mesh mesh) {
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(mesh.vertexCount * 16 + 64)) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < mesh.vertexCount; i++) {
                int o = i * ArenaMesh.FLOATS_PER_VERTEX;
                float[] d = mesh.data;
                builder.addVertex(d[o], d[o + 1], d[o + 2]).setColor(d[o + 8], d[o + 9], d[o + 10], 1.0F);
            }
            return upload(builder.buildOrThrow());
        }
    }

    private static VertexBuffer upload(MeshData data) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(data);
        VertexBuffer.unbind();
        return buffer;
    }

    private static void release() {
        for (VertexBuffer b : new VertexBuffer[] {shell, glow, floor}) {
            if (b != null) {
                b.close();
            }
        }
        shell = glow = floor = null;
        for (MipTexture t : new MipTexture[] {floorTexture}) {
            if (t != null) {
                t.close();
            }
        }
        floorTexture = null;
        failed = false;
    }

    /** Drops every GPU resource (resource reload, leaving the world); they are rebuilt when the arena is drawn again. */
    public static void reset() {
        release();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!ArenaWorld.isArena(mc.level) || !ensureLoaded(mc)) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f modelView = new Matrix4f(event.getModelViewMatrix())
                .translate((float) (ArenaWorld.ORIGIN_X - cam.x), (float) (ArenaWorld.ORIGIN_Y - cam.y), (float) (ArenaWorld.ORIGIN_Z - cam.z));
        Matrix4f projection = event.getProjectionMatrix();

        // the arena is lit by its own lights, not by the sky: everything is drawn unlit
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, floorTexture.id());
        draw(floor, modelView, projection);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        draw(shell, modelView, projection);
        draw(glow, modelView, projection);

        VertexBuffer.unbind();
        RenderSystem.enableCull();
    }

    private static void draw(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection) {
        buffer.bind();
        buffer.drawWithShader(modelView, projection, RenderSystem.getShader());
    }

    /** It never rains in the arena. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (ArenaWorld.isArena(level)) {
            level.setRainLevel(0.0F);
            level.setThunderLevel(0.0F);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        release();
    }
}
