package com.rlfennec.physics;

/**
 * Arcade car model, Rocket League style: grippy driving, a jump, a double jump / flip
 * and a boost meter of 100 that lasts three seconds.
 *
 * <p>This class is pure maths (no Minecraft classes) so it can be unit tested. The entity copies its
 * state in, calls {@link #step}, lets Minecraft move/collide the entity, and copies the
 * collision-adjusted velocity back.</p>
 *
 * <p>Units: blocks and ticks (20 ticks = 1 second). Angles follow Minecraft's convention:
 * {@code yaw} 0 = facing +Z (south), positive yaw turns to the right (clockwise seen from above);
 * positive {@code pitch} = nose down.</p>
 */
public final class CarPhysics {
    public static final float MAX_BOOST = 100.0F;

    public static final double GRAVITY = 0.065D;
    public static final double TERMINAL_VELOCITY = 2.2D;

    /** Top speed without boost: 1.1 blocks/tick = 22 blocks/s. */
    public static final double MAX_SPEED = 1.10D;
    /** "Supersonic" speed reached while boosting: 34 blocks/s. */
    public static final double MAX_BOOST_SPEED = 1.70D;
    public static final double MAX_REVERSE_SPEED = 0.45D;

    public static final double ACCELERATION = 0.055D;
    public static final double REVERSE_ACCELERATION = 0.030D;
    public static final double BRAKE = 0.080D;
    public static final double COASTING_DRAG = 0.010D;
    /** about 1.5x gravity, the same ratio as in Rocket League, so boosting at 45 degrees nose-up keeps you climbing. */
    public static final double BOOST_ACCELERATION = 0.100D;
    public static final double OVERSPEED_DECAY = 0.020D;
    /** Fraction of the sideways velocity that survives each tick (lower = more grip). */
    public static final double LATERAL_GRIP = 0.20D;

    public static final double JUMP_VELOCITY = 0.55D;
    public static final double DOUBLE_JUMP_VELOCITY = 0.45D;
    public static final double DODGE_VELOCITY = 0.35D;
    public static final double DODGE_IMPULSE = 0.45D;
    /** Ticks after leaving the ground in which the second jump is still available (1.5 s). */
    public static final int DOUBLE_JUMP_WINDOW = 30;

    /** 100 boost lasts 3 seconds, exactly like in Rocket League (33.3 per second). */
    public static final float BOOST_USE_PER_TICK = MAX_BOOST / 60.0F;

    public static final float AIR_PITCH_RATE = 4.0F;
    public static final float AIR_YAW_RATE = 3.0F;
    public static final float MAX_AIR_PITCH = 85.0F;
    public static final double WATER_DRAG = 0.86D;

    public static final int EVENT_NONE = 0;
    public static final int EVENT_JUMP = 1;
    public static final int EVENT_DOUBLE_JUMP = 2;

    public static final int FLIP_NONE = 0;
    public static final int FLIP_FRONT = 1;
    public static final int FLIP_BACK = 2;
    public static final int FLIP_LEFT = 3;
    public static final int FLIP_RIGHT = 4;

    /** What the driver is asking for this tick. */
    public static final class Input {
        /** -1 (brake / reverse) .. 1 (throttle). Ignored in the air. */
        public float throttle;
        /** -1 (left) .. 1 (right). */
        public float steer;
        /** True only on the tick on which the jump key went down. */
        public boolean jump;
        /** True while the boost key is held. */
        public boolean boost;
        /** Where the nose should point while airborne, degrees, positive = nose down. (The mouse in the real game.) */
        public float pitchTarget;

        public void clear() {
            throttle = 0.0F;
            steer = 0.0F;
            jump = false;
            boost = false;
            pitchTarget = 0.0F;
        }
    }

    // ---- state (copied in/out by the entity) ----
    public double vx;
    public double vy;
    public double vz;
    public float yaw;
    public float pitch;
    public boolean onGround;
    public boolean inWater;

