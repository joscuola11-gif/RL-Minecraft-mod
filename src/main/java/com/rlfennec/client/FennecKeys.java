package com.rlfennec.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rlfennec.entity.FennecEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Key bindings (rebindable under Options > Controls > Rocket League Fennec). */
public final class FennecKeys {
    /** Only active (and only conflicting with itself) while the player drives a Fennec. */
    public static final IKeyConflictContext DRIVING = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.player.getVehicle() instanceof FennecEntity && mc.screen == null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return this == other;
        }
    };

    public static final KeyMapping BOOST = new KeyMapping(
            "key.rlfennec.boost", DRIVING, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, "key.categories.rlfennec");

    private FennecKeys() {
    }
}
