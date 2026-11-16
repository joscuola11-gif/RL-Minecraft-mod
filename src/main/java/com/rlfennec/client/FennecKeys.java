package com.rlfennec.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings (rebindable under Options > Controls > Rocket League Fennec).
 *
 * <p>They use the normal in-game context on purpose: a custom context that does not conflict with it makes
 * NeoForge treat every binding as "inactive" while a modifier key is held, which would turn the boost
 * (Left Ctrl) off. The default keys do overlap with vanilla ones (Q drop, E inventory, Left Ctrl sprint); while
 * driving, the vanilla drop / inventory actions are swallowed, see {@link ClientGameEvents}.</p>
 */
public final class FennecKeys {
    public static final String CATEGORY = "key.categories.rlfennec";

    public static final KeyMapping BOOST = new KeyMapping(
            "key.rlfennec.boost", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);
    public static final KeyMapping ROLL_LEFT = new KeyMapping(
            "key.rlfennec.roll_left", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, CATEGORY);
    public static final KeyMapping ROLL_RIGHT = new KeyMapping(
            "key.rlfennec.roll_right", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, CATEGORY);
    /** Rocket League's "Air Roll" button: while held, A / D roll the car instead of yawing it. */
    public static final KeyMapping AIR_ROLL = new KeyMapping(
            "key.rlfennec.air_roll", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

    private FennecKeys() {
    }
}
