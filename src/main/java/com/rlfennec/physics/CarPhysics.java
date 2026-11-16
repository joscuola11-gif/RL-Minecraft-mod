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
 * positive {@code pitch} = nose down; positive {@code roll} = right side down. The orientation is
 * {@code yaw}, then {@code pitch}, then {@code roll}, each about the axes of the car.</p>
 *
 * <p>In the air the car rotates like in Rocket League: pitch, yaw and roll are rotations about the
 * <em>car's own</em> right, up and forward axes, so after a 90 degree air roll "yaw" points the nose
 * up or down.</p>
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

    /** Maximum rotation speed in the air, degrees per tick, about the car's right / up / forward axis. */
    public static final float AIR_PITCH_RATE = 7.0F;
    public static final float AIR_YAW_RATE = 5.0F;
    public static final float AIR_ROLL_RATE = 12.0F;
    /** How fast the rotation speed follows the input (1 = instantly). Without input the car stops turning quickly. */
    public static final float AIR_ANGULAR_RESPONSE = 0.4F;
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
        /** -1 (brake / reverse) .. 1 (throttle). Only used on the ground. */
        public float throttle;
        /** -1 (left) .. 1 (right). On the ground it steers, in the air it yaws (or rolls, see {@link #airRollFree}). */
        public float steer;
        /** In the air: -1 nose up .. 1 nose down (stick forward = nose down, like Rocket League). */
        public float pitch;
        /** In the air: -1 roll left .. 1 roll right. */
        public float roll;
        /** While held, {@link #steer} rolls the car instead of yawing it (Rocket League's "Air Roll" button). */
        public boolean airRollFree;
        /** True only on the tick on which the jump key went down. */
        public boolean jump;
        /** True while the boost key is held. */
        public boolean boost;

        public void clear() {
            throttle = 0.0F;
            steer = 0.0F;
            pitch = 0.0F;
            roll = 0.0F;
            airRollFree = false;
            jump = false;
            boost = false;
        }
    }

    // ---- state (copied in/out by the entity) ----
    public double vx;
    public double vy;
    public double vz;
    public float yaw;
    public float pitch;
    public float roll;
    public boolean onGround;
    public boolean inWater;

    // ---- own state ----
    public float steer;
    private float angularPitch;
    private float angularYaw;
    private float angularRoll;
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

        levelOut();
    }

    /** Back on the road (or in the water) the car stands upright again, smoothly. */
    private void levelOut() {
        angularPitch = 0.0F;
        angularYaw = 0.0F;
        angularRoll = 0.0F;
        pitch *= 0.5F;
        roll = wrapDegrees(roll) * 0.5F;
        if (Math.abs(pitch) < 0.2F) {
            pitch = 0.0F;
        }
        if (Math.abs(roll) < 0.2F) {
            roll = 0.0F;
        }
    }

    private void airStep(Input in) {
        if (inWater) {
            levelOut();
        } else {
            rotateInAir(in);
        }

        if (inWater) {
            vx *= WATER_DRAG;
            vz *= WATER_DRAG;
            vy = vy * 0.8D + 0.02D;
            return;
        }

        if (boosting) {
            // thrust along the nose, including up/down when the car is pitched
            double[] nose = forward(yaw, pitch);
            vx += nose[0] * BOOST_ACCELERATION;
            vy += nose[1] * BOOST_ACCELERATION;
            vz += nose[2] * BOOST_ACCELERATION;
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

    /**
     * Pitch (W/S), yaw (A/D) and roll (Q/E) in the air. Every rotation is about an axis of the car:
     * pitch about its right axis, yaw about its up axis, roll about its forward axis.
     */
    private void rotateInAir(Input in) {
        float yawInput = in.airRollFree ? 0.0F : in.steer;
        float rollInput = clamp(in.roll + (in.airRollFree ? in.steer : 0.0F), -1.0F, 1.0F);
        angularPitch += (clamp(in.pitch, -1.0F, 1.0F) * AIR_PITCH_RATE - angularPitch) * AIR_ANGULAR_RESPONSE;
        angularYaw += (clamp(yawInput, -1.0F, 1.0F) * AIR_YAW_RATE - angularYaw) * AIR_ANGULAR_RESPONSE;
        angularRoll += (rollInput * AIR_ROLL_RATE - angularRoll) * AIR_ANGULAR_RESPONSE;
        if (Math.abs(angularPitch) < 0.01F && Math.abs(angularYaw) < 0.01F && Math.abs(angularRoll) < 0.01F) {
            return;
        }

        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        basis(yaw, pitch, roll, f, u, r);

        // nose down: f tips towards -u
        double a = Math.toRadians(angularPitch);
        double c = Math.cos(a);
        double s = Math.sin(a);
        for (int i = 0; i < 3; i++) {
            double fi = f[i];
            f[i] = fi * c - u[i] * s;
            u[i] = u[i] * c + fi * s;
        }
        // turn right: f tips towards r
        a = Math.toRadians(angularYaw);
        c = Math.cos(a);
        s = Math.sin(a);
        for (int i = 0; i < 3; i++) {
            double fi = f[i];
            f[i] = fi * c + r[i] * s;
            r[i] = r[i] * c - fi * s;
        }
        // roll right: u tips towards r (the right side goes down)
        a = Math.toRadians(angularRoll);
        c = Math.cos(a);
        s = Math.sin(a);
        for (int i = 0; i < 3; i++) {
            double ui = u[i];
            u[i] = ui * c + r[i] * s;
            r[i] = r[i] * c - ui * s;
        }

        // keep it a clean rotation
        normalize(f);
        double dot = u[0] * f[0] + u[1] * f[1] + u[2] * f[2];
        for (int i = 0; i < 3; i++) {
            u[i] -= dot * f[i];
        }
        normalize(u);

        double[] euler = euler(f, u, yaw);
        yaw = (float) euler[0];
        pitch = (float) euler[1];
        roll = (float) euler[2];
    }

    /** Unit vector the nose points to, for a yaw / pitch in degrees. */
    public static double[] forward(double yawDeg, double pitchDeg) {
        double y = Math.toRadians(yawDeg);
        double p = Math.toRadians(pitchDeg);
        double cp = Math.cos(p);
        return new double[] {-Math.sin(y) * cp, -Math.sin(p), Math.cos(y) * cp};
    }

    /**
     * The three axes of the car in world space (x east, y up, z south) for yaw / pitch / roll in degrees:
     * {@code f} = nose, {@code u} = roof, {@code r} = right side. They are written to the given arrays.
     */
    public static void basis(double yawDeg, double pitchDeg, double rollDeg, double[] f, double[] u, double[] r) {
        double[] nose = forward(yawDeg, pitchDeg);
        double y = Math.toRadians(yawDeg);
        double[] level = {-Math.cos(y), 0.0D, -Math.sin(y)}; // right side of a car without roll
        double[] up0 = cross(level, nose);                    // roof of a car without roll
        double a = Math.toRadians(rollDeg);
        double c = Math.cos(a);
        double s = Math.sin(a);
        for (int i = 0; i < 3; i++) {
            f[i] = nose[i];
            u[i] = up0[i] * c + level[i] * s;
        }
        double[] right = cross(f, u);
        System.arraycopy(right, 0, r, 0, 3);
    }

    /** The inverse of {@link #basis}: {yaw, pitch, roll} in degrees. {@code previousYaw} is kept when the nose points straight up or down. */
    public static double[] euler(double[] f, double[] u, double previousYaw) {
        double pitchDeg = Math.toDegrees(Math.asin(clampD(-f[1], -1.0D, 1.0D)));
        double yawDeg = Math.abs(f[0]) + Math.abs(f[2]) > 1.0E-6D ? Math.toDegrees(Math.atan2(-f[0], f[2])) : previousYaw;
        double y = Math.toRadians(yawDeg);
        double[] level = {-Math.cos(y), 0.0D, -Math.sin(y)};
        double[] up0 = cross(level, f);
        double rollDeg = Math.toDegrees(Math.atan2(dot(u, level), dot(u, up0)));
        return new double[] {yawDeg, pitchDeg, rollDeg};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static void normalize(double[] v) {
        double len = Math.sqrt(dot(v, v));
        if (len > 1.0E-12D) {
            v[0] /= len;
            v[1] /= len;
            v[2] /= len;
        }
    }

    private static double clampD(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
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
