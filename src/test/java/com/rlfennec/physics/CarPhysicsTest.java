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
            car.groundDistance = Math.max(0.0D, y);
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
        s.in.jumpHeld = true;
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
        assertTrue(apex > 2.8D && apex < 4.2D, "apex " + apex);
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
        s.in.pitch = 1.0F; // stick forward
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
        s.run(6);
        assertTrue(s.car.pitch > 30.0F, "pitch " + s.car.pitch);
        s.in.pitch = -1.0F; // S: nose up
        s.run(14);
        assertTrue(s.car.pitch < -20.0F, "pitch " + s.car.pitch);
        s.in.pitch = 0.0F;
        s.run(12);
        float rest = s.car.pitch;
        s.run(10);
        assertEquals(rest, s.car.pitch, 0.1F, "the nose must stop turning");
    }

    @Test
    void throttleInTheAirPushesTheCarSlowlyForwardAndBackward() {
        Sim fwd = airborne();
        fwd.in.throttle = 1.0F;
        fwd.run(10);
        assertTrue(fwd.car.forwardSpeed() > 0.15D && fwd.car.forwardSpeed() < 0.25D, "speed " + fwd.car.forwardSpeed());
        fwd.run(200);
        assertEquals(CarPhysics.AIR_THROTTLE_MAX_FORWARD, fwd.car.forwardSpeed(), 0.03D); // never faster than that on its own

        Sim back = airborne();
        back.in.throttle = -1.0F;
        back.run(200);
        assertEquals(-CarPhysics.AIR_THROTTLE_MAX_BACKWARD, back.car.forwardSpeed(), 0.03D);

        Sim half = airborne();
        half.in.throttle = 0.5F; // half a trigger: half the push
        half.run(10);
        assertEquals(0.1D, half.car.forwardSpeed(), 0.02D);
    }

    @Test
    void airThrottleDoesNotSlowAFasterCarOrFightTheBoost() {
        Sim fast = airborne();
        fast.car.vz = 1.0D;
        fast.in.throttle = 1.0F;
        fast.run(10);
        assertEquals(1.0D, fast.car.forwardSpeed(), 0.03D); // no extra push above the limit, only the tiny air drag

        Sim boosted = airborne();
        boosted.in.throttle = 1.0F;
        boosted.in.boost = true;
        Sim onlyBoost = airborne();
        onlyBoost.in.boost = true;
        boosted.run(10);
        onlyBoost.run(10);
        assertEquals(onlyBoost.car.forwardSpeed(), boosted.car.forwardSpeed(), 1e-9D);
    }

    @Test
    void airThrottleIsOffOnARoofAndDuringADodge() {
        Sim roof = airborne();
        roof.car.roll = 180.0F;
        roof.in.throttle = 1.0F;
        roof.run(5);
        assertEquals(0.0D, roof.car.horizontalSpeed(), 1e-9D);

        Sim plain = midAir();
        Sim holding = midAir();
        holding.in.throttle = 1.0F; // W held for a forward flip does not make the flip faster
        dodge(plain, 1.0F, 0.0F);
        dodge(holding, 1.0F, 0.0F);
        holding.in.throttle = 1.0F;
        plain.run(CarPhysics.DODGE_TICKS - 2);
        holding.run(CarPhysics.DODGE_TICKS - 2);
        assertEquals(plain.car.forwardSpeed(), holding.car.forwardSpeed(), 0.03D);
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

    /** y component of the roof direction: 1 = upright, -1 = upside down. */
    static double roofUp(CarPhysics c) {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        CarPhysics.basis(c.yaw, c.pitch, c.roll, f, u, r);
        return u[1];
    }

    /** y component of the nose direction: 1 = straight up. */
    static double noseUp(CarPhysics c) {
        return CarPhysics.forward(c.yaw, c.pitch)[1];
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

    // ------------------------------------------------------------------ dodge / flip cancel / wave dash

    private static Sim midAir() {
        Sim s = airborne();
        s.car.jumpsUsed = 1; // the first jump is gone, the second one is the dodge
        s.run(3);
        return s;
    }

    private static void dodge(Sim s, float pitch, float steer) {
        s.in.pitch = pitch;
        s.in.steer = steer;
        s.in.jump = true;
        s.tick();
        s.in.pitch = 0.0F;
        s.in.steer = 0.0F;
    }

    @Test
    void withoutADirectionTheSecondJumpIsAPlainDoubleJump() {
        Sim s = midAir();
        double speed = s.car.horizontalSpeed();
        dodge(s, 0.0F, 0.0F);
        assertEquals(CarPhysics.EVENT_DOUBLE_JUMP, s.car.event);
        assertEquals(CarPhysics.FLIP_NONE, s.car.flip);
        assertEquals(CarPhysics.DOUBLE_JUMP_VELOCITY, s.car.vy, 1e-9D);
        assertEquals(speed, s.car.horizontalSpeed(), 1e-9D);
        s.run(20);
        assertEquals(0.0F, s.car.pitch, 1e-3F);
        assertEquals(0.0F, s.car.roll, 1e-3F);
    }

    @Test
    void aSmallPushOfTheStickIsNotADodge() {
        Sim s = midAir();
        dodge(s, 0.3F, 0.2F);
        assertEquals(CarPhysics.FLIP_NONE, s.car.flip);
    }

    @Test
    void theFlipGoesTheWayTheStickPoints() {
        float[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] expected = {CarPhysics.FLIP_FRONT, CarPhysics.FLIP_BACK, CarPhysics.FLIP_RIGHT, CarPhysics.FLIP_LEFT};
        for (int i = 0; i < dirs.length; i++) {
            Sim s = midAir();
            dodge(s, dirs[i][0], dirs[i][1]);
            assertEquals(expected[i], s.car.flip, "direction " + i);
        }
    }

    @Test
    void aForwardFlipTurnsTheNoseDownAndAllTheWayAroundAndEndsUpright() {
        Sim s = midAir();
        dodge(s, 1.0F, 0.0F);
        double lowestNose = 1.0D;
        double lowestRoof = 1.0D;
        for (int i = 0; i < CarPhysics.DODGE_TICKS + 2; i++) {
            s.tick();
            lowestNose = Math.min(lowestNose, noseUp(s.car));
            lowestRoof = Math.min(lowestRoof, roofUp(s.car));
        }
        assertTrue(lowestNose < -0.8D, "the nose went to y=" + lowestNose);
        assertTrue(lowestRoof < -0.8D, "the car was upside down (roof y=" + lowestRoof + ")");
        assertEquals(0.0F, s.car.pitch, 6.0F);
        assertEquals(0.0F, Math.abs(wrap(s.car.roll)), 6.0F);
    }

    @Test
    void aBackFlipTurnsTheNoseUp() {
        Sim s = midAir();
        dodge(s, -1.0F, 0.0F);
        double highestNose = -1.0D;
        for (int i = 0; i < CarPhysics.DODGE_TICKS + 2; i++) {
            s.tick();
            highestNose = Math.max(highestNose, noseUp(s.car));
        }
        assertTrue(highestNose > 0.8D, "the nose only went to y=" + highestNose);
        assertEquals(0.0F, Math.abs(wrap(s.car.pitch)), 6.0F);
    }

    @Test
    void aSideFlipRollsTheCarAndADiagonalOneReturnsUpright() {
        Sim s = midAir();
        dodge(s, 0.0F, 1.0F);
        s.run(4);
        assertTrue(s.car.roll > 60.0F, "roll " + s.car.roll);
        s.run(CarPhysics.DODGE_TICKS);
        assertEquals(0.0F, Math.abs(wrap(s.car.roll)), 6.0F);

        Sim d = midAir();
        dodge(d, 1.0F, 1.0F);
        d.run(CarPhysics.DODGE_TICKS + 2);
        assertEquals(0.0F, Math.abs(wrap(d.car.pitch)), 12.0F);
        assertEquals(0.0F, Math.abs(wrap(d.car.roll)), 12.0F);
    }

    @Test
    void theDodgeKeepsItsSpeedBoost() {
        Sim s = midAir();
        double before = s.car.forwardSpeed();
        dodge(s, 1.0F, 0.0F);
        assertTrue(s.car.forwardSpeed() > before + 0.3D);
    }

    @Test
    void holdingTheOppositeDirectionCancelsTheFlipButKeepsTheSpeed() {
        Sim cancelled = midAir();
        Sim flipped = midAir();
        dodge(cancelled, 1.0F, 0.0F);
        dodge(flipped, 1.0F, 0.0F);
        float maxTilt = 0.0F;
        for (int i = 0; i < CarPhysics.CANCEL_GRACE_TICKS - 1; i++) {
            cancelled.in.pitch = -1.0F; // stick back
            cancelled.tick();
            flipped.tick();
            maxTilt = Math.max(maxTilt, Math.abs(wrap(cancelled.car.pitch)));
        }
        cancelled.in.pitch = 0.0F;
        cancelled.run(CarPhysics.DODGE_TICKS);
        flipped.run(CarPhysics.DODGE_TICKS);
        assertTrue(maxTilt < 45.0F, "the cancelled flip still tipped " + maxTilt); // it coasts a little, but is nowhere near a flip
        assertTrue(maxTilt > 3.0F, "the cancel must not be instant, tilt " + maxTilt);
        assertEquals(maxTilt, Math.abs(wrap(cancelled.car.pitch)), 3.0F); // and it stays where it came to rest
        assertEquals(flipped.car.vx, cancelled.car.vx, 1e-9D * 100);
        assertEquals(flipped.car.vz, cancelled.car.vz, 1e-9D * 100);
    }

    @Test
    void cancellingStopsTheFlipWhereItIsAndItDoesNotRestart() {
        Sim s = midAir();
        dodge(s, 1.0F, 0.0F);
        s.run(1); // a sixth of the flip
        double tiltAtCancel = tilt(s.car);
        assertTrue(tiltAtCancel > 20.0D);
        s.in.pitch = -1.0F;
        s.tick();
        double firstTick = tilt(s.car);
        assertTrue(firstTick > tiltAtCancel + 5.0D, "the flip must not stop on the very tick of the cancel (" + tiltAtCancel + " -> " + firstTick + ")");
        s.run(5);
        double stopped = tilt(s.car);
        assertTrue(stopped > firstTick, "the car keeps turning a little after the cancel");
        assertTrue(stopped < tiltAtCancel + 60.0D, "but it does stop: " + stopped);
        s.run(2); // (the stick is still held back, but for a few ticks it must not tip the car the other way)
        assertEquals(stopped, tilt(s.car), 1.0D, "the nose must stay where the cancel brought it to rest");
        s.in.pitch = 0.0F; // let go: the flip must not start again
        s.run(10);
        assertEquals(stopped, tilt(s.car), 1.0D);
    }

    /** Angle in degrees between the roof of the car and straight up. */
    private static double tilt(CarPhysics car) {
        return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, roofUp(car)))));
    }

    @Test
    void theCancelBringsTheFlipToRestInAboutSevenTicks() {
        Sim s = midAir();
        dodge(s, 1.0F, 0.0F);
        s.run(2); // fast rotation
        double before = tilt(s.car);
        s.in.pitch = -1.0F;
        double[] angle = new double[7];
        for (int i = 0; i < angle.length; i++) {
            s.tick();
            angle[i] = tilt(s.car);
        }
        double firstStep = angle[0] - before;
        double secondStep = angle[1] - angle[0];
        double thirdStep = angle[2] - angle[1];
        double lastStep = Math.abs(angle[6] - angle[5]);
        assertTrue(firstStep > 10.0D, "the flip keeps turning on the tick of the cancel (" + firstStep + ")");
        assertTrue(firstStep > secondStep && secondStep > thirdStep && thirdStep > 0.0D, "the turn dies away gradually");
        assertTrue(lastStep < 1.0D, "still turning " + lastStep + " degrees per tick after 7 ticks");
    }

    @Test
    void halfFlipEndsWheelsDownFacingTheOtherWay() {
        Sim s = midAir();
        s.car.vz = -0.3D; // reversing: the car travels backwards while facing +z
        dodge(s, -1.0F, 0.0F); // back flip
        int guard = 0;
        while (tilt(s.car) < 110.0D && guard++ < 30) {
            s.tick(); // flip until the car is well past vertical (the cancel takes a moment to bite)
        }
        s.in.pitch = 1.0F; // stick forward: cancel the flip
        s.tick();
        s.in.pitch = 0.0F;
        s.run(CarPhysics.CANCEL_GRACE_TICKS); // it coasts to a stop, upside down
        assertTrue(roofUp(s.car) < -0.7D, "the car must have come to rest upside down, roof up " + roofUp(s.car));
        s.in.roll = 1.0F; // air roll the wheels back down
        guard = 0;
        while (roofUp(s.car) < 0.85D && guard++ < 30) {
            s.tick();
        }
        s.in.roll = 0.0F;
        s.run(6);
        double[] nose = CarPhysics.forward(s.car.yaw, s.car.pitch);
        assertTrue(roofUp(s.car) > 0.85D, "roof up " + roofUp(s.car));
        assertTrue(nose[2] < -0.7D, "the car must now face -z, nose = " + java.util.Arrays.toString(nose));
        assertTrue(Math.abs(nose[1]) < 0.5D, "nose should be roughly level, y = " + nose[1]);
    }

    @Test
    void aFlipCanBeDoneWhileTheFreeAirRollButtonIsHeld() {
        Sim s = midAir();
        s.in.airRollFree = true;
        dodge(s, 1.0F, 0.0F);
        assertEquals(CarPhysics.FLIP_FRONT, s.car.flip);
        double lowestNose = 1.0D;
        for (int i = 0; i < CarPhysics.DODGE_TICKS; i++) {
            s.tick();
            lowestNose = Math.min(lowestNose, noseUp(s.car));
        }
        assertTrue(lowestNose < -0.8D, "it flipped: " + lowestNose);

        Sim side = midAir();
        side.in.airRollFree = true;
        dodge(side, 0.0F, 1.0F);
        assertEquals(CarPhysics.FLIP_RIGHT, side.car.flip);
        side.run(6);
        assertTrue(side.car.roll > 40.0F, "roll " + side.car.roll);
    }

    @Test
    void aTapIsAShortHopAndHoldingGivesTheFullJump() {
        double[] apex = new double[2];
        for (int held = 0; held < 2; held++) {
            Sim s = new Sim();
            s.run(2);
            s.in.jump = true;
            s.in.jumpHeld = held == 1;
            for (int i = 0; i < 40; i++) {
                s.tick();
                apex[held] = Math.max(apex[held], s.y);
            }
        }
        assertTrue(apex[0] > 0.9D && apex[0] < 1.3D, "tap apex " + apex[0]);
        assertTrue(apex[1] > 2.8D && apex[1] < 4.2D, "hold apex " + apex[1]);
    }

    @Test
    void waveDashFromAShortHopWithTheDodgeRightAfterTheJump() {
        double[] after = new double[2];
        boolean sawEvent = false;
        for (int dodgeIt = 0; dodgeIt < 2; dodgeIt++) {
            Sim s = new Sim();
            s.in.throttle = 1.0F;
            s.run(40);
            s.in.throttle = 0.0F;
            s.in.jump = true;
            s.tick(); // tap: short hop
            s.in.jumpHeld = false;
            s.tick();
            if (dodgeIt == 1) {
                dodge(s, 1.0F, 0.0F);
            }
            int guard = 0;
            while (!s.car.onGround && guard++ < 40) {
                s.tick();
            }
            s.tick();
            sawEvent |= s.car.event == CarPhysics.EVENT_WAVE_DASH;
            after[dodgeIt] = s.car.forwardSpeed();
        }
        assertTrue(sawEvent, "the landing right after the dodge must be a wave dash");
        assertTrue(after[1] > after[0] + 0.25D, "speed without " + after[0] + " with " + after[1]);
    }

    @Test
    void pitchInputDoesNotAddToARunningFlip() {
        Sim a = midAir();
        Sim b = midAir();
        dodge(a, 1.0F, 0.0F);
        dodge(b, 1.0F, 0.0F);
        for (int i = 0; i < 10; i++) {
            b.in.pitch = 1.0F; // pushing the same way adds nothing extra
            a.tick();
            b.tick();
        }
        assertEquals(a.car.pitch, b.car.pitch, 1e-3F);
    }

    /** A car at speed 1.0 falling towards the road; after {@code fallTicks} it dodges forward. Returns the sim after the first tick on the road. */
    private static Sim fallAndDodge(int fallTicks, boolean dodge) {
        Sim s = new Sim();
        s.in.throttle = 1.0F;
        s.run(40);
        s.in.throttle = 0.0F;
        s.y = 2.0D;
        s.car.onGround = false;
        s.car.vy = -0.2D;
        s.car.jumpsUsed = 1;
        s.run(fallTicks);
        if (dodge) {
            dodge(s, 1.0F, 0.0F);
        }
        int guard = 0;
        while (!s.car.onGround && guard++ < 80) {
            s.tick();
        }
        s.tick(); // the first tick on the road carries the dash
        return s;
    }

    @Test
    void waveDashLandingJustAfterADodgeGivesSpeed() {
        double without = fallAndDodge(3, false).car.forwardSpeed();
        int successes = 0;
        for (int fall = 0; fall <= 8; fall++) {
            Sim s = fallAndDodge(fall, true);
            if (s.car.forwardSpeed() > without + 0.2D) {
                successes++;
            }
        }
        assertTrue(successes >= 1, "no dodge timing produced a wave dash");
        assertTrue(successes < 9, "every timing gave a wave dash, the window should be limited");
    }

    @Test
    void aDodgeFarAboveTheRoadDoesNotWaveDash() {
        Sim s = new Sim();
        s.in.throttle = 1.0F;
        s.run(40);
        s.in.throttle = 0.0F;
        s.y = 30.0D;
        s.car.onGround = false;
        s.car.vy = 0.0D;
        s.car.jumpsUsed = 1;
        double before = s.car.forwardSpeed();
        dodge(s, 1.0F, 0.0F);
        int guard = 0;
        while (!s.car.onGround && guard++ < 200) {
            s.tick();
        }
        s.tick();
        assertTrue(s.car.forwardSpeed() <= Math.max(before + 0.0D, CarPhysics.MAX_SPEED) + 0.45D);
        assertEquals(CarPhysics.EVENT_NONE, s.car.event);
    }

    @Test
    void waveDashOnlyHappensOnceAndNeedsALateLandingAfterTheDodge() {
        Sim s = midAir();
        dodge(s, 1.0F, 0.0F);
        s.run(CarPhysics.WAVE_DASH_WINDOW + 3); // still in the air long after
        s.car.onGround = true;
        double before = s.car.forwardSpeed();
        s.tick();
        assertEquals(CarPhysics.EVENT_NONE, s.car.event);
        assertTrue(s.car.forwardSpeed() <= Math.max(before, CarPhysics.MAX_SPEED) + 1e-6D);
    }

    @Test
    void steeringAndAirTurnSpeedCanBeScaled() {
        Sim normal = new Sim();
        Sim fast = new Sim();
        fast.car.groundSteerScale = 2.0F;
        for (Sim s : new Sim[] {normal, fast}) {
            s.in.throttle = 1.0F;
            s.run(30);
            s.in.steer = 1.0F;
            s.run(10);
        }
        assertTrue(fast.car.yaw > normal.car.yaw * 1.5F, normal.car.yaw + " vs " + fast.car.yaw);

        Sim a = airborne();
        Sim b = airborne();
        b.car.airYawScale = 0.5F;
        b.car.airPitchScale = 0.5F;
        b.car.airRollScale = 0.5F;
        for (Sim s : new Sim[] {a, b}) {
            s.in.pitch = 1.0F;
            s.in.roll = 1.0F;
            s.in.steer = 1.0F;
            s.run(5);
        }
        // the half-speed car has not turned as far
        assertTrue(b.car.pitch < a.car.pitch);
        assertTrue(b.car.roll < a.car.roll);
    }

    /** A car at speed 0.8 that falls onto the road at the given attitude; returns the sim on the tick it touches down. */
    private static Sim fallOn(float pitch, float roll, double height, double fallSpeed) {
        Sim s = new Sim();
        s.car.vz = 0.8D;
        s.car.pitch = pitch;
        s.car.roll = roll;
        s.car.onGround = false;
        s.y = height;
        s.car.vy = -fallSpeed;
        s.car.jumpsUsed = 1;
        int guard = 0;
        while (!s.car.onGround && guard++ < 100) {
            s.tick();
        }
        assertTrue(s.car.onGround, "never landed");
        return s;
    }

    @Test
    void landingOnTheRoofBouncesAndTheCarStaysCrashedOnItsRoof() {
        Sim s = fallOn(0.0F, 180.0F, 3.0D, 0.3D);
        s.tick(); // touchdown: it bounces
        assertTrue(s.car.crashed);
        assertTrue(s.car.vy > 0.2D, "bounce speed " + s.car.vy);
        assertFalse(s.car.stranded, "still bouncing");
        s.run(3);
        assertFalse(s.car.onGround, "in the air again");
        double[] roofBefore = roofVector(s.car);
        assertTrue(roofBefore[1] < -0.9D, "still upside down in the bounce");

        s.run(100);
        assertTrue(s.car.onGround);
        assertTrue(s.car.stranded, "the car lies on the road waiting for the jump key");
        assertTrue(roofUp(s.car) < -0.9D, "no self-righting: roof y = " + roofUp(s.car));
        assertTrue(s.car.horizontalSpeed() < 0.01D, "it slid to a halt");
        assertEquals(0.0D, s.y, 1e-9D);
    }

    @Test
    void aCrashedCarCannotBeDrivenOrBoostedOrJumpedUntilItIsTurnedOver() {
        Sim s = fallOn(0.0F, 180.0F, 3.0D, 0.3D);
        s.run(120);
        assertTrue(s.car.stranded);
        s.in.throttle = 1.0F;
        s.in.steer = 1.0F;
        s.in.boost = true;
        float yaw = s.car.yaw;
        float boost = s.car.boost;
        s.run(30);
        assertEquals(0.0D, s.car.horizontalSpeed(), 0.01D);
        assertEquals(yaw, s.car.yaw, 0.01F);
        assertEquals(boost, s.car.boost, 1e-6F, "no boost is used while lying on the roof");
        assertFalse(s.car.boosting);
        assertEquals(0.0D, s.y, 1e-9D, "it did not take off");
    }

    @Test
    void landingOnTheNoseBouncesAndTopplesOverButNeverBackOntoItsWheels() {
        Sim s = fallOn(85.0F, 0.0F, 3.0D, 0.3D); // nose almost straight down
        s.tick();
        assertTrue(s.car.crashed);
        assertTrue(s.car.vy > 0.2D, "it bounces off its nose");
        s.run(150);
        assertTrue(s.car.stranded);
        assertTrue(roofUp(s.car) < 0.1D, "it must not have righted itself, roof y = " + roofUp(s.car));
        // it lies flat now: the nose is level (or the car is on its side / roof)
        double[] nose = CarPhysics.forward(s.car.yaw, s.car.pitch);
        assertTrue(Math.abs(nose[1]) < 0.3D, "nose y " + nose[1]);
    }

    @Test
    void landingOnTheSideStaysOnTheSide() {
        Sim s = fallOn(0.0F, 90.0F, 1.0D, 0.3D);
        s.run(100);
        assertTrue(s.car.stranded);
        assertEquals(90.0F, wrap(s.car.roll), 8.0F, "it lies on its right side");
    }

    @Test
    void aSlowCrashDoesNotBounce() {
        Sim s = fallOn(0.0F, 180.0F, 0.1D, 0.15D);
        s.tick();
        assertTrue(s.car.crashed);
        assertTrue(s.car.vy <= 0.0D, "no bounce, vy " + s.car.vy);
        assertTrue(s.car.stranded);
    }

    @Test
    void theBounceKeepsAirControlSoTheDriverCanSaveTheLanding() {
        Sim s = fallOn(0.0F, 180.0F, 6.0D, 0.6D);
        s.tick();
        assertTrue(s.car.crashed && !s.car.onGround || s.car.vy > 0.0D);
        s.in.roll = 1.0F; // air roll back onto the wheels while it bounces
        int guard = 0;
        while (roofUp(s.car) < 0.9D && guard++ < 40) {
            s.tick();
        }
        s.in.roll = 0.0F;
        assertTrue(roofUp(s.car) > 0.9D, "rolled upright in the bounce");
        s.run(80);
        assertTrue(s.car.onGround);
        assertFalse(s.car.crashed, "landing on the wheels ends the crash");
        assertFalse(s.car.stranded);
        s.in.throttle = 1.0F;
        s.run(10);
        assertTrue(s.car.forwardSpeed() > 0.3D, "the car can be driven again");
    }

    @Test
    void jumpTurnsACrashedCarBackOntoItsWheelsInExactlyOneSecond() {
        Sim s = fallOn(0.0F, 180.0F, 3.0D, 0.3D);
        s.run(120);
        assertTrue(s.car.stranded);
        float yaw = s.car.yaw;
        s.in.jump = true;
        s.tick();
        assertEquals(CarPhysics.EVENT_RECOVER, s.car.event);
        assertTrue(s.car.recovering);
        assertFalse(s.car.stranded, "the tip goes away once it is turning");
        assertEquals(0.0D, s.y, 1e-9D, "it turns over, it does not jump");

        double[] previous = roofVector(s.car);
        double biggestStep = 0.0D;
        for (int i = 1; i < CarPhysics.RECOVERY_TICKS; i++) {
            s.in.jump = true; // pressing jump again changes nothing
            s.tick();
            double[] roof = roofVector(s.car);
            biggestStep = Math.max(biggestStep, Math.toDegrees(Math.acos(Math.min(1.0D, dot3(previous, roof)))));
            previous = roof;
            assertTrue(s.car.crashed, "still turning after " + (i + 1) + " ticks");
            assertEquals(CarPhysics.EVENT_NONE, s.car.event);
        }
        assertTrue(roofUp(s.car) > -0.9D || biggestStep > 0.0D);
        assertTrue(biggestStep < 25.0D, "smooth: at most " + biggestStep + " degrees per tick");
        assertTrue(s.car.crashed, "not done a tick before the second is over");
        s.tick(); // the 20th tick of the turn
        assertFalse(s.car.crashed);
        assertFalse(s.car.recovering);
        assertEquals(0.0F, s.car.pitch, 1e-3F);
        assertEquals(0.0F, s.car.roll, 1e-3F);
        assertEquals(yaw, s.car.yaw, 1e-3F, "it keeps facing the way it faced");
        assertEquals(1.0D, roofUp(s.car), 1e-3D);

        // and it drives again
        s.in.jump = false;
        s.in.throttle = 1.0F;
        s.run(10);
        assertTrue(s.car.forwardSpeed() > 0.3D || Math.abs(s.car.vx) + Math.abs(s.car.vz) > 0.3D);
    }

    @Test
    void theTurnOverIsGradualNotInstant() {
        Sim s = fallOn(0.0F, 180.0F, 3.0D, 0.3D);
        s.run(120);
        s.in.jump = true;
        s.tick();
        s.run(4);
        assertTrue(roofUp(s.car) < -0.8D, "after 0.25 s it has hardly begun, roof y " + roofUp(s.car));
        s.run(6);
        assertTrue(Math.abs(roofUp(s.car)) < 0.8D, "after 0.5 s it is about half way, roof y " + roofUp(s.car));
        s.run(8);
        assertTrue(roofUp(s.car) > 0.9D, "almost there after 0.9 s, roof y " + roofUp(s.car));
    }

    @Test
    void theStickChoosesWhichWayACarOnItsRoofTurnsOver() {
        float[] rolls = new float[2];
        float[] sticks = {-1.0F, 1.0F};
        for (int i = 0; i < 2; i++) {
            Sim s = fallOn(0.0F, 180.0F, 3.0D, 0.3D);
            s.run(120);
            s.in.steer = sticks[i];
            s.in.jump = true;
            s.tick();
            s.in.steer = 0.0F;
            s.run(4);
            rolls[i] = wrap(s.car.roll);
        }
        // turning right (stick right) takes the roll from 180 down through 90 ... the two directions differ
        assertTrue(rolls[0] * rolls[1] < 0.0F, "left " + rolls[0] + ", right " + rolls[1]);
    }

    @Test
    void aNormalLandingOnTheWheelsStillLevelsOutWithoutACrash() {
        Sim s = fallOn(35.0F, 20.0F, 2.0D, 0.4D);
        s.tick();
        assertFalse(s.car.crashed);
        s.run(12);
        assertEquals(0.0F, s.car.pitch, 0.5F);
        assertEquals(0.0F, s.car.roll, 0.5F);
    }

    private static double[] roofVector(CarPhysics car) {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        CarPhysics.basis(car.yaw, car.pitch, car.roll, f, u, r);
        return u;
    }

    private static double dot3(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    @Test
    void wavedashLandingNoseDownOnTheWheelsStillWorks() {
        // 4 ticks into a forward flip (about 81 degrees) the car touches the road
        double without = fallAndDodge(3, false).car.forwardSpeed();
        boolean any = false;
        for (int fall = 0; fall <= 10; fall++) {
            if (fallAndDodge(fall, true).car.forwardSpeed() > without + 0.2D) {
                any = true;
            }
        }
        assertTrue(any);
    }
}