    // ---- own state ----
    public float steer;
    public float boost = MAX_BOOST;
    public int jumpsUsed;
    public int airTicks;
    public int ticksSinceBoost = 1000;
    public float regenPerTick;
    public int regenDelayTicks = 40;

    // ---- results of the last step ----
    public boolean boosting;
    public int event = EVENT_NONE;
    public int flip = FLIP_NONE;

    public double horizontalSpeed() {
        return Math.sqrt(vx * vx + vz * vz);
    }

    /** Speed along the nose of the car, negative when reversing. */
    public double forwardSpeed() {
        double rad = Math.toRadians(yaw);
        return vx * -Math.sin(rad) + vz * Math.cos(rad);
    }

    public void step(Input in) {
        event = EVENT_NONE;
        flip = FLIP_NONE;

        // smoothed steering wheel
        float target = clamp(in.steer, -1.0F, 1.0F);
        float rate = Math.abs(target) > Math.abs(steer) ? 0.35F : 0.55F;
        steer += (target - steer) * rate;
        if (target == 0.0F && Math.abs(steer) < 0.01F) {
            steer = 0.0F;
        }

        // boost meter
        boosting = in.boost && boost > 0.0F && !inWater;
        if (boosting) {
            boost = Math.max(0.0F, boost - BOOST_USE_PER_TICK);
            ticksSinceBoost = 0;
        } else {
            ticksSinceBoost++;
            if (regenPerTick > 0.0F && ticksSinceBoost > regenDelayTicks) {
                boost = Math.min(MAX_BOOST, boost + regenPerTick);
            }
        }

        if (onGround && !inWater) {
            jumpsUsed = 0;
            airTicks = 0;
            groundStep(in);
        } else {
            airTicks++;
            airStep(in);
        }

        if (in.jump) {
            tryJump(in);
        }

        if (vy < -TERMINAL_VELOCITY) {
            vy = -TERMINAL_VELOCITY;
        }
    }

    private void groundStep(Input in) {
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double rx = -fz;
        double rz = fx;

        double fs = vx * fx + vz * fz;
        double ls = vx * rx + vz * rz;

        // turning: sharper when slow, nothing when standing still, inverted when reversing
        double abs = Math.abs(fs);
        double speedFrac = Math.min(abs / MAX_SPEED, 1.0D);
        double maxTurn = 5.5D + (1.8D - 5.5D) * speedFrac;
        double turnScale = Math.min(1.0D, abs / 0.07D);
        double dir = fs >= -0.02D ? 1.0D : -1.0D;
        yaw += (float) (steer * maxTurn * turnScale * dir);
        yaw = wrapDegrees(yaw);

        // longitudinal acceleration
        double accel = 0.0D;
        if (in.throttle > 0.0F) {
            if (fs >= 0.0D) {
                accel = ACCELERATION * in.throttle * clamp01(1.0D - fs / MAX_SPEED);
            } else {
                accel = BRAKE;
            }
        } else if (in.throttle < 0.0F) {
            if (fs > 0.03D) {
                accel = -BRAKE;
            } else {
                accel = -REVERSE_ACCELERATION * -in.throttle * clamp01(1.0D + fs / MAX_REVERSE_SPEED);
            }
        } else if (fs != 0.0D) {
            accel = -Math.signum(fs) * Math.min(Math.abs(fs), COASTING_DRAG);
        }
        if (boosting) {
            accel += BOOST_ACCELERATION;
        }
        fs += accel;

        if (boosting) {
            fs = Math.min(fs, MAX_BOOST_SPEED);
        } else if (fs > MAX_SPEED) {
            fs = Math.max(MAX_SPEED, fs - OVERSPEED_DECAY);
        }
        fs = Math.max(fs, -MAX_REVERSE_SPEED);
        ls *= LATERAL_GRIP;

        // re-project the velocity on the (possibly rotated) car
        rad = Math.toRadians(yaw);
        fx = -Math.sin(rad);
        fz = Math.cos(rad);
        rx = -fz;
        rz = fx;
        vx = fx * fs + rx * ls;
        vz = fz * fs + rz * ls;
        vy = -0.1D; // keeps the wheels glued to the road and the "on ground" flag stable

        pitch += (0.0F - pitch) * 0.5F;
        if (Math.abs(pitch) < 0.2F) {
            pitch = 0.0F;
        }
    }

