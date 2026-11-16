package com.rlfennec.input;

import javax.annotation.Nullable;

/**
 * Turns a gamepad snapshot plus the bindings into car controls. Pure maths (no GLFW, no Minecraft) so it is unit tested.
 *
 * <p>Default layout (every input can be rebound, see {@link PadAction}):</p>
 * <pre>
 *   Right / left trigger ... accelerate / brake and reverse
 *   Left stick ←→ ......... steer, in the air yaw (or roll while the air roll button is held)
 *   Left stick ↑↓ ......... in the air nose down / up (stick forward = nose down, like Rocket League)
 *   A ..................... jump / double jump / flip
 *   B ..................... boost
 *   X ..................... air roll (hold, then the steering rolls the car)
 *   LB / RB ............... air roll left / right
 *   Y ..................... get into the nearest Fennec / get out
 * </pre>
 */
public final class GamepadMapper {
    public static final float STICK_DEAD_ZONE = 0.15F;
    public static final float TRIGGER_DEAD_ZONE = 0.05F;
    /** How far an axis has to be pushed to count as "pressed" when it is bound to a button-like action or captured for rebinding. */
    public static final float PRESS_THRESHOLD = 0.6F;

    // GLFW gamepad layout
    public static final int AXIS_LEFT_X = 0;
    public static final int AXIS_LEFT_Y = 1;
    public static final int AXIS_RIGHT_X = 2;
    public static final int AXIS_RIGHT_Y = 3;
    public static final int AXIS_LEFT_TRIGGER = 4;
    public static final int AXIS_RIGHT_TRIGGER = 5;
    public static final int BUTTON_A = 0;
    public static final int BUTTON_B = 1;
    public static final int BUTTON_X = 2;
    public static final int BUTTON_Y = 3;
    public static final int BUTTON_LEFT_BUMPER = 4;
    public static final int BUTTON_RIGHT_BUMPER = 5;

    private GamepadMapper() {
    }

    /** Raw state: sticks are -1..1 with up = -1 (as GLFW reports them), triggers are already 0..1. */
    public static final class State {
        public final float[] axes = new float[PadInput.AXIS_COUNT];
        public final boolean[] buttons = new boolean[PadInput.BUTTON_COUNT];
    }

    public static final class Controls {
        public float throttle;
        public float steer;
        public float pitch;
        public float roll;
        public boolean airRollFree;
        public boolean jump;
        public boolean boost;
        public boolean enterExit;
    }

    public static Controls map(State s, GamepadBindings b) {
        Controls c = new Controls();
        c.steer = value(s, b.get(PadAction.STEER_RIGHT)) - value(s, b.get(PadAction.STEER_LEFT));
        c.throttle = value(s, b.get(PadAction.ACCELERATE)) - value(s, b.get(PadAction.BRAKE));
        c.pitch = value(s, b.get(PadAction.NOSE_DOWN)) - value(s, b.get(PadAction.NOSE_UP));
        c.roll = value(s, b.get(PadAction.ROLL_RIGHT)) - value(s, b.get(PadAction.ROLL_LEFT));
        c.airRollFree = pressed(s, b.get(PadAction.AIR_ROLL_FREE));
        c.jump = pressed(s, b.get(PadAction.JUMP));
        c.boost = pressed(s, b.get(PadAction.BOOST));
        c.enterExit = pressed(s, b.get(PadAction.ENTER_EXIT));
        return c;
    }

    /** 0..1: how much this input is pressed. */
    public static float value(State s, PadInput input) {
        switch (input.kind()) {
            case BUTTON:
                return s.buttons[input.index()] ? 1.0F : 0.0F;
            case AXIS: {
                int axis = input.index();
                if (axis >= AXIS_LEFT_TRIGGER) {
                    return trigger(s.axes[axis]);
                }
                int pair = axis / 2 * 2;
                float[] stick = deadZone(s.axes[pair], s.axes[pair + 1]);
                return Math.max(0.0F, input.sign() * stick[axis - pair]);
            }
            default:
                return 0.0F;
        }
    }

    public static boolean pressed(State s, PadInput input) {
        return value(s, input) > 0.5F;
    }

    /**
     * The first input that is being pressed right now, used to capture a new binding. Buttons win over axes.
     * @return {@code null} when nothing is pressed
     */
    @Nullable
    public static PadInput detectPressed(State s) {
        for (int i = 0; i < PadInput.BUTTON_COUNT; i++) {
            if (s.buttons[i]) {
                return PadInput.button(i);
            }
        }
        PadInput strongest = null;
        float best = PRESS_THRESHOLD;
        for (int axis = 0; axis < PadInput.AXIS_COUNT; axis++) {
            float v = s.axes[axis];
            if (axis >= AXIS_LEFT_TRIGGER) {
                if (v > best) {
                    best = v;
                    strongest = PadInput.axis(axis, 1);
                }
            } else if (Math.abs(v) > best) {
                best = Math.abs(v);
                strongest = PadInput.axis(axis, v < 0 ? -1 : 1);
            }
        }
        return strongest;
    }

    /** Radial dead zone that is rescaled so the stick still reaches 1.0 at the rim. */
    public static float[] deadZone(float x, float y) {
        float mag = (float) Math.sqrt(x * x + y * y);
        if (mag < STICK_DEAD_ZONE) {
            return new float[] {0.0F, 0.0F};
        }
        float scaled = Math.min(1.0F, (mag - STICK_DEAD_ZONE) / (1.0F - STICK_DEAD_ZONE));
        return new float[] {x / mag * scaled, y / mag * scaled};
    }

    private static float trigger(float v) {
        if (v < TRIGGER_DEAD_ZONE) {
            return 0.0F;
        }
        return Math.min(1.0F, (v - TRIGGER_DEAD_ZONE) / (1.0F - TRIGGER_DEAD_ZONE));
    }

    /**
     * GLFW reports triggers from -1 (released) to 1 (pressed). Some drivers report 0 until the trigger is first
     * touched, which would look like "half pressed" forever; {@code seenReleased} tells whether the trigger has
     * been seen at rest, until then a raw 0 is treated as released.
     */
    public static float normaliseTrigger(float raw, boolean seenReleased) {
        if (!seenReleased && raw == 0.0F) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (raw + 1.0F) * 0.5F));
    }
}
