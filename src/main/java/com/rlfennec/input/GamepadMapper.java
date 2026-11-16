package com.rlfennec.input;

/**
 * Turns a gamepad snapshot into car controls. Pure maths (no GLFW, no Minecraft) so it is unit tested.
 *
 * <pre>
 *   Left stick X ..... steer, in the air yaw (or roll while X is held)
 *   Left stick Y ..... in the air pitch (stick forward = nose down, like Rocket League)
 *   Right trigger .... accelerate           Left trigger ... brake / reverse
 *   A ................ jump / double jump / flip
 *   B ................ boost
 *   X ................ air roll (hold, then the left stick rolls the car)
 *   Left / right bumper ... air roll left / right
 *   Y ................ get into the nearest Fennec / get out
 * </pre>
 */
public final class GamepadMapper {
    public static final float STICK_DEAD_ZONE = 0.15F;
    public static final float TRIGGER_DEAD_ZONE = 0.05F;

    private GamepadMapper() {
    }

    /** Raw state. Stick axes are -1..1 with up = -1 (as GLFW reports them), triggers are already 0..1. */
    public static final class State {
        public float leftX;
        public float leftY;
        public float leftTrigger;
        public float rightTrigger;
        public boolean a;
        public boolean b;
        public boolean x;
        public boolean y;
        public boolean leftBumper;
        public boolean rightBumper;
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

    public static Controls map(State s) {
        Controls c = new Controls();
        float[] stick = deadZone(s.leftX, s.leftY);
        c.steer = stick[0];
        c.pitch = 0.0F - stick[1]; // (not -x: that would turn 0 into -0)
        c.throttle = trigger(s.rightTrigger) - trigger(s.leftTrigger);
        c.roll = (s.rightBumper ? 1.0F : 0.0F) - (s.leftBumper ? 1.0F : 0.0F);
        c.airRollFree = s.x;
        c.jump = s.a;
        c.boost = s.b;
        c.enterExit = s.y;
        return c;
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
