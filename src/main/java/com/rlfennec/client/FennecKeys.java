package com.rlfennec.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rlfennec.entity.FennecEntity;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * Every keyboard / mouse action of the car as a normal Minecraft key binding, so all of them can be changed under
 * Options > Controls > Key Binds > Rocket League Fennec.
 *
 * <p>Their state is read straight from the keyboard ({@link #isDown}) instead of through NeoForge's key mapping
 * dispatch: that way the bindings use their own conflict contexts, so they do not show up in red against the
 * vanilla keys that happen to share the default, and holding a modifier key
 * (Ctrl, Alt, Shift) can never switch another binding off.</p>
 */
public final class FennecKeys {
    public static final String CATEGORY = "key.categories.rlfennec";

    /**
     * Contexts: actions of the same context conflict with each other, {@link #GENERAL} actions (always available)
     * with everything of the mod, ground and air actions do not (W accelerates on the ground and tips the nose down in
     * the air, so both can have the same key). None of them conflicts with vanilla.
     */
    public enum Context implements IKeyConflictContext {
        GENERAL, GROUND, AIR;

        @Override
        public boolean isActive() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.player.getVehicle() instanceof FennecEntity;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            if (!(other instanceof Context context)) {
                return false;
            }
            return this == GENERAL || context == GENERAL || this == context;
        }
    }

    public static final KeyMapping ACCELERATE = key("throttle", Context.GROUND, GLFW.GLFW_KEY_W);
    public static final KeyMapping BRAKE = key("reverse", Context.GROUND, GLFW.GLFW_KEY_S);
    public static final KeyMapping STEER_LEFT = key("turn_left", Context.GENERAL, GLFW.GLFW_KEY_A);
    public static final KeyMapping STEER_RIGHT = key("turn_right", Context.GENERAL, GLFW.GLFW_KEY_D);
    public static final KeyMapping NOSE_DOWN = key("pitch_down", Context.AIR, GLFW.GLFW_KEY_W);
    public static final KeyMapping NOSE_UP = key("pitch_up", Context.AIR, GLFW.GLFW_KEY_S);
    public static final KeyMapping JUMP = key("hop", Context.GENERAL, GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping BOOST = key("boost", Context.GENERAL, GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final KeyMapping ROLL_LEFT = key("roll_left", Context.AIR, GLFW.GLFW_KEY_Q);
    public static final KeyMapping ROLL_RIGHT = key("roll_right", Context.AIR, GLFW.GLFW_KEY_E);
    /** Rocket League's "Air Roll" button: while held, the steering keys roll the car instead of yawing it. */
    public static final KeyMapping AIR_ROLL = key("air_roll", Context.AIR, GLFW.GLFW_KEY_LEFT_ALT);
    /** Get out of the car (it replaces the vanilla sneak key as the way to dismount while driving). */
    public static final KeyMapping EXIT = key("leave", Context.GENERAL, GLFW.GLFW_KEY_LEFT_SHIFT);

    public static final List<KeyMapping> ALL = List.of(ACCELERATE, BRAKE, STEER_LEFT, STEER_RIGHT, NOSE_DOWN, NOSE_UP,
            JUMP, BOOST, ROLL_LEFT, ROLL_RIGHT, AIR_ROLL, EXIT);

    private FennecKeys() {
    }

    private static KeyMapping key(String name, Context context, int key) {
        return new KeyMapping("key.rlfennec." + name, context, InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    /**
     * Whether the key (keyboard key or mouse button, plus its modifier) bound to the mapping is held down right now.
     * An unbound (empty) action is never pressed.
     */
    public static boolean isDown(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key.equals(InputConstants.UNKNOWN)) {
            return false;
        }
        KeyModifier modifier = mapping.getKeyModifier();
        if (modifier != KeyModifier.NONE && !modifier.isActive(KeyConflictContext.IN_GAME)) {
            return false;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }
}
