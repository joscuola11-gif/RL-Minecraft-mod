package com.rlfennec.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArenaTest {
    @Test
    void theFloorTheWallsAndTheCeilingAreAtDistanceZero() {
        assertEquals(0.0D, Arena.sd(0, 0, 0), 1e-9D);
        assertEquals(0.0D, Arena.sd(30, 0, -40), 1e-9D);
        assertEquals(0.0D, Arena.sd(Arena.HALF_WIDTH, 20, 0), 1e-9D);
        assertEquals(0.0D, Arena.sd(-Arena.HALF_WIDTH, 20, 10), 1e-9D);
        assertEquals(0.0D, Arena.sd(10, 20, Arena.HALF_LENGTH), 1e-9D);
        assertEquals(0.0D, Arena.sd(10, 20, -Arena.HALF_LENGTH), 1e-9D);
        assertEquals(0.0D, Arena.sd(0, Arena.HEIGHT, 0), 1e-9D);
    }

    @Test
    void distanceGrowsInsideAndIsNegativeInTheWall() {
        assertEquals(5.0D, Arena.sd(0, 5, 0), 1e-9D);
        assertEquals(5.0D, Arena.sd(Arena.HALF_WIDTH - 5, 20, 0), 1e-9D);
        assertEquals(3.0D, Arena.sd(0, Arena.HEIGHT - 3, 0), 1e-9D);
        assertTrue(Arena.sd(0, -1, 0) < 0.0D);
        assertTrue(Arena.sd(Arena.HALF_WIDTH + 1, 20, 0) < 0.0D);
        assertTrue(Arena.sd(0, Arena.HEIGHT + 1, 0) < 0.0D);
        assertTrue(Arena.contains(0, 10, 0));
        assertFalse(Arena.contains(0, -0.1, 0));
    }

    @Test
    void theFloorCurvesIntoTheWallAlongAQuarterCircle() {
        // the ramp: centre at (CORE_X, RADIUS), radius RADIUS
        for (int deg = 0; deg <= 90; deg += 15) {
            double a = Math.toRadians(deg);
            double x = Arena.CORE_X + Arena.RADIUS * Math.sin(a);
            double y = Arena.RADIUS - Arena.RADIUS * Math.cos(a);
            assertEquals(0.0D, Arena.sd(x, y, 0.0D), 1e-9D, "ramp point at " + deg + " degrees");
            double[] n = new double[3];
            Arena.normal(x, y, 0.0D, n);
            assertEquals(-Math.sin(a), n[0], 1e-3D);
            assertEquals(Math.cos(a), n[1], 1e-3D);
            assertEquals(0.0D, n[2], 1e-3D);
        }
    }

    @Test
    void theNormalsPointIntoTheArenaOnEverySurface() {
        double[] n = new double[3];
        Arena.normal(0, 0, 0, n);
        assertEquals(1.0D, n[1], 1e-6D);
        Arena.normal(Arena.HALF_WIDTH, 20, 0, n);
        assertEquals(-1.0D, n[0], 1e-6D);
        Arena.normal(0, 20, -Arena.HALF_LENGTH, n);
        assertEquals(1.0D, n[2], 1e-6D);
        Arena.normal(0, Arena.HEIGHT, 0, n);
        assertEquals(-1.0D, n[1], 1e-6D);
        // the 45 degree corner wall
        double sum = Arena.HALF_WIDTH + Arena.HALF_LENGTH - Arena.CORNER_CUT; // x + z = sum on the corner wall
        Arena.normal(sum / 2.0D - 10.0D, 20, sum / 2.0D + 10.0D, n);
        assertEquals(-Math.sqrt(0.5D), n[0], 1e-3D);
        assertEquals(-Math.sqrt(0.5D), n[2], 1e-3D);
    }

    @Test
    void theCornersAreRoundedWithTheSameRadius() {
        // the vertical edge where a corner wall meets the side wall: a cylinder of radius RADIUS around the core vertex
        double vx = Arena.CORE_X;
        double vz = Arena.CORE_DIAGONAL - Arena.CORE_X;
        for (int deg = 0; deg <= 45; deg += 15) {
            double a = Math.toRadians(deg);
            double x = vx + Arena.RADIUS * Math.cos(a);
            double z = vz + Arena.RADIUS * Math.sin(a);
            assertEquals(0.0D, Arena.sd(x, 20, z), 1e-9D);
        }
    }

    @Test
    void theGradientHasLengthOneAwayFromTheMiddle() {
        double e = 1e-4D;
        double[][] points = {{10, 3, 20}, {70, 6, 40}, {78, 15, 90}, {30, 39, 50}, {60, 30, 95}};
        for (double[] p : points) {
            double gx = (Arena.sd(p[0] + e, p[1], p[2]) - Arena.sd(p[0] - e, p[1], p[2])) / (2 * e);
            double gy = (Arena.sd(p[0], p[1] + e, p[2]) - Arena.sd(p[0], p[1] - e, p[2])) / (2 * e);
            double gz = (Arena.sd(p[0], p[1], p[2] + e) - Arena.sd(p[0], p[1], p[2] - e)) / (2 * e);
            assertEquals(1.0D, Math.sqrt(gx * gx + gy * gy + gz * gz), 1e-3D, "at " + java.util.Arrays.toString(p));
        }
    }

    @Test
    void thePadsAreSixBigAndTwentyEightSmallAllOnTheFlatFloor() {
        assertEquals(6, Arena.BIG_PADS.length);
        assertEquals(28, Arena.SMALL_PADS.length);
        for (double[][] group : new double[][][] {Arena.BIG_PADS, Arena.SMALL_PADS}) {
            for (double[] pad : group) {
                assertEquals(0.0D, Arena.sd(pad[0], 0, pad[1]), 1e-9D, "pad on the flat floor " + pad[0] + ", " + pad[1]);
                assertTrue(Arena.coreSd2D(pad[0], pad[1]) < 0.0D, "inside the flat part of the floor");
            }
        }
        // the layout is symmetrical
        for (double[] pad : Arena.SMALL_PADS) {
            boolean found = false;
            for (double[] other : Arena.SMALL_PADS) {
                found |= Math.abs(other[0] + pad[0]) < 1e-6D && Math.abs(other[1] + pad[1]) < 1e-6D;
            }
            assertTrue(found || (pad[0] == 0.0D && false), "mirror of " + pad[0] + ", " + pad[1]);
        }
    }

    @Test
    void kickoffSpotsAreOnTheFloor() {
        for (double[] k : Arena.KICKOFFS) {
            assertTrue(Arena.sd(k[0], 0.0D, k[1]) >= 0.0D);
            assertTrue(Arena.coreSd2D(k[0], k[1]) < 0.0D);
        }
    }
}
