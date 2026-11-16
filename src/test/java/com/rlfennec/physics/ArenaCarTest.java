package com.rlfennec.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The car on the curved floor, walls and ceiling of the arena. */
class ArenaCarTest {
    private static final class Sim {
        final CarPhysics car = new CarPhysics();
        final CarPhysics.Input in = new CarPhysics.Input();
        double worstPenetration;
        int ticks;

        Sim(double x, double y, double z, double yawDeg) {
            car.arena = true;
            car.x = x;
            car.y = y;
            car.z = z;
            car.yaw = (float) yawDeg;
        }

        void tick() {
            car.step(in);
            in.jump = false;
            worstPenetration = Math.min(worstPenetration, lowestHullPoint(car));
            ticks++;
        }

        void run(int n) {
            for (int i = 0; i < n; i++) {
                tick();
            }
        }

        double speed() {
            return Math.sqrt(car.vx * car.vx + car.vy * car.vy + car.vz * car.vz);
        }

        double roofY() {
            double[] f = new double[3];
            double[] u = new double[3];
            double[] r = new double[3];
            CarPhysics.basis(car.yaw, car.pitch, car.roll, f, u, r);
            return u[1];
        }

        double[] roof() {
            double[] f = new double[3];
            double[] u = new double[3];
            double[] r = new double[3];
            CarPhysics.basis(car.yaw, car.pitch, car.roll, f, u, r);
            return u;
        }
    }

    /** The smallest distance of any point of the car's shape to the arena surface (negative = inside the wall). */
    static double lowestHullPoint(CarPhysics car) {
        double[] f = new double[3];
        double[] u = new double[3];
        double[] r = new double[3];
        CarPhysics.basis(car.yaw, car.pitch, car.roll, f, u, r);
        double lowest = Double.MAX_VALUE;
        for (float[] pts : new float[][] {CarHull.POINTS, CarHull.WHEELS}) {
            for (int i = 0; i < pts.length; i += 3) {
                double hx = pts[i];
                double hy = pts[i + 1];
                double hz = pts[i + 2];
                double px = car.x + f[0] * hx + u[0] * hy + r[0] * hz;
                double py = car.y + f[1] * hx + u[1] * hy + r[1] * hz;
                double pz = car.z + f[2] * hx + u[2] * hy + r[2] * hz;
                lowest = Math.min(lowest, Arena.sd(px, py, pz));
            }
        }
        return lowest;
    }

    // yaw: f = (-sin yaw, 0, cos yaw): 0 faces +z, -90 faces +x, 90 faces -x, 180 faces -z
    private static final double EAST = -90.0D;

    @Test
    void aCarOnTheFloorStaysOnItAndDrivesLikeBefore() {
        Sim s = new Sim(0, 0.02, -30, 0);
        s.run(5);
        assertTrue(s.car.onSurface);
        s.in.throttle = 1.0F;
        s.run(120);
        assertTrue(s.car.onSurface);
        assertEquals(CarPhysics.MAX_SPEED, s.speed(), 0.05D);
        assertEquals(0.0D, s.car.y, 0.06D);
        assertEquals(0.0D, s.car.pitch, 0.5F);
        assertEquals(0.0D, s.car.roll, 0.5F);
        assertTrue(s.worstPenetration > -0.05D, "penetration " + s.worstPenetration);
    }

    @Test
    void steeringTurnsTheCarOnTheFloor() {
        Sim s = new Sim(0, 0.02, 0, 0);
        s.in.throttle = 1.0F;
        s.run(30);
        double yawBefore = s.car.yaw;
        s.in.steer = 1.0F;
        s.run(20);
        assertTrue(Math.abs(s.car.yaw - yawBefore) > 20.0D, "turned " + (s.car.yaw - yawBefore));
        assertTrue(s.car.onSurface);
    }

    @Test
    void aCarThatFallsOntoItsWheelsLandsWithoutBouncing() {
        Sim s = new Sim(0, 12.0, 0, 0);
        s.run(60);
        assertTrue(s.car.onSurface);
        assertEquals(0.0D, s.car.y, 0.06D);
        assertFalse(s.car.crashed);
        assertEquals(0.0D, s.car.vy, 0.1D);
    }

    @Test
    void drivingIntoTheRampCurvesUpTheWallAndKeepsTheCarOnTheSurface() {
        Sim s = new Sim(45, 0.02, 0, EAST);
        s.in.throttle = 1.0F;
        double maxY = 0.0D;
        double minRoofY = 1.0D;
        for (int i = 0; i < 100; i++) {
            s.tick();
            maxY = Math.max(maxY, s.car.y);
            minRoofY = Math.min(minRoofY, s.roofY());
            assertTrue(s.car.onSurface || i < 3, "left the surface at tick " + i + " (y=" + s.car.y + ", x=" + s.car.x + ")");
        }
        assertTrue(maxY > 3.0D, "the car must go up the ramp, max height " + maxY);
        assertTrue(minRoofY < 0.5D, "the car tilted with the ramp, roof y " + minRoofY);
        assertTrue(s.worstPenetration > -0.1D, "penetration " + s.worstPenetration);
    }

