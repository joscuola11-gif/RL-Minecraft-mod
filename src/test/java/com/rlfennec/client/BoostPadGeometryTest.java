package com.rlfennec.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoostPadGeometryTest {
    private static double[] bounds(BoostPadGeometry.Mesh m) {
        double[] b = {1e9, 1e9, 1e9, -1e9, -1e9, -1e9};
        for (int i = 0; i < m.vertexCount; i++) {
            int o = i * BoostPadGeometry.FLOATS_PER_VERTEX;
            for (int k = 0; k < 3; k++) {
                b[k] = Math.min(b[k], m.data[o + k]);
                b[k + 3] = Math.max(b[k + 3], m.data[o + k]);
            }
        }
        return b;
    }

    @Test
    void theBigPadHasAPlateThreeFinsAColumnAndAnOrb() {
        BoostPadGeometry.Pad p = BoostPadGeometry.bigPad();
        assertTrue(p.solid.vertexCount > 800 && p.glow.vertexCount > 100 && p.beam.vertexCount > 200);
        assertTrue(p.orb.vertexCount > 1000 && p.halo.vertexCount > 500);
        double[] plate = bounds(p.solid);
        // about 6 blocks across (the lobes), flat on the floor, a few tenths thick
        assertTrue(plate[3] - plate[0] > 5.0D && plate[3] - plate[0] < 7.5D, "width " + (plate[3] - plate[0]));
        assertEquals(0.0D, plate[1], 1e-6D);
        assertTrue(plate[4] < 0.8D, "plate height " + plate[4]);
        double[] beam = bounds(p.beam);
        assertTrue(beam[4] > 3.0D, "the column of light is tall: " + beam[4]);
        double[] orb = bounds(p.orb);
        assertEquals(BoostPadGeometry.ORB_RADIUS, orb[4], 0.02D);
    }

    @Test
    void theBigPadIsATriangle() {
        // three lobes: the outline is farthest from the middle at three angles 120 degrees apart
        double[][] o = BoostPadGeometry.outline(2.25D, 0.72D, 3, Math.toRadians(90.0D), 360, 1.0D);
        int lobes = 0;
        for (int i = 0; i < 360; i++) {
            double r = Math.hypot(o[0][i], o[1][i]);
            double prev = Math.hypot(o[0][(i + 359) % 360], o[1][(i + 359) % 360]);
            double next = Math.hypot(o[0][(i + 1) % 360], o[1][(i + 1) % 360]);
            if (r > prev && r >= next && r > 2.9D) {
                lobes++;
            }
        }
        assertEquals(3, lobes);
    }

    @Test
    void theSmallPadIsSmallerAndHasNoOrb() {
        BoostPadGeometry.Pad small = BoostPadGeometry.smallPad();
        BoostPadGeometry.Pad big = BoostPadGeometry.bigPad();
        assertEquals(0, small.orb.vertexCount);
        assertTrue(small.solid.vertexCount > 300);
        double[] s = bounds(small.solid);
        double[] b = bounds(big.solid);
        assertTrue(s[3] - s[0] < (b[3] - b[0]) * 0.65D);
    }
}
