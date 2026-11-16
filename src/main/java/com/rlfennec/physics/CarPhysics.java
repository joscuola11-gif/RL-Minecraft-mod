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

    /**
     * A tap on the jump key gives a hop of this speed (about 0.8 blocks high); holding the key adds more for up to
     * 6 ticks (RL's variable jump height), the full jump is about 3.4 blocks high.
     */
    public static final double JUMP_VELOCITY = 0.35D;
    public static final double JUMP_HOLD_ACCEL = 0.075D;
    public static final int JUMP_HOLD_TICKS = 6;
    public static final double DOUBLE_JUMP_VELOCITY = 0.52D;
    /**
     * A dodge keeps only a fifth of the vertical speed. High above the road it also gives this much lift, so that the
     * flip has time to finish; close to the road it gives none, so the car lands quickly (the wave dash).
     */
    public static final double DODGE_LIFT = 0.38D;
    /** Below this height above the road a dodge gives no lift, above it the lift grows to the full amount over the next 1.2 blocks. */
    public static final double DODGE_LIFT_MIN_HEIGHT = 0.8D;
    public static final double DODGE_IMPULSE = 0.45D;
    /** Ticks after leaving the ground in which the second jump is still available (1.5 s). */
    public static final int DOUBLE_JUMP_WINDOW = 30;

    /** 100 boost lasts 3 seconds, exactly like in Rocket League (33.3 per second). */
    public static final float BOOST_USE_PER_TICK = MAX_BOOST / 60.0F;

    /** Maximum rotation speed in the air, degrees per tick, about the car's right / up / forward axis. */
    public static final float AIR_PITCH_RATE = 9.0F;
    public static final float AIR_YAW_RATE = 7.0F;
    public static final float AIR_ROLL_RATE = 16.0F;
    /** How fast the rotation speed follows the input (1 = instantly). Without input the car stops turning quickly. */
    public static final float AIR_ANGULAR_RESPONSE = 0.4F;
    public static final double WATER_DRAG = 0.86D;

    /**
     * A dodge turns the car 360 degrees, 30 per tick (0.6 s). Close to the road the rotation eases in (9, 13, 17, 21,
     * 26, then 30 degrees per tick) so that touching down a few ticks after the dodge still finds the car nearly level
     * (the wave dash). The dodge is over after 16 ticks.
     */
    public static final int DODGE_TICKS = 16;
    public static final float DODGE_ROTATION_RATE = 30.0F;
    /** How far the stick has to be pushed in the air for a second jump to become a dodge (otherwise it is a plain double jump). */
    public static final float DODGE_DEAD_ZONE = 0.5F;
    public static final int CANCEL_GRACE_TICKS = 8;
    /** Opposite input beyond this cancels the flip. */
    public static final float FLIP_CANCEL_THRESHOLD = 0.4F;
    /**
     * The flip does not stop dead when it is cancelled: the car keeps turning with 55% of the speed it had, then 30%,
     * and so on (about 35 degrees more at full speed, over a fifth of a second), so the cancel needs a moment (and a
     * bit of anticipation) to take hold.
     */
    public static final float FLIP_CANCEL_COAST_DECAY = 0.55F;

    /** Slow thrust in the air without boost: W / S (or the triggers) nudge the car forward / back along its heading. */
    public static final double AIR_THROTTLE_ACCEL = 0.020D;
    public static final double AIR_THROTTLE_MAX_FORWARD = 0.60D;
    public static final double AIR_THROTTLE_MAX_BACKWARD = 0.40D;

    /** Wave dash: landing within this many ticks of a dodge, nearly level, gives a burst of speed. */
    public static final int WAVE_DASH_WINDOW = 12;
    public static final double WAVE_DASH_IMPULSE = 0.60D;
    public static final double WAVE_DASH_MAX_SPEED = 1.55D;
    /** After a wave dash the extra speed bleeds off slowly for this many ticks. */
    public static final int WAVE_DASH_GRACE_TICKS = 20;
    public static final double WAVE_DASH_OVERSPEED_DECAY = 0.004D;
    /**
     * Landing with the roof tilted further than this from straight up (roof direction y below 0.45, about 63 degrees) is a
     * crash landing: the car bounces and stays where it ends up (on its roof, side or nose) until the driver turns it
     * over with the jump key.
     */
    public static final double SAFE_LANDING_ROOF_UP = 0.45D;
    /** A landing within the wave dash window still counts as a (wave dash) landing on the wheels down to this tilt. */
    public static final double CRASH_LANDING_ROOF_UP = -0.2D;
    /** A crash at less than this speed (blocks per tick) does not bounce; faster ones bounce at half the speed, at most this fast. */
    public static final double BOUNCE_MIN_IMPACT = 0.28D;
    public static final double BOUNCE_RESTITUTION = 0.5D;
    public static final double BOUNCE_MAX_SPEED = 0.55D;
    public static final double BOUNCE_HORIZONTAL_KEEP = 0.75D;
    /** Fraction of the speed a crashed car keeps each tick on the road (it slides to a stop). */
    public static final double CRASHED_FRICTION = 0.86D;
    /** A crashed car tips over onto its roof or side by itself, but never back onto its wheels: degrees per tick (it speeds up). */
    public static final float TOPPLE_ACCEL = 1.5F;
    public static final float TOPPLE_MAX_RATE = 12.0F;
    /** Turning a crashed car back on its wheels with the jump key takes exactly one second. */
    public static final int RECOVERY_TICKS = 20;

    // ---- arena mode (driving on the curved floor, walls and ceiling of the Arena) ----
    /** A wheel is on the surface when it is closer to it than this; three wheels on a surface = the car drives on it. */
    public static final double SURFACE_CONTACT_DISTANCE = 0.35D;
    /** The car is pushed this fast towards the surface it drives on (so it stays on walls and ceilings). */
    public static final double SURFACE_STICK_SPEED = 0.03D;
    /** Gravity along a wall is weaker than free fall, so that the throttle can take the car up a wall. */
    public static final double SURFACE_GRAVITY_FACTOR = 0.5D;
    /** Fraction of the remaining angle the car turns towards the surface normal each tick. */
    public static final double SURFACE_ALIGN_RATE = 0.5D;
    /** Fraction of the tangential speed a body (not wheels) keeps each tick it scrapes along a surface. */
    public static final double BODY_FRICTION = 0.9D;
    /** A flip touches down when the car's own origin (the bottom of the wheels) is this close to the floor. */
    public static final double FLIP_TOUCHDOWN_HEIGHT = 0.25D;
    /** Points of the car below this height (model space) are its "bottom": wheels and underside. */
    public static final double LOWER_BODY_HEIGHT = 0.45D;

    public static final int EVENT_NONE = 0;
    public static final int EVENT_JUMP = 1;
    public static final int EVENT_DOUBLE_JUMP = 2;
    public static final int EVENT_WAVE_DASH = 3;
    /** The driver started to turn a crashed car back onto its wheels. */
    public static final int EVENT_RECOVER = 4;

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
        /** True while the jump key is held: a longer hold gives a higher first jump. */
        public boolean jumpHeld;
        /** True while the boost key is held. */
        public boolean boost;

        public void clear() {
            throttle = 0.0F;
            steer = 0.0F;
            pitch = 0.0F;
            roll = 0.0F;
            airRollFree = false;
            jump = false;
            jumpHeld = false;
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
    /** Height of the car above the road below it, in blocks (the entity measures it; it only matters in the air). */
    public double groundDistance = 10.0D;

    // ---- arena mode: the physics moves the car itself (in arena coordinates) and collides its real shape with the Arena ----
    public boolean arena;
    public double x;
    public double y;
    public double z;
    /** Result: the wheels are on a surface of the arena (floor, ramp, wall or ceiling). */
    public boolean onSurface;
    /** Result: normal of the surface the car drives on (pointing into the pitch), valid while {@link #onSurface}. */
    public final double[] surfaceNormal = {0.0D, 1.0D, 0.0D};
    private boolean wasOnSurface;
    private boolean bodyOnFloor;
    private boolean wasBodyOnFloor;
    /** The direction a jump pushes the car in, and the direction the held jump key keeps pushing (up in the normal world). */
    private final double[] jumpDir = {0.0D, 1.0D, 0.0D};

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
    private int jumpHoldTicks;
    /** 0 when the dodge started on the road, 1 when it started high above it (decides how gently the flip starts). */
    private double dodgeHeightFactor;
    private int overspeedGrace;
    /** Degrees per tick that a cancelled flip is still turning, dying away (see {@link #FLIP_CANCEL_COAST_DECAY}). */
    private float coastRate;
    /** After a flip cancel the input that cancelled it must not also tip the car the other way for a few ticks. */
    private int cancelGrace;
    /** Ticks since the last dodge started (large when there was none). */
    public int ticksSinceDodge = 1000;
    private boolean wasOnGround = true;
    private boolean dodgeStartsThisTick;

    // ---- crash landing ----
    /** The car crashed (landed on its roof, side or nose) and has not been turned back onto its wheels yet. */
    public boolean crashed;
    /** The crashed car lies on the road waiting for the jump key to turn it over (this is when the HUD shows the tip). */
    public boolean stranded;
    /** True while the driver's jump key is turning the crashed car over, 0..{@link #RECOVERY_TICKS}. */
    public boolean recovering;
    public int recoveryTick;
    private final double[] recoveryAxis = new double[3];
    private double recoveryAngle;
    private float recoveryYaw;
    private float toppleRate;
    /** Vertical speed at the end of the last step: the speed of the impact when the car touches down. */
    private double lastVy;

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

        if (inWater) {
            clearCrash(); // the water stands the car up again
        }
        // boost meter (a car lying on its roof cannot boost)
        boosting = in.boost && boost > 0.0F && !inWater && !(crashed && (arena ? stranded : onGround));
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
        if (overspeedGrace > 0) {
            overspeedGrace--;
        }
        if (arena) {
            arenaStep(in);
            if (vy < -TERMINAL_VELOCITY) {
                vy = -TERMINAL_VELOCITY;
            }
            lastVy = vy;
            return;
        }
        boolean grounded = onGround && !inWater;
        // the stick that starts a dodge must not also steer the car on this very tick
        dodgeStartsThisTick = in.jump && !grounded && jumpsUsed == 1 && airTicks + 1 <= DOUBLE_JUMP_WINDOW
                && dodgeMagnitude(in) >= DODGE_DEAD_ZONE;
        boolean bounced = false;
        if (grounded && !wasOnGround) {
            bounced = landed();
        }
        boolean resting = grounded && !bounced;
        if (resting) {
            jumpsUsed = 0;
            airTicks = 0;
            dodging = false;
            coastRate = 0.0F;
            if (crashed) {
                crashedStep();
            } else {
                groundStep(in);
            }
        } else {
            airTicks++;
            airStep(in);
        }
        wasOnGround = resting;

        if (in.jump) {
            tryJump(in, resting);
        }
        stranded = resting && crashed && !recovering;

        if (vy < -TERMINAL_VELOCITY) {
            vy = -TERMINAL_VELOCITY;
        }
        lastVy = vy;
    }

    /** Forgets a crash (the car is on its wheels again, or someone else drives it). */
    public void clearCrash() {
        crashed = false;
        stranded = false;
        recovering = false;
        recoveryTick = 0;
        toppleRate = 0.0F;
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

        fs = driveAcceleration(in, fs);
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

    /** The new speed along the nose after one tick of throttle / brake / boost / coasting, with the speed limits. */
    private double driveAcceleration(Input in, double fs) {
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
            fs = Math.max(MAX_SPEED, fs - (overspeedGrace > 0 ? WAVE_DASH_OVERSPEED_DECAY : OVERSPEED_DECAY));
        }
        fs = Math.max(fs, -MAX_REVERSE_SPEED);
        return fs;
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
        } else if (in.throttle != 0.0F && !dodging && ticksSinceDodge >= DODGE_TICKS && roofUp() > 0.2D) {
            airThrottle(in.throttle);
        }

        if (jumpHoldTicks > 0) {
            if (in.jumpHeld && vx * jumpDir[0] + vy * jumpDir[1] + vz * jumpDir[2] > 0.0D && jumpsUsed == 1) {
                vx += jumpDir[0] * JUMP_HOLD_ACCEL;
                vy += jumpDir[1] * JUMP_HOLD_ACCEL;
                vz += jumpDir[2] * JUMP_HOLD_ACCEL;
                jumpHoldTicks--;
            } else {
                jumpHoldTicks = 0;
            }
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

    /** y component of the roof direction: 1 = wheels down, -1 = upside down. */
    private double roofUp() {
        return Math.cos(Math.toRadians(pitch)) * Math.cos(Math.toRadians(roll));
    }

    /**
     * Gentle thrust in the air, with no boost: forward or back along the heading of the car. It only speeds the car up
     * to a modest speed (and slows it down back to a modest reverse speed); it never takes speed away from a faster car
     * going the same way, so it cannot get in the way of a boost or a flip.
     */
    private void airThrottle(float throttle) {
        double rad = Math.toRadians(yaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double fs = vx * fx + vz * fz;
        double push = 0.0D;
        if (throttle > 0.0F) {
            push = Math.min(AIR_THROTTLE_ACCEL * throttle, Math.max(0.0D, AIR_THROTTLE_MAX_FORWARD - fs));
        } else {
            push = -Math.min(AIR_THROTTLE_ACCEL * -throttle, Math.max(0.0D, fs + AIR_THROTTLE_MAX_BACKWARD));
        }
        vx += fx * push;
        vz += fz * push;
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
                // flip cancel: the dodge stops turning the car. It does not stop dead but coasts for a moment, dying
                // away (cancel at the start = hardly any flip, cancel a little before half way = a half flip, and the
                // driver finishes with air roll)
                dodging = false;
                cancelGrace = CANCEL_GRACE_TICKS;
                coastRate = Math.min(DODGE_ROTATION_RATE * Math.min(1.0F, 0.15F + 0.14F * ticksSinceDodge + (float) dodgeHeightFactor),
                        360.0F - dodgeAngle);
            } else {
                float rate = DODGE_ROTATION_RATE * Math.min(1.0F, 0.15F + 0.14F * ticksSinceDodge + (float) dodgeHeightFactor);
                dodgeStep = Math.min(rate, 360.0F - dodgeAngle);
                dodgeAngle += dodgeStep;
                if (dodgeAngle >= 360.0F || ticksSinceDodge >= DODGE_TICKS) {
                    dodging = false; // the dodge is over, normal air control is back
                }
                pitchInput = 0.0F;
                rollInput = 0.0F;
                yawInput = 0.0F;
            }
        }
        if (!dodging && coastRate > 0.0F) {
            coastRate *= FLIP_CANCEL_COAST_DECAY;
            if (coastRate < 0.5F) {
                coastRate = 0.0F;
            } else {
                dodgeStep = coastRate;
            }
        }
        if (cancelGrace > 0) {
            cancelGrace--;
            // right after a cancel, the input that pushes against the dodge must not also turn the car the other way
            if (dodgeForward * pitchInput < -0.3D) {
                pitchInput = 0.0F;
            }
            double side = in.steer + in.roll;
            if (dodgeRight * side < -0.3D) {
                yawInput = 0.0F;
                rollInput = 0.0F;
            }
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

    private void tryJump(Input in, boolean resting) {
        if (crashed && resting) {
            // a crashed car lying on the road: the jump key turns it over (it never jumps)
            if (!recovering) {
                startRecovery(in);
            }
            return;
        }
        boolean grounded = (arena || (onGround && !inWater)) && resting;
        if (grounded) {
            kick(JUMP_VELOCITY, arena ? surfaceNormal : null);
            jumpHoldTicks = JUMP_HOLD_TICKS;
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
            kick(JUMP_VELOCITY * 0.9D, arena ? nearestNormal() : null);
            jumpHoldTicks = JUMP_HOLD_TICKS;
            jumpsUsed = 1;
            event = EVENT_JUMP;
            return;
        }

        jumpsUsed = 2;
        event = EVENT_DOUBLE_JUMP;

        // a dodge needs the stick (or keys) pushed in some direction; without it this is a plain double jump
        double df = in.pitch;                      // forward = nose down input
        double dr = in.steer;                      // (also while the free air roll button is held)
        double magnitude = dodgeMagnitude(in);
        if (magnitude < DODGE_DEAD_ZONE) {
            if (arena) {
                double[] f = new double[3];
                double[] u = new double[3];
                double[] r = new double[3];
                basis(yaw, pitch, roll, f, u, r);
                kick(DOUBLE_JUMP_VELOCITY, u); // along the roof of the car, like Rocket League
            } else {
                vy = DOUBLE_JUMP_VELOCITY;
            }
            return;
        }
        dodgeForward = df / magnitude;
        dodgeRight = dr / magnitude;
        double strength = Math.min(1.0D, magnitude);

        double side = dodgeRight * strength * DODGE_IMPULSE * 0.8D;
        double front = dodgeForward * strength * DODGE_IMPULSE * (dodgeForward < 0.0D ? 0.8D : 1.0D);
        double lift = DODGE_LIFT * clamp01((groundDistance - DODGE_LIFT_MIN_HEIGHT) / 1.2D);
        if (arena) {
            arenaDodgeImpulse(front, side, lift);
        } else {
            double yawRad = Math.toRadians(yaw);
            double fx = -Math.sin(yawRad);
            double fz = Math.cos(yawRad);
            double rx = -fz;
            double rz = fx;
            vy = Math.max(vy * 0.2D, -0.1D) + lift;
            vx += fx * front + rx * side;
            vz += fz * front + rz * side;
            double h = horizontalSpeed();
            if (h > MAX_BOOST_SPEED) {
                double k = MAX_BOOST_SPEED / h;
                vx *= k;
                vz *= k;
            }
        }

        dodging = true;
        coastRate = 0.0F;
        dodgeHeightFactor = clamp01((groundDistance - DODGE_LIFT_MIN_HEIGHT) / 1.2D);
        dodgeAngle = 0.0F;
        ticksSinceDodge = 0;
        if (Math.abs(dodgeForward) >= Math.abs(dodgeRight)) {
            flip = dodgeForward > 0.0D ? FLIP_FRONT : FLIP_BACK;
        } else {
            flip = dodgeRight > 0.0D ? FLIP_RIGHT : FLIP_LEFT;
        }
    }

    /**
     * Touching the road. Shortly after a dodge, with the wheels down, this cancels the flip and keeps the dodge's
     * speed: the wave dash. Landing on the roof, the side or the nose is a crash: the car bounces and stays crashed.
     *
     * @return true when the car bounced off the road and is in the air again
     */
    private boolean landed() {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        basis(yaw, pitch, roll, f, u, r);
        boolean waveDashLanding = ticksSinceDodge <= WAVE_DASH_WINDOW && u[1] >= CRASH_LANDING_ROOF_UP;
        if (u[1] < SAFE_LANDING_ROOF_UP && !waveDashLanding) {
            return crashLanded();
        }
        clearCrash();
        if (ticksSinceDodge > WAVE_DASH_WINDOW) {
            return false;
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
        overspeedGrace = WAVE_DASH_GRACE_TICKS;
        event = EVENT_WAVE_DASH;
        return false;
    }

    private boolean crashLanded() {
        double impact = Math.max(0.0D, -lastVy);
        crashed = true;
        recovering = false;
        recoveryTick = 0;
        toppleRate = 0.0F;
        dodging = false;
        coastRate = 0.0F;
        ticksSinceDodge = 1000;
        angularPitch = 0.0F;
        angularYaw = 0.0F;
        angularRoll = 0.0F;
        if (impact <= BOUNCE_MIN_IMPACT) {
            return false; // too slow to bounce: the car just lies there
        }
        // it bounces, still tumbling the way it came down; the driver can even air roll it back onto its wheels before
        // the next touchdown. No jumps while it bounces.
        vy = Math.min(impact * BOUNCE_RESTITUTION, BOUNCE_MAX_SPEED);
        vx *= BOUNCE_HORIZONTAL_KEEP;
        vz *= BOUNCE_HORIZONTAL_KEEP;
        jumpsUsed = 2;
        jumpHoldTicks = 0;
        airTicks = 0;
        return true;
    }

    /** A crashed car on the road: it slides to a stop, tips over onto its roof or side, and waits for the jump key. */
    private void crashedStep() {
        vx *= CRASHED_FRICTION;
        vz *= CRASHED_FRICTION;
        vy = -0.1D;
        angularPitch = 0.0F;
        angularYaw = 0.0F;
        angularRoll = 0.0F;
        if (recovering) {
            recoveryStep();
        } else {
            topple();
        }
    }

    /**
     * A car standing on its nose (or tilted past its tipping point) falls over onto the face that is already the lowest:
     * roof, left side or right side. It never falls back onto its wheels: only the driver does that.
     */
    private void topple() {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        basis(yaw, pitch, roll, f, u, r);
        // outward normals of the roof and the two sides; the one that points down the most ends up on the road
        // (a small preference for the roof: a car standing straight on its nose lands on its roof)
        double[][] faces = {u, {-r[0], -r[1], -r[2]}, r};
        double[] bias = {-0.2D, 0.0D, 0.0D};
        int best = 0;
        for (int i = 1; i < faces.length; i++) {
            if (faces[i][1] + bias[i] < faces[best][1] + bias[best]) {
                best = i;
            }
        }
        double[] n = faces[best];
        double angle = Math.acos(clampD(-n[1], -1.0D, 1.0D));
        if (angle < Math.toRadians(0.5D)) {
            toppleRate = 0.0F;
            return;
        }
        double[] axis = {n[2], 0.0D, -n[0]}; // n x down: the horizontal axis that tips n towards the road
        if (axis[0] * axis[0] + axis[2] * axis[2] < 1.0E-8D) {
            axis[0] = f[0];
            axis[1] = f[1];
            axis[2] = f[2];
        }
        normalize(axis);
        toppleRate = Math.min(toppleRate + TOPPLE_ACCEL, TOPPLE_MAX_RATE);
        rotateAbout(axis, Math.min(Math.toRadians(toppleRate), angle), f, u, r);
        setOrientation(f, u);
    }

    /** Writes a (nearly) orthonormal nose / roof pair back as yaw, pitch and roll. */
    private void setOrientation(double[] f, double[] u) {
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

    /**
     * The driver pressed jump on a crashed car: it turns over, wheels down, nose pointing the way it points now. The turn
     * takes {@link #RECOVERY_TICKS} ticks (one second), slow at the start and the end.
     */
    private void startRecovery(Input in) {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        basis(yaw, pitch, roll, f, u, r);
        double yawRad = Math.toRadians(yaw);
        double[] ft = {-Math.sin(yawRad), 0.0D, Math.cos(yawRad)};
        double[] ut = {0.0D, 1.0D, 0.0D};
        double[] rt = cross(ft, ut);
        // the rotation that takes the car's axes to the upright ones: q = ft f^T + ut u^T + rt r^T
        double[][] q = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                q[i][j] = ft[i] * f[j] + ut[i] * u[j] + rt[i] * r[j];
            }
        }
        double cos = clampD((q[0][0] + q[1][1] + q[2][2] - 1.0D) / 2.0D, -1.0D, 1.0D);
        double angle = Math.acos(cos);
        double[] axis = new double[3];
        if (angle > Math.PI - 0.02D) {
            // a half turn (the car is on its roof): about the car's own axis that does not move, the nose axis; the
            // stick says which way (right by default)
            double best = -1.0D;
            for (int col = 0; col < 3; col++) {
                double d = (q[col][col] + 1.0D) / 2.0D;
                if (d > best) {
                    best = d;
                    for (int i = 0; i < 3; i++) {
                        axis[i] = (q[i][col] + (i == col ? 1.0D : 0.0D)) / 2.0D;
                    }
                }
            }
            normalize(axis);
            double side = in.steer + in.roll;
            double along = dot(axis, f);
            if (Math.abs(along) > 0.3D) {
                boolean wantRight = side >= -0.3D;
                if ((along > 0.0D) != wantRight) {
                    for (int i = 0; i < 3; i++) {
                        axis[i] = -axis[i];
                    }
                }
            }
        } else if (angle > 1.0E-3D) {
            double sin = Math.sin(angle);
            axis[0] = (q[2][1] - q[1][2]) / (2.0D * sin);
            axis[1] = (q[0][2] - q[2][0]) / (2.0D * sin);
            axis[2] = (q[1][0] - q[0][1]) / (2.0D * sin);
            normalize(axis);
        }
        System.arraycopy(axis, 0, recoveryAxis, 0, 3);
        recoveryAngle = angle;
        recoveryYaw = yaw;
        recoveryTick = 0;
        recovering = true;
        toppleRate = 0.0F;
        event = EVENT_RECOVER;
    }

    private void recoveryStep() {
        double before = smoothStep(recoveryTick / (double) RECOVERY_TICKS);
        recoveryTick++;
        if (recoveryTick >= RECOVERY_TICKS) {
            yaw = recoveryYaw;
            pitch = 0.0F;
            roll = 0.0F;
            clearCrash(); // on its wheels again
            return;
        }
        double after = smoothStep(recoveryTick / (double) RECOVERY_TICKS);
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        basis(yaw, pitch, roll, f, u, r);
        rotateAbout(recoveryAxis, recoveryAngle * (after - before), f, u, r);
        setOrientation(f, u);
    }

    private static double smoothStep(double x) {
        return x * x * (3.0D - 2.0D * x);
    }


    // ------------------------------------------------------------------ arena mode

    /** Sets the speed along {@code dir} (null = straight up, the normal world) to {@code speed}, keeping the rest of the velocity. */
    private void kick(double speed, double[] dir) {
        if (dir == null) {
            vy = speed;
            jumpDir[0] = 0.0D;
            jumpDir[1] = 1.0D;
            jumpDir[2] = 0.0D;
            return;
        }
        double along = vx * dir[0] + vy * dir[1] + vz * dir[2];
        vx += dir[0] * (speed - along);
        vy += dir[1] * (speed - along);
        vz += dir[2] * (speed - along);
        System.arraycopy(dir, 0, jumpDir, 0, 3);
    }

    private double[] nearestNormal() {
        double[] n = new double[3];
        Arena.normal(x, y, z, n);
        return n;
    }

    /** The dodge impulse in the arena: along the car's nose and side flattened onto the nearest surface, plus the lift away from it. */
    private void arenaDodgeImpulse(double front, double side, double lift) {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        basis(yaw, pitch, roll, f, u, r);
        double[] nn = nearestNormal();
        double[] ff = flatten(f, nn);
        double[] rr = flatten(r, nn);
        double along = vx * nn[0] + vy * nn[1] + vz * nn[2];
        double newAlong = Math.max(along * 0.2D, -0.1D) + lift;
        vx += nn[0] * (newAlong - along) + ff[0] * front + rr[0] * side;
        vy += nn[1] * (newAlong - along) + ff[1] * front + rr[1] * side;
        vz += nn[2] * (newAlong - along) + ff[2] * front + rr[2] * side;
        // the speed along the surface is limited like the boost speed
        double vn = vx * nn[0] + vy * nn[1] + vz * nn[2];
        double tx = vx - nn[0] * vn;
        double ty = vy - nn[1] * vn;
        double tz = vz - nn[2] * vn;
        double speed = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (speed > MAX_BOOST_SPEED) {
            double k = MAX_BOOST_SPEED / speed;
            vx += tx * (k - 1.0D);
            vy += ty * (k - 1.0D);
            vz += tz * (k - 1.0D);
        }
    }

    /** {@code v} without its part along {@code n}, as a unit vector. */
    private static double[] flatten(double[] v, double[] n) {
        double d = dot(v, n);
        double[] out = {v[0] - n[0] * d, v[1] - n[1] * d, v[2] - n[2] * d};
        normalize(out);
        return out;
    }

    private double[] hullPoint(float[] points, int index, double[] f, double[] u, double[] r) {
        double hx = points[index * 3];
        double hy = points[index * 3 + 1];
        double hz = points[index * 3 + 2];
        return new double[] {
                x + f[0] * hx + u[0] * hy + r[0] * hz,
                y + f[1] * hx + u[1] * hy + r[1] * hz,
                z + f[2] * hx + u[2] * hy + r[2] * hz};
    }

    /**
     * One tick of the car in the arena: the wheels decide whether it drives on a surface (the floor, a ramp, a wall or the
     * ceiling) or flies; then the car is moved and its real shape (the hull points of the model) is pushed out of the walls.
     */
    private void arenaStep(Input in) {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        basis(yaw, pitch, roll, f, u, r);
        groundDistance = Arena.sd(x, y, z);

        // which wheels touch a surface?
        int wheels = 0;
        double[] n = new double[3];
        double[] wn = new double[3];
        for (int i = 0; i < 4; i++) {
            double[] w = hullPoint(CarHull.WHEELS, i, f, u, r);
            if (Arena.sd(w[0], w[1], w[2]) < SURFACE_CONTACT_DISTANCE) {
                wheels++;
                Arena.normal(w[0], w[1], w[2], wn);
                n[0] += wn[0];
                n[1] += wn[1];
                n[2] += wn[2];
            }
        }
        normalize(n);
        // three wheels on a surface and not moving away from it (a jump) = driving on it
        boolean grounded = wheels >= 3 && dot(u, n) > 0.35D && vx * n[0] + vy * n[1] + vz * n[2] < 0.12D;
        if (inWater) {
            grounded = false;
        }

        if (grounded) {
            if (!wasOnSurface) {
                arenaLanded(n, f);
            }
            crashed = false;
            stranded = false;
            surfaceStep(in, n, f, u, r);
            x += vx;
            y += vy;
            z += vz;
            snapToSurface(f, u, r);
        } else {
            if (crashed && (wasBodyOnFloor || recovering)) {
                airTicks = 0;
                crashedArenaStep();
            } else {
                airTicks++;
                airStep(in);
            }
            x += vx;
            y += vy;
            z += vz;
        }
        wasOnSurface = grounded;
        onSurface = grounded;

        bodyOnFloor = false;
        collideWithArena(grounded);
        wasBodyOnFloor = bodyOnFloor;
        stranded = crashed && bodyOnFloor && !recovering && !grounded && vx * vx + vy * vy + vz * vz < 0.04D;

        if (in.jump) {
            tryJump(in, grounded || stranded);
        }
    }

    /** The wheels touch a surface after being in the air: a landing (the wave dash gives its burst of speed). */
    private void arenaLanded(double[] n, double[] f) {
        clearCrash();
        if (ticksSinceDodge > WAVE_DASH_WINDOW) {
            return;
        }
        double gain = WAVE_DASH_IMPULSE * (dodgeForward >= 0.0D
                ? Math.max(dodgeForward, 0.6D * Math.abs(dodgeRight))
                : dodgeForward * 0.6D);
        double[] ff = flatten(f, n);
        double fs = vx * ff[0] + vy * ff[1] + vz * ff[2];
        double target = gain >= 0.0D ? Math.min(fs + gain, Math.max(fs, WAVE_DASH_MAX_SPEED)) : Math.max(fs + gain, -MAX_REVERSE_SPEED);
        double delta = target - fs;
        vx += ff[0] * delta;
        vy += ff[1] * delta;
        vz += ff[2] * delta;
        ticksSinceDodge = 1000;
        overspeedGrace = WAVE_DASH_GRACE_TICKS;
        event = EVENT_WAVE_DASH;
    }

    /** Driving on a surface: the car steers about its roof axis and accelerates along its nose (gravity pulls along walls). */
    private void surfaceStep(Input in, double[] n, double[] f, double[] u, double[] r) {
        jumpsUsed = 0;
        airTicks = 0;
        dodging = false;
        coastRate = 0.0F;

        // velocity along the surface, with gravity pulling along it (nothing on a floor or ceiling, weaker than free fall on a wall)
        double vn = vx * n[0] + vy * n[1] + vz * n[2];
        double[] vt = {vx - n[0] * vn, vy - n[1] * vn, vz - n[2] * vn};
        double gn = -GRAVITY * n[1];
        vt[0] += -n[0] * gn * SURFACE_GRAVITY_FACTOR;
        vt[1] += (-GRAVITY - n[1] * gn) * SURFACE_GRAVITY_FACTOR;
        vt[2] += -n[2] * gn * SURFACE_GRAVITY_FACTOR;

        double fs = dot(vt, f);
        double ls = dot(vt, r);

        // steering about the roof axis: sharper when slow, nothing when standing still, inverted when reversing
        double abs = Math.abs(fs);
        double speedFrac = Math.min(abs / MAX_SPEED, 1.0D);
        double maxTurn = 5.5D + (1.8D - 5.5D) * speedFrac;
        double turnScale = Math.min(1.0D, abs / 0.07D);
        double dir = fs >= -0.02D ? 1.0D : -1.0D;
        double turn = Math.toRadians(steer * maxTurn * turnScale * dir * groundSteerScale);
        double c = Math.cos(turn);
        double s = Math.sin(turn);
        for (int i = 0; i < 3; i++) {
            double fi = f[i];
            f[i] = fi * c + r[i] * s;
            r[i] = r[i] * c - fi * s;
        }

        fs = driveAcceleration(in, fs);
        ls *= LATERAL_GRIP;

        vx = f[0] * fs + r[0] * ls - n[0] * SURFACE_STICK_SPEED;
        vy = f[1] * fs + r[1] * ls - n[1] * SURFACE_STICK_SPEED;
        vz = f[2] * fs + r[2] * ls - n[2] * SURFACE_STICK_SPEED;
        angularPitch = 0.0F;
        angularYaw = 0.0F;
        angularRoll = 0.0F;
    }

    /**
     * After the car moved along a surface: fits it to the surface under its wheels. The four wheels rest on the surface,
     * so the roof turns to the plane through the four contact points (the car pitches up a ramp, rolls onto a wall, and
     * follows the curves), the velocity turns with the car (the speed is kept) and the car is placed on the surface.
     */
    private void snapToSurface(double[] f, double[] u, double[] r) {
        double[][] q = new double[4][];
        double[] n = new double[3];
        double[] avg = new double[3];
        for (int i = 0; i < 4; i++) {
            double[] w = hullPoint(CarHull.WHEELS, i, f, u, r);
            double d = Arena.sd(w[0], w[1], w[2]);
            Arena.normal(w[0], w[1], w[2], n);
            q[i] = new double[] {w[0] - n[0] * d, w[1] - n[1] * d, w[2] - n[2] * d};
            avg[0] += n[0];
            avg[1] += n[1];
            avg[2] += n[2];
        }
        // order: front right, front left, rear right, rear left
        double[] a = new double[3];
        double[] b = new double[3];
        for (int i = 0; i < 3; i++) {
            a[i] = (q[0][i] + q[1][i]) * 0.5D - (q[2][i] + q[3][i]) * 0.5D; // rear -> front
            b[i] = (q[0][i] + q[2][i]) * 0.5D - (q[1][i] + q[3][i]) * 0.5D; // left -> right
        }
        double[] fit = cross(b, a); // right x forward = roof
        normalize(avg);
        if (dot(fit, avg) < 0.0D) {
            fit[0] = -fit[0];
            fit[1] = -fit[1];
            fit[2] = -fit[2];
        }
        normalize(fit);
        if (dot(fit, fit) < 0.5D || dot(fit, avg) < 0.3D) {
            fit = avg; // degenerate: fall back to the average normal
        }

        double[] axis = cross(u, fit);
        double sin = Math.sqrt(dot(axis, axis));
        double angle = Math.atan2(sin, dot(u, fit));
        if (sin > 1.0E-9D && angle > 1.0E-6D) {
            angle = Math.min(angle, Math.toRadians(35.0D));
            for (int i = 0; i < 3; i++) {
                axis[i] /= sin;
            }
            double[] v = {vx, vy, vz};
            rotateAbout(axis, angle, f, u, r, v);
            vx = v[0];
            vy = v[1];
            vz = v[2];
        }
        normalize(f);
        double d = dot(u, f);
        for (int i = 0; i < 3; i++) {
            u[i] -= d * f[i];
        }
        normalize(u);
        double[] rr = cross(f, u);
        System.arraycopy(rr, 0, r, 0, 3);

        // place the car so that the wheels rest on the surface
        double mean = 0.0D;
        for (int i = 0; i < 4; i++) {
            double[] w = hullPoint(CarHull.WHEELS, i, f, u, r);
            mean += Arena.sd(w[0], w[1], w[2]);
        }
        mean /= 4.0D;
        x -= fit[0] * mean;
        y -= fit[1] * mean;
        z -= fit[2] * mean;

        double[] euler = euler(f, u, yaw);
        yaw = (float) euler[0];
        pitch = (float) euler[1];
        roll = (float) euler[2];
        System.arraycopy(fit, 0, surfaceNormal, 0, 3);
    }

    /** Puts the car upright on a surface, keeping the way it faces (its nose flattened onto the surface). */
    private void levelOnto(double[] n) {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        basis(yaw, pitch, roll, f, u, r);
        double[] ft = flatten(f, n);
        if (dot(ft, ft) < 0.5D) {
            ft = flatten(new double[] {vx, vy, vz}, n);
        }
        if (dot(ft, ft) < 0.5D) {
            ft = flatten(r, n);
        }
        double[] un = {n[0], n[1], n[2]};
        setOrientation(ft, un);
        basis(yaw, pitch, roll, f, u, r);
        // lift the car until no wheel is below the surface
        double lowest = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            double[] w = hullPoint(CarHull.WHEELS, i, f, u, r);
            lowest = Math.min(lowest, Arena.sd(w[0], w[1], w[2]));
        }
        if (lowest < 0.0D) {
            x -= n[0] * lowest;
            y -= n[1] * lowest;
            z -= n[2] * lowest;
        }
    }

    /** A crashed car on the floor: it slides to a stop and tips onto its roof or side (the driver turns it over with the jump key). */
    private void crashedArenaStep() {
        vx *= CRASHED_FRICTION;
        vz *= CRASHED_FRICTION;
        vy -= GRAVITY;
        angularPitch = 0.0F;
        angularYaw = 0.0F;
        angularRoll = 0.0F;
        if (recovering) {
            recoveryStep();
        } else {
            topple();
        }
    }

    /**
     * Pushes the shape of the car out of the walls: every outline point of the model and the four wheel contacts must stay
     * inside the arena. A hit with the wheels is a landing; a hit with the body bounces, scrapes and (on the floor,
     * roof down) is a crash.
     */
    private void collideWithArena(boolean driving) {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        basis(yaw, pitch, roll, f, u, r);
        double[] n = new double[3];
        for (int iteration = 0; iteration < 4; iteration++) {
            double worst = 0.0D;
            double[] worstPoint = null;
            boolean wheel = false;
            int count = CarHull.POINTS.length / 3;
            for (int i = 0; i < count; i++) {
                double[] p = hullPoint(CarHull.POINTS, i, f, u, r);
                double d = Arena.sd(p[0], p[1], p[2]);
                if (d < worst) {
                    worst = d;
                    worstPoint = p;
                    // the wheels and the underside of the car (below the doors) are the "bottom": touching down on them is a landing
                    wheel = CarHull.POINTS[i * 3 + 1] < LOWER_BODY_HEIGHT;
                }
            }
            for (int i = 0; i < 4; i++) {
                double[] p = hullPoint(CarHull.WHEELS, i, f, u, r);
                double d = Arena.sd(p[0], p[1], p[2]);
                if (d < worst) {
                    worst = d;
                    worstPoint = p;
                    wheel = true;
                }
            }
            if (worstPoint == null) {
                return;
            }
            Arena.normal(worstPoint[0], worstPoint[1], worstPoint[2], n);
            double roofToWall = dot(u, n);
            if (n[1] > 0.7D && ticksSinceDodge <= DODGE_TICKS && !driving) {
                // A flip close to the floor turns the car through the floor, like in the normal world, until the car itself
                // touches down; then the ground cancels the flip (the wave dash) or, past the window, it is a normal landing.
                if (Arena.sd(x, y, z) > FLIP_TOUCHDOWN_HEIGHT) {
                    return;
                }
                if (ticksSinceDodge <= WAVE_DASH_WINDOW && roofToWall >= CRASH_LANDING_ROOF_UP) {
                    double vnDash = vx * n[0] + vy * n[1] + vz * n[2];
                    levelOnto(n);
                    if (vnDash < 0.0D) {
                        vx -= n[0] * vnDash;
                        vy -= n[1] * vnDash;
                        vz -= n[2] * vnDash;
                    }
                    wasOnSurface = false; // the next tick is a landing (it gives the wave dash its speed)
                    return;
                }
            }
            x -= n[0] * worst;
            y -= n[1] * worst;
            z -= n[2] * worst;

            double vn = vx * n[0] + vy * n[1] + vz * n[2];
            if (vn >= 0.0D) {
                continue;
            }
            if (driving && roofToWall > SAFE_LANDING_ROOF_UP) {
                continue; // driving on the surface: the car is only moved out of it, the snap to the wheels' plane keeps the velocity
            }
            if (wheel && roofToWall > SAFE_LANDING_ROOF_UP) {
                // the wheels touch down: no bounce
                vx -= n[0] * vn;
                vy -= n[1] * vn;
                vz -= n[2] * vn;
                continue;
            }
            // the body hits: bounce a little, scrape along the surface
            double impact = -vn;
            double bounce = impact > BOUNCE_MIN_IMPACT ? Math.min(impact * BOUNCE_RESTITUTION, BOUNCE_MAX_SPEED) : 0.0D;
            vx += n[0] * (bounce - vn);
            vy += n[1] * (bounce - vn);
            vz += n[2] * (bounce - vn);
            double vn2 = vx * n[0] + vy * n[1] + vz * n[2];
            vx = n[0] * vn2 + (vx - n[0] * vn2) * BODY_FRICTION;
            vy = n[1] * vn2 + (vy - n[1] * vn2) * BODY_FRICTION;
            vz = n[2] * vn2 + (vz - n[2] * vn2) * BODY_FRICTION;
            if (n[1] > 0.7D) {
                bodyOnFloor = true;
                if (roofToWall < SAFE_LANDING_ROOF_UP) {
                    crashed = true;
                    jumpsUsed = 2;
                    jumpHoldTicks = 0;
                    dodging = false;
                    coastRate = 0.0F;
                }
            }
        }
    }

    private static double dodgeMagnitude(Input in) {
        double df = in.pitch;
        double dr = in.steer;
        return Math.sqrt(df * df + dr * dr);
    }

    private static void rotateAbout(double[] axis, double angle, double[] f, double[] u, double[] r, double[] extra) {
        rotateAbout(axis, angle, new double[][] {f, u, r, extra});
    }

    private static void rotateAbout(double[] axis, double angle, double[] f, double[] u, double[] r) {
        rotateAbout(axis, angle, new double[][] {f, u, r});
    }

    private static void rotateAbout(double[] axis, double angle, double[][] vectors) {
        for (double[] v : vectors) {
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