    @Test
    void theCurveOfTheRampDoesNotCostSpeed() {
        // boosting up the ramp: the car keeps its full speed along the curve (a wall of friction would show here)
        Sim s = new Sim(55, 0.02, 0, EAST);
        s.in.throttle = 1.0F;
        s.in.boost = true;
        double minSpeedOnRamp = 10.0D;
        for (int i = 0; i < 40; i++) {
            s.car.boost = 100.0F;
            s.tick();
            if (s.car.x > 76.0D && s.car.y < 20.0D) {
                minSpeedOnRamp = Math.min(minSpeedOnRamp, s.speed());
            }
        }
        assertTrue(minSpeedOnRamp > 1.5D, "speed on the ramp and the wall " + minSpeedOnRamp);
    }

    @Test
    void withBoostTheCarDrivesUpTheWallAndOntoTheCeiling() {
        Sim s = new Sim(45, 0.02, 0, EAST);
        s.in.throttle = 1.0F;
        s.in.boost = true;
        s.run(70);
        // up the east wall: the roof points to the west
        assertTrue(s.car.onSurface, "on the surface at tick 70, y=" + s.car.y + " x=" + s.car.x);
        assertTrue(s.car.y > 12.0D, "height " + s.car.y);
        s.car.boost = 100.0F;
        s.run(40);
        double maxY = s.car.y;
        s.car.boost = 100.0F;
        s.run(40);
        maxY = Math.max(maxY, s.car.y);
        assertTrue(s.worstPenetration > -0.1D, "penetration " + s.worstPenetration);
        assertTrue(maxY > 30.0D, "it must reach the top, height " + maxY);
    }

    @Test
    void theCarCanDriveUpsideDownOnTheCeiling() {
        // start already on the ceiling, roof up against it
        Sim s = new Sim(0, Arena.HEIGHT - 0.02, -30, 0);
        s.car.roll = 180.0F;
        s.run(5);
        assertTrue(s.car.onSurface, "on the ceiling");
        s.in.throttle = 1.0F;
        s.run(80);
        assertTrue(s.car.onSurface);
        assertTrue(s.roofY() < -0.95D, "roof y " + s.roofY());
        assertTrue(s.car.y > Arena.HEIGHT - 0.3D, "height " + s.car.y);
        assertTrue(s.car.z > -30.0D + 10.0D, "it must have driven forward, z=" + s.car.z);
        assertTrue(s.worstPenetration > -0.05D, "penetration " + s.worstPenetration);
    }

    @Test
    void jumpingFromTheFloorGoesUpAndComesBackOntoTheWheels() {
        Sim s = new Sim(0, 0.02, 0, 0);
        s.run(5);
        s.in.jump = true;
        s.in.jumpHeld = true;
        s.tick();
        assertEquals(CarPhysics.EVENT_JUMP, s.car.event);
        double apex = 0.0D;
        for (int i = 0; i < 80; i++) {
            s.in.jumpHeld = i < 8;
            s.tick();
            apex = Math.max(apex, s.car.y);
        }
        assertTrue(apex > 2.5D, "apex " + apex);
        assertTrue(s.car.onSurface);
        assertEquals(0.0D, s.car.y, 0.06D);
    }

    @Test
    void jumpingFromTheWallPushesTheCarAwayFromIt() {
        Sim s = new Sim(Arena.HALF_WIDTH, 20.0, 0, 0);
        s.car.roll = 90.0F; // wheels on the east wall facing north: the roof points west
        s.run(4);
        assertTrue(s.car.onSurface, "on the wall");
        double x0 = s.car.x;
        s.in.jump = true;
        s.in.jumpHeld = true;
        s.tick();
        assertTrue(s.car.vx < -0.2D, "pushed away from the wall, vx " + s.car.vx);
    }

    @Test
    void theCarCannotLeaveTheArena() {
        // fly at the east wall, the ceiling and the north wall with the boost on
        double[][] starts = {{0, 20, 0, EAST}, {0, 20, 0, 0}, {0, 20, 0, 180}, {0, 20, 0, 90}};
        for (double[] st : starts) {
            Sim s = new Sim(st[0], st[1], st[2], st[3]);
            s.car.pitch = -20.0F;
            s.in.boost = true;
            for (int i = 0; i < 200; i++) {
                s.car.boost = 100.0F;
                s.tick();
            }
            assertTrue(s.worstPenetration > -0.3D, "yaw " + st[3] + ": the car sank " + s.worstPenetration + " into the wall");
            assertTrue(Arena.sd(s.car.x, s.car.y, s.car.z) > 0.0D, "origin outside at yaw " + st[3]);
        }
    }