    private void airStep(Input in) {
        // yaw with A/D, the nose follows the aim (mouse) at a limited rate, so "look up and boost" climbs
        yaw = wrapDegrees(yaw + in.steer * AIR_YAW_RATE);
        float want = clamp(in.pitchTarget, -MAX_AIR_PITCH, MAX_AIR_PITCH);
        pitch += clamp(want - pitch, -AIR_PITCH_RATE, AIR_PITCH_RATE);

        if (inWater) {
            vx *= WATER_DRAG;
            vz *= WATER_DRAG;
            vy = vy * 0.8D + 0.02D;
            return;
        }

        if (boosting) {
            // thrust along the nose, including up/down when the car is pitched
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);
            double cp = Math.cos(pitchRad);
            vx += -Math.sin(yawRad) * cp * BOOST_ACCELERATION;
            vz += Math.cos(yawRad) * cp * BOOST_ACCELERATION;
            vy += -Math.sin(pitchRad) * BOOST_ACCELERATION;
        }

        vy -= GRAVITY;
        vx *= 0.998D;
        vz *= 0.998D;

        if (boosting) {
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (speed > MAX_BOOST_SPEED) {
                double k = MAX_BOOST_SPEED / speed;
                vx *= k;
                vy *= k;
                vz *= k;
            }
        }
    }

    private void tryJump(Input in) {
        boolean grounded = onGround && !inWater;
        if (grounded) {
            vy = JUMP_VELOCITY;
            jumpsUsed = 1;
            airTicks = 0;
            event = EVENT_JUMP;
            return;
        }
        if (airTicks > DOUBLE_JUMP_WINDOW || jumpsUsed >= 2) {
            return;
        }
        if (jumpsUsed == 0) {
            // drove off a ledge: the first jump is still available
            vy = JUMP_VELOCITY * 0.9D;
            jumpsUsed = 1;
            event = EVENT_JUMP;
            return;
        }

        jumpsUsed = 2;
        event = EVENT_DOUBLE_JUMP;
        double yawRad = Math.toRadians(yaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double rx = -fz;
        double rz = fx;

        if (in.throttle > 0.3F) {
            flip = FLIP_FRONT;
            addDodge(fx, fz);
        } else if (in.throttle < -0.3F) {
            flip = FLIP_BACK;
            addDodge(-fx, -fz);
        } else if (in.steer < -0.3F) {
            flip = FLIP_LEFT;
            addDodge(-rx, -rz);
        } else if (in.steer > 0.3F) {
            flip = FLIP_RIGHT;
            addDodge(rx, rz);
        } else {
            vy = DOUBLE_JUMP_VELOCITY;
        }
    }

    private void addDodge(double dx, double dz) {
        vy = Math.max(vy, 0.0D) * 0.3D + DODGE_VELOCITY;
        vx += dx * DODGE_IMPULSE;
        vz += dz * DODGE_IMPULSE;
        double h = horizontalSpeed();
        if (h > MAX_BOOST_SPEED) {
            double k = MAX_BOOST_SPEED / h;
            vx *= k;
            vz *= k;
        }
    }

    private static float wrapDegrees(float deg) {
        deg %= 360.0F;
        if (deg >= 180.0F) {
            deg -= 360.0F;
        }
        if (deg < -180.0F) {
            deg += 360.0F;
        }
        return deg;
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : Math.min(v, 1.0D);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }
}
