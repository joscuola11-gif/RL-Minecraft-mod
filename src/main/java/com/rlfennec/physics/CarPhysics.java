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
    /** A dodge keeps only half of the vertical speed and adds this, so a dodge near the ground lands quickly (wave dash). */
    public static final double DODGE_LIFT = 0.12D;
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

    /**
     * A dodge turns the car 360 degrees. The rotation eases in (13, 18, 22, 27, then 30 degrees per tick) so that
     * touching the road a few ticks after the dodge still finds the car nearly level (the wave dash); it is over after
     * 15 ticks (0.75 s).
     */
    public static final int DODGE_TICKS = 15;
    public static final float DODGE_ROTATION_RATE = 30.0F;
    /** How far the stick has to be pushed in the air for a second jump to become a dodge (otherwise it is a plain double jump). */
    public static final float DODGE_DEAD_ZONE = 0.5F;
    /** Opposite input beyond this cancels the flip (the car rotates back to how it was). */
    public static final float FLIP_CANCEL_THRESHOLD = 0.4F;

    /** Wave dash: landing within this many ticks of a dodge, nearly level, gives a burst of speed. */
    public static final int WAVE_DASH_WINDOW = 10;
    public static final double WAVE_DASH_IMPULSE = 0.55D;
    public static final double WAVE_DASH_MAX_SPEED = 1.45D;
    /** Landing with the roof facing down (roof direction y below this) is a crash landing: the car is stood up again. */
    public static final double CRASH_LANDING_ROOF_UP = 0.15D;

    public static final int EVENT_NONE = 0;
    public static final int EVENT_JUMP = 1;
    public static final int EVENT_DOUBLE_JUMP = 2;
    public static final int EVENT_WAVE_DASH = 3;

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

    // ---- tuning from the settings (1 = normal) ----
    public float groundSteerScale = 1.0F;
    public float airPitchScale = 1.0F;
    public float airYawScale = 1.0F;
    public float airRollScale = 1.0F;

    // ---- dodge (flip) state ----
    /** Direction of the running dodge: forward (+) / back (-) and right (+) / left (-), a unit vector. */
    private double dodgeForward;
    private double dodgeRight;
    /** How far the dodge has turned the car, degrees, 0..360. */
    private float dodgeAngle;
    private boolean dodging;
    /** The driver pushed the opposite way: the flip is being undone and will not restart. */
    private boolean dodgeCancelled;
    /** Ticks since the last dodge started (large when there was none). */
    public int ticksSinceDodge = 1000;
    private boolean wasOnGround = true;
    private boolean dodgeStartsThisTick;

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

        ticksSinceDodge = Math.min(ticksSinceDodge + 1, 1000);
        boolean grounded = onGround && !inWater;
        // the stick that starts a dodge must not also steer the car on this very tick
        dodgeStartsThisTick = in.jump && !grounded && jumpsUsed == 1 && airTicks + 1 <= DOUBLE_JUMP_WINDOW
                && dodgeMagnitude(in) >= DODGE_DEAD_ZONE;
        if (grounded) {
            if (!wasOnGround) {
                landed();
            }
            jumpsUsed = 0;
            airTicks = 0;
            dodging = false;
            groundStep(in);
        } else {
            airTicks++;
            airStep(in);
        }
        wasOnGround = grounded;

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
        yaw += (float) (steer * maxTurn * turnScale * dir * groundSteerScale);
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
        float pitchInput = clamp(in.pitch, -1.0F, 1.0F);
        if (dodgeStartsThisTick) {
            pitchInput = 0.0F;
            rollInput = 0.0F;
            yawInput = 0.0F;
            angularPitch = 0.0F;
            angularYaw = 0.0F;
            angularRoll = 0.0F;
        }

        // a running dodge turns the car by itself; pitch / roll input fights it (flip cancel) instead of adding to it
        float dodgeStep = 0.0F;
        if (dodging) {
            double sideInput = clamp(in.steer + in.roll, -1.0F, 1.0F);
            double against = -(pitchInput * dodgeForward + sideInput * dodgeRight);
            if (against > FLIP_CANCEL_THRESHOLD) {
                dodgeCancelled = true;
            }
            float rate = DODGE_ROTATION_RATE * Math.min(1.0F, 0.3F + 0.15F * ticksSinceDodge);
            dodgeStep = dodgeCancelled ? -Math.min(rate, dodgeAngle) : Math.min(rate, 360.0F - dodgeAngle);
            dodgeAngle += dodgeStep;
            if (ticksSinceDodge >= DODGE_TICKS) {
                dodging = false; // the dodge is over, normal air control is back
            }
            pitchInput = 0.0F;
            rollInput = 0.0F;
            yawInput = 0.0F;
        }

        angularPitch += (pitchInput * AIR_PITCH_RATE * airPitchScale - angularPitch) * AIR_ANGULAR_RESPONSE;
        angularYaw += (clamp(yawInput, -1.0F, 1.0F) * AIR_YAW_RATE * airYawScale - angularYaw) * AIR_ANGULAR_RESPONSE;
        angularRoll += (rollInput * AIR_ROLL_RATE * airRollScale - angularRoll) * AIR_ANGULAR_RESPONSE;
        if (dodgeStep == 0.0F && Math.abs(angularPitch) < 0.01F && Math.abs(angularYaw) < 0.01F && Math.abs(angularRoll) < 0.01F) {
            return;
        }

        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        basis(yaw, pitch, roll, f, u, r);

        if (dodgeStep != 0.0F) {
            // one rotation about the axis that makes the car flip towards the stick: forward = nose down (about -r),
            // right = roll right (about +f)
            double[] axis = new double[3];
            for (int i = 0; i < 3; i++) {
                axis[i] = -dodgeForward * r[i] + dodgeRight * f[i];
            }
            normalize(axis);
            rotateAbout(axis, Math.toRadians(dodgeStep), f, u, r);
        }

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

        // a dodge needs the stick (or keys) pushed in some direction; without it this is a plain double jump
        double df = in.pitch;                      // forward = nose down input
        double dr = in.airRollFree ? 0.0D : in.steer;
        double magnitude = dodgeMagnitude(in);
        if (magnitude < DODGE_DEAD_ZONE) {
            vy = DOUBLE_JUMP_VELOCITY;
            return;
        }
        dodgeForward = df / magnitude;
        dodgeRight = dr / magnitude;
        double strength = Math.min(1.0D, magnitude);

        double yawRad = Math.toRadians(yaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double rx = -fz;
        double rz = fx;
        double side = dodgeRight * strength * DODGE_IMPULSE * 0.8D;
        double front = dodgeForward * strength * DODGE_IMPULSE * (dodgeForward < 0.0D ? 0.8D : 1.0D);
        vy = Math.max(vy * 0.5D, -0.1D) + DODGE_LIFT;
        vx += fx * front + rx * side;
        vz += fz * front + rz * side;
        double h = horizontalSpeed();
        if (h > MAX_BOOST_SPEED) {
            double k = MAX_BOOST_SPEED / h;
            vx *= k;
            vz *= k;
        }

        dodging = true;
        dodgeCancelled = false;
        dodgeAngle = 0.0F;
        ticksSinceDodge = 0;
        if (Math.abs(dodgeForward) >= Math.abs(dodgeRight)) {
            flip = dodgeForward > 0.0D ? FLIP_FRONT : FLIP_BACK;
        } else {
            flip = dodgeRight > 0.0D ? FLIP_RIGHT : FLIP_LEFT;
        }
    }

    /**
     * Touching the ground shortly after a dodge, with the wheels down, cancels the flip and keeps the dodge's
     * speed: the wave dash.
     */
    private void landed() {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        basis(yaw, pitch, roll, f, u, r);
        if (u[1] < CRASH_LANDING_ROOF_UP) {
            // Landed on the roof or on the nose: stand the car up and keep it going the way it was travelling
            // (the yaw of a car that is upside down or past vertical can point the opposite way).
            if (horizontalSpeed() > 0.1D) {
                yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
            }
            pitch = 0.0F;
            roll = 0.0F;
            ticksSinceDodge = 1000;
            return;
        }
        if (ticksSinceDodge > WAVE_DASH_WINDOW) {
            return;
        }
        double gain = WAVE_DASH_IMPULSE * (dodgeForward >= 0.0D
                ? Math.max(dodgeForward, 0.6D * Math.abs(dodgeRight))
                : dodgeForward * 0.6D);
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double fs = vx * fx + vz * fz;
        double target = gain >= 0.0D ? Math.min(fs + gain, Math.max(fs, WAVE_DASH_MAX_SPEED)) : Math.max(fs + gain, -MAX_REVERSE_SPEED);
        double delta = target - fs;
        vx += fx * delta;
        vz += fz * delta;
        ticksSinceDodge = 1000; // only once per dodge
        event = EVENT_WAVE_DASH;
    }

    private static double dodgeMagnitude(Input in) {
        double df = in.pitch;
        double dr = in.airRollFree ? 0.0D : in.steer;
        return Math.sqrt(df * df + dr * dr);
    }

    private static void rotateAbout(double[] axis, double angle, double[] f, double[] u, double[] r) {
        for (double[] v : new double[][] {f, u, r}) {
            double c = Math.cos(angle);
            double s = Math.sin(angle);
            double d = dot(axis, v) * (1.0D - c);
            double[] x = cross(axis, v);
            for (int i = 0; i < 3; i++) {
                v[i] = v[i] * c + x[i] * s + axis[i] * d;
            }
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
