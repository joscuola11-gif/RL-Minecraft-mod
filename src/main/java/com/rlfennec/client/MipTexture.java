package com.rlfennec.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * A 2048x2048 texture sampled with nearest filtering shimmers terribly when the car is a few
 * blocks away, so the car textures get a full mip chain and trilinear filtering.
 */
final class MipTexture implements AutoCloseable {
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_TEXTURE_MIN_FILTER = 10241;
    private static final int GL_TEXTURE_MAG_FILTER = 10240;
    private static final int GL_LINEAR = 9729;
    private static final int GL_LINEAR_MIPMAP_LINEAR = 9987;

    private int id;

    private MipTexture(int id) {
        this.id = id;
    }

    static MipTexture load(ResourceManager manager, ResourceLocation location) throws IOException {
        try (InputStream in = manager.getResourceOrThrow(location).open()) {
            NativeImage image = NativeImage.read(in);
            int levels = Math.min(8, 31 - Integer.numberOfLeadingZeros(Math.min(image.getWidth(), image.getHeight())));
            NativeImage[] mips = MipmapGenerator.generateMipLevels(new NativeImage[] {image}, levels);
            int id = TextureUtil.generateTextureId();
            TextureUtil.prepareImage(id, levels, image.getWidth(), image.getHeight());
            GlStateManager._bindTexture(id);
            for (int i = 0; i < mips.length; i++) {
                mips[i].upload(i, 0, 0, true);
            }
            GlStateManager._bindTexture(id);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._texParameter(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            return new MipTexture(id);
        }
    }

    int id() {
        return this.id;
    }

    @Override
    public void close() {
        if (this.id != -1) {
            TextureUtil.releaseTextureId(this.id);
            this.id = -1;
        }
    }
}
