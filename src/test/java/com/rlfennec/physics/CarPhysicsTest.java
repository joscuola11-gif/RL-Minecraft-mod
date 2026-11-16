package com.rlfennec.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CarPhysicsTest {
    /** Minimal world: an infinite flat floor at y = 0. */
    private static final class Sim {
        final CarPhysics car = new CarPhysics();
        final CarPhysics.Input in = new CarPhysics.Input();
        double x, y, z;
        int ticks;

        Sim() {
            car.onGround = true;
        }

        void tick() {
            car.step(in);
            in.jump = false;
            x += car.vx;
            y += car.vy;
            z += car.vz;
            car.onGround = false;
            if (y <= 0.0D) {
                y = 0.0D;
                if (car.vy < 0.0D) {
                    car.vy = 0.0D;
                }
                car.onGround = true;
            }
            ticks++;
        }

        void run(int n) {
            for (int i = 0; i < n; i++) {
                tick();
            }
        }
    }

    @Test
    void acceleratesToTopSpeedButNotBeyondWithoutBoost() {
        Sim s = new Sim();
        s.in.throttle = 1.0F;
        s.run(150);
        assertTrue(s.car.forwardSpeed() > CarPhysics.MAX_SPEED * 0.95D, "speed " + s.car.forwardSpeed());
        assertTrue(s.car.forwardSpeed() <= CarPhysics.MAX_SPEED + 1e-6D);
    }

    @Test
    void hundredBoostLastsThreeSecondsAndIsFasterThanTopSpeed() {
        Sim s = new Sim();
        s.in.throttle = 1.0F;
        s.in.boost = true;
        assertEquals(100.0F, s.car.boost, 1e-6F);

        int boostTicks = 0;
        for (int i = 0; i < 200 && s.car.boost > 0.0F; i++) {
            s.tick();
            if (s.car.boosting) {
                boostTicks++;
            }
        }
        assertEquals(0.0F, s.car.boost, 1e-3F);
        assertTrue(boostTicks >= 59 && boostTicks <= 61, "boost lasted " + boostTicks + " ticks");
        assertTrue(s.car.forwardSpeed() > CarPhysics.MAX_SPEED * 1.3D, "speed " + s.car.forwardSpeed());

        // the meter is empty: no more boosting, speed decays back to the normal top speed
        s.tick();
        assertFalse(s.car.boosting);
        s.run(120);
        assertTrue(s.car.forwardSpeed() <= CarPhysics.MAX_SPEED + 1e-6D);
    }

    @Test
    void boostRegeneratesOnlyAfterDelayWhenEnabled() {
        Sim s = new Sim();
        s.car.regenPerTick = 0.5F;
        s.car.regenDelayTicks = 20;
        s.car.boost = 10.0F;
        s.car.ticksSinceBoost = 0;
        s.run(20);
        assertEquals(10.0F, s.car.boost, 1e-6F);
        s.run(21);
        assertTrue(s.car.boost > 10.0F);
        s.run(1000);
        assertEquals(CarPhysics.MAX_BOOST, s.car.boost, 1e-6F);
    }

    @Test
    void jumpLeavesTheGroundAndComesBack() {
        Sim s = new Sim();
        s.run(2);
        s.in.jump = true;
        s.tick();
        assertEquals(CarPhysics.EVENT_JUMP, s.car.event);
        double apex = 0.0D;
        int air = 0;
        while (!s.car.onGround || air == 0) {
            s.tick();
            air++;
            apex = Math.max(apex, s.y);
            assertTrue(air < 100, "never landed");
        }
        assertTrue(apex > 1.5D && apex < 3.5D, "apex " + apex);
        assertTrue(air > 10 && air < 40, "air time " + air);
    }

    @Test
    void doubleJumpWorksOnceInTheAir() {
        Sim s = new Sim();
        s.in.jump = true;
        s.tick();
        s.run(8);
        s.in.jump = true;
        s.tick();
        assertEquals(CarPhysics.EVENT_DOUBLE_JUMP, s.car.event);
        s.run(1);
        s.in.jump = true;
        s.tick();
        assertEquals(CarPhysics.EVENT_NONE, s.car.event, "third jump must not work");
    }

    @Test
    void directionalDoubleJumpIsAFlipThatPushesTheCar() {
        Sim s = new Sim();
        s.in.jump = true;
        s.tick();
        s.run(5);
        s.in.throttle = 1.0F;
        s.in.jump = true;
        double before = s.car.forwardSpeed();
        s.tick();
        assertEquals(CarPhysics.FLIP_FRONT, s.car.flip);
        assertTrue(s.car.forwardSpeed() > before + 0.3D);
    }

    @Test
    void secondJumpExpiresAfterTheWindow() {
        Sim s = new Sim();
        s.y = 500.0D; // stay in the air for the whole test
        s.car.onGround = false;
        s.car.jumpsUsed = 1;
        s.run(CarPhysics.DOUBLE_JUMP_WINDOW + 5);
        assertFalse(s.car.onGround);
        s.in.jump = true;
        s.tick();
        assertEquals(CarPhysics.EVENT_NONE, s.car.event);
    }

    @Test
    void fallingOffALedgeStillAllowsOneJump() {
        Sim s = new Sim();
        s.y = 500.0D;
        s.car.onGround = false;
        s.run(5);
        s.in.jump = true;
        s.tick();
        assertEquals(CarPhysics.EVENT_JUMP, s.car.event);
    }

    @Test
    void steeringRightIncreasesYawAndStandingStillDoesNotTurn() {
        Sim still = new Sim();
        still.in.steer = 1.0F;
        still.run(40);
        assertEquals(0.0F, still.car.yaw, 1e-4F);

        Sim s = new Sim();
        s.in.throttle = 1.0F;
        s.run(30);
        s.in.steer = 1.0F;
        s.run(20);
        assertTrue(s.car.yaw > 20.0F, "yaw " + s.car.yaw);
    }

    @Test
    void reverseSteeringIsInverted() {
        Sim s = new Sim();
        s.in.throttle = -1.0F;
        s.run(40);
        assertTrue(s.car.forwardSpeed() < -0.2D);
        s.in.steer = 1.0F;
        s.run(20);
        assertTrue(s.car.yaw < -10.0F, "yaw " + s.car.yaw);
    }

    @Test
    void sidewaysVelocityIsKilledByGrip() {
        Sim s = new Sim();
        s.car.vx = 0.8D; // pure sideways motion for a car facing +Z
        s.run(15);
        assertTrue(Math.abs(s.car.vx) < 0.01D, "vx " + s.car.vx);
    }

    private static Sim airborne() {
        Sim s = new Sim();
        s.y = 500.0D; // stay in the air for the whole test
        s.car.onGround = false;
        return s;
    }

    @Test
    void airBoostFollowsThePitchOfTheNose() {
        Sim boosted = airborne();
        Sim coasting = airborne();
        boosted.in.boost = true;
        for (Sim s : new Sim[] {boosted, coasting}) {
            s.car.pitch = -50.0F; // nose up
        }
        for (int i = 0; i < 30; i++) {
            boosted.tick();
            coasting.tick();
        }
        assertTrue(boosted.car.vy > coasting.car.vy + 1.0D, "boosted vy " + boosted.car.vy + " vs " + coasting.car.vy);
        assertTrue(boosted.car.horizontalSpeed() > 0.3D);
        double speed = Math.sqrt(boosted.car.vx * boosted.car.vx + boosted.car.vy * boosted.car.vy + boosted.car.vz * boosted.car.vz);
        assertTrue(speed <= CarPhysics.MAX_BOOST_SPEED + 1e-6D);
    }

    @Test
    void boostAtFortyFiveDegreesNoseUpKeepsTheCarFromFalling() {
        Sim s = airborne();
        s.car.vz = 1.0D; // already flying forward
        s.car.pitch = -45.0F;
        s.in.boost = true;
        s.run(40);
        assertTrue(s.car.vy > -0.02D, "vy " + s.car.vy);
    }

    @Test
    void pitchKeysTipTheNoseDownAndUpAndStopWhenReleased() {
        Sim s = airborne();
        s.in.pitch = 1.0F; // W: nose down
        s.run(10);
        assertTrue(s.car.pitch > 30.0F, "pitch " + s.car.pitch);
        s.in.pitch = -1.0F; // S: nose up
        s.run(30);
        assertTrue(s.car.pitch < -30.0F, "pitch " + s.car.pitch);
        s.in.pitch = 0.0F;
        s.run(12);
        float rest = s.car.pitch;
        s.run(10);
        assertEquals(rest, s.car.pitch, 0.1F, "the nose must stop turning");
    }

    @Test
    void wAndSDoNotAcceleratetheCarInTheAir() {
        Sim s = airborne();
        s.in.throttle = 1.0F;
        s.run(10);
        assertEquals(0.0D, s.car.horizontalSpeed(), 1e-9D);
    }

    @Test
    void rollRightAndLeft() {
        Sim s = airborne();
        s.in.roll = 1.0F; // E
        s.run(5);
        assertTrue(s.car.roll > 20.0F && s.car.roll < 120.0F, "roll " + s.car.roll);
        assertEquals(0.0F, s.car.yaw, 1e-3F);
        assertEquals(0.0F, s.car.pitch, 1e-3F);
        s.in.roll = -1.0F; // Q
        s.run(12);
        assertTrue(s.car.roll < -40.0F && s.car.roll > -170.0F, "roll " + s.car.roll);
    }

    @Test
    void fullRollGoesAllTheWayAround() {
        Sim s = airborne();
        s.in.roll = 1.0F;
        float travelled = 0.0F;
        float last = s.car.roll;
        for (int i = 0; i < 40; i++) {
            s.tick();
            travelled += CarPhysicsTest.wrap(s.car.roll - last);
            last = s.car.roll;
        }
        assertTrue(travelled > 360.0F, "rolled " + travelled);
    }

    static float wrap(float deg) {
        deg %= 360.0F;
        return deg > 180.0F ? deg - 360.0F : deg < -180.0F ? deg + 360.0F : deg;
    }

    @Test
    void freeAirRollTurnsTheSteeringIntoRoll() {
        Sim s = airborne();
        s.in.airRollFree = true;
        s.in.steer = 1.0F; // D while the air roll button is held
        s.run(10);
        assertTrue(s.car.roll > 40.0F, "roll " + s.car.roll);
        assertEquals(0.0F, s.car.yaw, 1e-3F);

        Sim yaw = airborne();
        yaw.in.steer = 1.0F;
        yaw.run(10);
        assertTrue(yaw.car.yaw > 20.0F, "yaw " + yaw.car.yaw);
        assertEquals(0.0F, yaw.car.roll, 1e-3F);
    }

    @Test
    void yawIsAboutTheCarsOwnUpAxisSoARolledCarDivesWhenYawing() {
        // after a 90 degree roll to the right the roof points to the right: yawing right tips the nose DOWN
        Sim s = airborne();
        s.car.roll = 90.0F;
        s.in.steer = 1.0F;
        s.run(10);
        assertTrue(s.car.pitch > 20.0F, "pitch " + s.car.pitch);
        assertEquals(0.0F, s.car.yaw, 1.0F);
    }

    @Test
    void pitchIsAboutTheCarsOwnRightAxisSoARolledCarSwingsSideways() {
        // on its side (roof to the right) pitching "down" swings the nose to the side instead of down
        Sim s = airborne();
        s.car.roll = 90.0F;
        s.in.pitch = 1.0F;
        s.run(10);
        assertTrue(Math.abs(s.car.yaw) > 20.0F, "yaw " + s.car.yaw);
        assertEquals(0.0F, s.car.pitch, 1.0F);
    }

    @Test
    void eulerAnglesRoundTripThroughTheBasis() {
        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < 500; i++) {
            double yaw = rnd.nextDouble() * 360.0D - 180.0D;
            double pitch = rnd.nextDouble() * 170.0D - 85.0D;
            double roll = rnd.nextDouble() * 360.0D - 180.0D;
            double[] f = new double[3];
            double[] u = new double[3];
            double[] r = new double[3];
            CarPhysics.basis(yaw, pitch, roll, f, u, r);
            double[] e = CarPhysics.euler(f, u, 0.0D);
            assertEquals(yaw, e[0], 1e-6D);
            assertEquals(pitch, e[1], 1e-6D);
            assertEquals(roll, e[2], 1e-6D);
        }
    }

    @Test
    void basisAxesMatchTheDocumentedConventions() {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        // yaw 0 faces +Z, its right side is -X (west)
        CarPhysics.basis(0.0D, 0.0D, 0.0D, f, u, r);
        assertEquals(1.0D, f[2], 1e-9D);
        assertEquals(1.0D, u[1], 1e-9D);
        assertEquals(-1.0D, r[0], 1e-9D);
        // positive pitch = nose down
        CarPhysics.basis(0.0D, 30.0D, 0.0D, f, u, r);
        assertTrue(f[1] < 0.0D);
        // positive roll = right side down
        CarPhysics.basis(0.0D, 0.0D, 30.0D, f, u, r);
        assertTrue(r[1] < 0.0D);
    }

    @Test
    void landingLevelsTheCarOutAgain() {
        Sim s = new Sim();
        s.car.pitch = 40.0F;
        s.car.roll = 120.0F;
        s.run(12);
        assertEquals(0.0F, s.car.pitch, 0.5F);
        assertEquals(0.0F, s.car.roll, 0.5F);
    }
}
