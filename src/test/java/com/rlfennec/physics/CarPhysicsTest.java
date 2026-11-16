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

    @Test
    void airBoostFollowsThePitchOfTheNose() {
        Sim boosted = new Sim();
        Sim coasting = new Sim();
        for (Sim s : new Sim[] {boosted, coasting}) {
            s.y = 500.0D;
            s.car.onGround = false;
            s.in.boost = s == boosted;
        }
        boosted.in.pitchTarget = -50.0F; // aiming up
        coasting.in.pitchTarget = -50.0F;
        for (int i = 0; i < 30; i++) {
            boosted.tick();
            coasting.tick();
        }
        assertEquals(-50.0F, boosted.car.pitch, 0.01F);
        assertTrue(boosted.car.vy > coasting.car.vy + 1.0D, "boosted vy " + boosted.car.vy + " vs " + coasting.car.vy);
        assertTrue(boosted.car.horizontalSpeed() > 0.3D);
        double speed = Math.sqrt(boosted.car.vx * boosted.car.vx + boosted.car.vy * boosted.car.vy + boosted.car.vz * boosted.car.vz);
        assertTrue(speed <= CarPhysics.MAX_BOOST_SPEED + 1e-6D);
    }

    @Test
    void boostAtFortyFiveDegreesNoseUpKeepsTheCarFromFalling() {
        Sim s = new Sim();
        s.y = 500.0D;
        s.car.onGround = false;
        s.car.vz = 1.0D; // already flying forward
        s.in.boost = true;
        s.in.pitchTarget = -45.0F;
        s.car.pitch = -45.0F;
        s.run(40);
        assertTrue(s.car.vy > -0.02D, "vy " + s.car.vy);
    }

    @Test
    void theNoseFollowsTheAimAtALimitedRateAndLevelsOutOnTheGround() {
        Sim s = new Sim();
        s.y = 500.0D;
        s.car.onGround = false;
        s.in.pitchTarget = 60.0F;
        s.tick();
        assertEquals(CarPhysics.AIR_PITCH_RATE, s.car.pitch, 1e-4F);
        s.run(30);
        assertEquals(60.0F, s.car.pitch, 1e-4F);
        // W / S do nothing in the air
        s.in.pitchTarget = 0.0F;
        s.in.throttle = 1.0F;
        s.run(30);
        assertEquals(0.0F, s.car.pitch, 1e-4F);

        // back on the road the car stands level again
        s.car.pitch = 40.0F;
        s.car.onGround = true;
        s.run(10);
        assertEquals(0.0F, s.car.pitch, 0.5F);
    }
}
