package com.rlfennec.client;

import com.rlfennec.input.GamepadMapper;
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

    /** @return the current controls, or {@code null} when no gamepad is connected. */
    static GamepadMapper.Controls poll() {
        if (!read()) {
            return null;
        }
        return GamepadMapper.map(SNAPSHOT);
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

        SNAPSHOT.leftX = STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X);
        SNAPSHOT.leftY = STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
        SNAPSHOT.leftTrigger = trigger(0, STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER));
        SNAPSHOT.rightTrigger = trigger(1, STATE.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER));
        SNAPSHOT.a = pressed(GLFW.GLFW_GAMEPAD_BUTTON_A);
        SNAPSHOT.b = pressed(GLFW.GLFW_GAMEPAD_BUTTON_B);
        SNAPSHOT.x = pressed(GLFW.GLFW_GAMEPAD_BUTTON_X);
        SNAPSHOT.y = pressed(GLFW.GLFW_GAMEPAD_BUTTON_Y);
        SNAPSHOT.leftBumper = pressed(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER);
        SNAPSHOT.rightBumper = pressed(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER);
        return true;
    }

    private static float trigger(int index, float raw) {
        if (raw <= -0.9F) {
            TRIGGER_SEEN_RELEASED[index] = true;
        }
        return GamepadMapper.normaliseTrigger(raw, TRIGGER_SEEN_RELEASED[index]);
    }

    private static boolean pressed(int button) {
        return STATE.buttons(button) == GLFW.GLFW_PRESS;
    }
}