    @Test
    void aCarThatHitsTheCeilingBouncesOffAndFalls() {
        Sim s = new Sim(0, 30.0, 0, 0);
        s.car.vy = 0.8D;
        s.run(80);
        assertTrue(s.worstPenetration > -0.1D);
        assertTrue(s.car.y < Arena.HEIGHT - 2.0D);
        assertTrue(s.car.y < 29.0D || s.car.onSurface, "it came down, y=" + s.car.y);
    }

    @Test
    void landingOnTheRoofIsACrashThatNeedsTheJumpKey() {
        Sim s = new Sim(0, 6.0, 0, 0);
        s.car.roll = 180.0F;
        s.run(150);
        assertTrue(s.car.crashed, "crashed");
        assertTrue(s.car.stranded, "waiting for the jump key");
        assertTrue(s.roofY() < -0.9D, "no self-righting, roof y " + s.roofY());
        assertTrue(s.worstPenetration > -0.1D, "penetration " + s.worstPenetration);
        // the car rests on its roof: its origin is above the floor by the height of the car
        assertTrue(s.car.y > 0.8D && s.car.y < 1.6D, "origin height " + s.car.y);

        s.in.jump = true;
        s.tick();
        assertEquals(CarPhysics.EVENT_RECOVER, s.car.event);
        s.run(CarPhysics.RECOVERY_TICKS + 3);
        assertFalse(s.car.crashed);
        assertTrue(s.roofY() > 0.99D, "back on its wheels, roof y " + s.roofY());
        s.run(20);
        assertTrue(s.car.onSurface);
        s.in.throttle = 1.0F;
        s.run(30);
        assertTrue(s.speed() > 0.5D, "it drives again");
    }

    @Test
    void theCornerWallsAreDrivable() {
        // drive diagonally into the corner at (+x, +z)
        Sim s = new Sim(40, 0.02, 60, -45.0D);
        double[] f = CarPhysics.forward(s.car.yaw, 0.0F);
        assertTrue(f[0] > 0.5D && f[2] > 0.5D, "heading into the corner " + java.util.Arrays.toString(f));
        s.in.throttle = 1.0F;
        s.run(150);
        assertTrue(s.worstPenetration > -0.1D, "penetration " + s.worstPenetration);
        assertTrue(Arena.sd(s.car.x, s.car.y, s.car.z) > 0.0D);
    }

    @Test
    void aFlipInTheArenaTurnsTheCarAllTheWayAndItLandsOnItsWheels() {
        Sim s = new Sim(0, 0.02, -90, 0);
        s.in.throttle = 1.0F;
        s.run(40);
        s.in.throttle = 0.0F;
        s.in.jump = true;
        s.in.jumpHeld = true;
        s.tick();
        s.run(8); // the hold of the jump
        s.in.jumpHeld = false;
        s.run(4);
        double speedBefore = s.speed();
        s.in.jump = true;
        s.in.pitch = 1.0F; // forward flip
        s.tick();
        s.in.pitch = 0.0F;
        assertEquals(CarPhysics.FLIP_FRONT, s.car.flip);
        double lowestRoof = 1.0D;
        for (int i = 0; i < 20; i++) {
            s.tick();
            lowestRoof = Math.min(lowestRoof, s.roofY());
        }
        assertTrue(lowestRoof < -0.8D, "the car turned upside down during the flip, roof y " + lowestRoof);
        assertTrue(s.speed() > speedBefore + 0.2D, "the flip gives speed: " + speedBefore + " -> " + s.speed());
        s.run(80);
        assertTrue(s.car.onSurface, "back on the floor");
        assertFalse(s.car.crashed);
        assertTrue(s.roofY() > 0.99D, "wheels down again, roof y " + s.roofY());
        assertTrue(s.worstPenetration > -0.15D, "penetration " + s.worstPenetration);
    }

    @Test
    void aWaveDashOnTheArenaFloorGivesTheBurstOfSpeed() {
        // hop, dodge forward just before landing: the landing cancels the flip and keeps the speed
        double[] speed = new double[2];
        for (int dodge = 0; dodge < 2; dodge++) {
            Sim s = new Sim(0, 0.02, -60, 0);
            s.in.throttle = 1.0F;
            s.run(40);
            s.in.throttle = 0.0F;
            s.in.jump = true;
            s.tick(); // a tap: a short hop
            s.in.jumpHeld = false;
            s.run(6);
            if (dodge == 1) {
                s.in.jump = true;
                s.in.pitch = 1.0F;
                s.tick();
                s.in.pitch = 0.0F;
            }
            int guard = 0;
            while (!s.car.onSurface && guard++ < 60) {
                s.tick();
            }
            s.run(2);
            speed[dodge] = s.speed();
        }
        assertTrue(speed[1] > speed[0] + 0.25D, "speed with the dodge " + speed[1] + ", without " + speed[0]);
    }
}
