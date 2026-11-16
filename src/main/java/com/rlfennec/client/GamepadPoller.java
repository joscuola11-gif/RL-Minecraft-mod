package com.rlfennec.client;

import com.rlfennec.input.GamepadMapper;
import com.rlfennec.input.PadInput;
import com.rlfennec.registry.ClientConfig;
import javax.annotation.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * Reads the first connected gamepad through GLFW (Xbox, PlayStation and anything else that is in GLFW's
 * gamepad mapping database). Minecraft itself ignores gamepads, so this only drives the car.
 */
final class GamepadPoller {
    private static final GLFWGamepadState STATE = GLFWGamepadState.create();
    private static final GamepadMapper.State SNAPSHOT = new GamepadMapper.State();
    private static final boolean[] TRIGGER_SEEN_RELEASED = new boolean[2];
    private static int joystick = -1;

    private GamepadPoller() {
    }

    /** @return the car controls for the current bindings, or {@code null} when no gamepad is connected. */
    @Nullable
    static GamepadMapper.Controls poll() {
        GamepadMapper.State raw = readRaw();
        return raw == null ? null : GamepadMapper.map(raw, ClientConfig.bindings());
    }

    /** @return the raw state (shared instance, do not keep it), or {@code null} when no gamepad is connected. */
    @Nullable
    static GamepadMapper.State readRaw() {
        if (!read()) {
            return null;
        }
        return SNAPSHOT;
    }

    /** Name of the connected gamepad, or {@code null}. */
    @Nullable
    static String name() {
        return read() ? GLFW.glfwGetGamepadName(joystick) : null;
    }

    private static boolean read() {
        if (joystick >= 0 && !(GLFW.glfwJoystickIsGamepad(joystick) && GLFW.glfwGetGamepadState(joystick, STATE))) {
            joystick = -1;
        }
        if (joystick < 0) {
            for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
                if (GLFW.glfwJoystickIsGamepad(jid) && GLFW.glfwGetGamepadState(jid, STATE)) {
                    joystick = jid;
                    TRIGGER_SEEN_RELEASED[0] = false;
                    TRIGGER_SEEN_RELEASED[1] = false;
                    break;
                }
            }
            if (joystick < 0) {
                return false;
            }
        }

        for (int axis = 0; axis < PadInput.AXIS_COUNT; axis++) {
            float raw = STATE.axes(axis);
            if (axis == GamepadMapper.AXIS_LEFT_TRIGGER || axis == GamepadMapper.AXIS_RIGHT_TRIGGER) {
                int index = axis - GamepadMapper.AXIS_LEFT_TRIGGER;
                if (raw <= -0.9F) {
                    TRIGGER_SEEN_RELEASED[index] = true;
                }
                raw = GamepadMapper.normaliseTrigger(raw, TRIGGER_SEEN_RELEASED[index]);
            }
            SNAPSHOT.axes[axis] = raw;
        }
        for (int button = 0; button < PadInput.BUTTON_COUNT; button++) {
            SNAPSHOT.buttons[button] = STATE.buttons(button) == GLFW.GLFW_PRESS;
        }
        return true;
    }
}
