package com.rlfennec.client;

import java.util.Arrays;

/**
 * The shapes of the boost pads, rebuilt from the Rocket League pads: a flat silver plate shaped like a rounded
 * triangle (three lobes) with a black rim, arrow marks, a round socket with glowing orange rings, three glowing fins at
 * the corners with flames over them, a tall amber column of light and, for the big pad, the golden orb that floats
 * above it. The small pad is the same idea made smaller: a rounded hexagon with one glowing ring and a short column.
 *
 * <p>Pure geometry (no Minecraft classes). Everything is built around the middle of the pad at floor level.</p>
 */
final class BoostPadGeometry {
    static final int FLOATS_PER_VERTEX = 10; // x y z, normal, r g b a

    static final float[] SILVER = {0.80F, 0.82F, 0.87F};
    static final float[] STEEL = {0.36F, 0.38F, 0.44F};
    static final float[] BLACK = {0.045F, 0.045F, 0.06F};
    static final float[] ORANGE = {1.00F, 0.52F, 0.06F};
    static final float[] AMBER = {1.00F, 0.68F, 0.14F};
    static final float[] YELLOW = {1.00F, 0.90F, 0.45F};
    static final float[] GOLD = {1.00F, 0.74F, 0.14F};

    static final double BIG_ORB_HEIGHT = 4.6D;
    static final double ORB_RADIUS = 0.85D;

    /** A triangle mesh with a normal and an RGBA colour per vertex. */
    static final class Mesh {
        float[] data = new float[FLOATS_PER_VERTEX * 1024];
        int vertexCount;

        void vertex(double x, double y, double z, double nx, double ny, double nz, float[] c, float a) {
            if ((vertexCount + 1) * FLOATS_PER_VERTEX > data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            int o = vertexCount * FLOATS_PER_VERTEX;
            data[o] = (float) x;
            data[o + 1] = (float) y;
            data[o + 2] = (float) z;
            data[o + 3] = (float) nx;
            data[o + 4] = (float) ny;
            data[o + 5] = (float) nz;
            data[o + 6] = c[0];
            data[o + 7] = c[1];
            data[o + 8] = c[2];
            data[o + 9] = a;
            vertexCount++;
        }

        /** A flat triangle (the normal comes from its corners, flipped to point away from {@code away}). */
        void flat(double[] a, double[] b, double[] c, float[] color, float alpha) {
            double ux = b[0] - a[0];
            double uy = b[1] - a[1];
            double uz = b[2] - a[2];
            double vx = c[0] - a[0];
            double vy = c[1] - a[1];
            double vz = c[2] - a[2];
            double nx = uy * vz - uz * vy;
            double ny = uz * vx - ux * vz;
            double nz = ux * vy - uy * vx;
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0E-12D) {
                return;
            }
            nx /= len;
            ny /= len;
            nz /= len;
            vertex(a[0], a[1], a[2], nx, ny, nz, color, alpha);
            vertex(b[0], b[1], b[2], nx, ny, nz, color, alpha);
            vertex(c[0], c[1], c[2], nx, ny, nz, color, alpha);
        }
    }

    /** The five parts of a pad, drawn with different materials. */
    static final class Pad {
        final Mesh solid = new Mesh();   // lit: plate, rim, ring
        final Mesh glow = new Mesh();    // emissive: the orange rings and fins
        final Mesh beam = new Mesh();    // translucent columns of light
        final Mesh orb = new Mesh();     // emissive golden sphere (big pads)
        final Mesh halo = new Mesh();    // translucent shell around the orb
    }

    private BoostPadGeometry() {
    }

    // ------------------------------------------------------------------ shapes

    /** The outline r(theta) = mid + amp * cos(lobes * (theta - phase)): a rounded triangle for 3 lobes, a rounded hexagon for 6. */
    static double[][] outline(double mid, double amp, int lobes, double phase, int points, double scale) {
        double[] xs = new double[points];
        double[] zs = new double[points];
        for (int i = 0; i < points; i++) {
            double t = 2.0D * Math.PI * i / points;
            double r = (mid + amp * Math.cos(lobes * (t - phase))) * scale;
            xs[i] = r * Math.cos(t);
            zs[i] = r * Math.sin(t);
        }
        return new double[][] {xs, zs};
    }

    static void polygon(Mesh m, double[] xs, double[] zs, double y, double ny, float[] c) {
        double cx = 0.0D;
        double cz = 0.0D;
        for (int i = 0; i < xs.length; i++) {
            cx += xs[i] / xs.length;
            cz += zs[i] / xs.length;
        }
        for (int i = 0; i < xs.length; i++) {
            int j = (i + 1) % xs.length;
            m.vertex(cx, y, cz, 0.0D, ny, 0.0D, c, 1.0F);
            m.vertex(xs[i], y, zs[i], 0.0D, ny, 0.0D, c, 1.0F);
            m.vertex(xs[j], y, zs[j], 0.0D, ny, 0.0D, c, 1.0F);
        }
    }

    /** A prism with a flat top and side walls (smooth round normals when the outline has many points). */
    static void prism(Mesh m, double[] xs, double[] zs, double y0, double y1, float[] top, float[] side) {
        polygon(m, xs, zs, y1, 1.0D, top);
        double cx = 0.0D;
        double cz = 0.0D;
        for (int i = 0; i < xs.length; i++) {
            cx += xs[i] / xs.length;
            cz += zs[i] / xs.length;
        }
        int n = xs.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            // outward normals from the point direction (smooth on rounded outlines)
            double[] ni = outward(xs[i] - cx, zs[i] - cz);
            double[] nj = outward(xs[j] - cx, zs[j] - cz);
            m.vertex(xs[i], y0, zs[i], ni[0], 0.0D, ni[1], side, 1.0F);
            m.vertex(xs[j], y0, zs[j], nj[0], 0.0D, nj[1], side, 1.0F);
            m.vertex(xs[j], y1, zs[j], nj[0], 0.0D, nj[1], side, 1.0F);
            m.vertex(xs[i], y0, zs[i], ni[0], 0.0D, ni[1], side, 1.0F);
            m.vertex(xs[j], y1, zs[j], nj[0], 0.0D, nj[1], side, 1.0F);
            m.vertex(xs[i], y1, zs[i], ni[0], 0.0D, ni[1], side, 1.0F);
        }
    }

    private static double[] outward(double x, double z) {
        double len = Math.sqrt(x * x + z * z);
        return len < 1.0E-9D ? new double[] {1.0D, 0.0D} : new double[] {x / len, z / len};
    }

    static double[][] circle(double r, int points, double cx, double cz) {
        double[] xs = new double[points];
        double[] zs = new double[points];
        for (int i = 0; i < points; i++) {
            double t = 2.0D * Math.PI * i / points;
            xs[i] = cx + r * Math.cos(t);
            zs[i] = cz + r * Math.sin(t);
        }
        return new double[][] {xs, zs};
    }

    /** A rectangle of the given size, rotated by {@code angle} about its centre. */
    static double[][] rect(double cx, double cz, double length, double width, double angle) {
        double[] xs = new double[4];
        double[] zs = new double[4];
        double[][] corners = {{-length / 2, -width / 2}, {length / 2, -width / 2}, {length / 2, width / 2}, {-length / 2, width / 2}};
        for (int i = 0; i < 4; i++) {
            xs[i] = cx + corners[i][0] * Math.cos(angle) - corners[i][1] * Math.sin(angle);
            zs[i] = cz + corners[i][0] * Math.sin(angle) + corners[i][1] * Math.cos(angle);
        }
        return new double[][] {xs, zs};
    }

    /** The side of a cylinder; the alpha fades from {@code aBottom} to {@code aTop}. */
    static void cylinder(Mesh m, double r, double y0, double y1, int segments, float[] c, float aBottom, float aTop) {
        for (int i = 0; i < segments; i++) {
            double t0 = 2.0D * Math.PI * i / segments;
            double t1 = 2.0D * Math.PI * (i + 1) / segments;
            double c0 = Math.cos(t0);
            double s0 = Math.sin(t0);
            double c1 = Math.cos(t1);
            double s1 = Math.sin(t1);
            m.vertex(r * c0, y0, r * s0, c0, 0.0D, s0, c, aBottom);
            m.vertex(r * c1, y0, r * s1, c1, 0.0D, s1, c, aBottom);
            m.vertex(r * c1, y1, r * s1, c1, 0.0D, s1, c, aTop);
            m.vertex(r * c0, y0, r * s0, c0, 0.0D, s0, c, aBottom);
            m.vertex(r * c1, y1, r * s1, c1, 0.0D, s1, c, aTop);
            m.vertex(r * c0, y1, r * s0, c0, 0.0D, s0, c, aTop);
        }
    }

    /** A cylinder column of light standing at (cx, cz). */
    static void column(Mesh m, double cx, double cz, double r, double y0, double y1, int segments, float[] c, float aBottom, float aTop) {
        Mesh tmp = new Mesh();
        cylinder(tmp, r, y0, y1, segments, c, aBottom, aTop);
        for (int i = 0; i < tmp.vertexCount; i++) {
            int o = i * FLOATS_PER_VERTEX;
            m.vertex(tmp.data[o] + cx, tmp.data[o + 1], tmp.data[o + 2] + cz, tmp.data[o + 3], tmp.data[o + 4], tmp.data[o + 5],
                    new float[] {tmp.data[o + 6], tmp.data[o + 7], tmp.data[o + 8]}, tmp.data[o + 9]);
        }
    }

    static void torus(Mesh m, double major, double minor, int segments, int tube, double cy, float[] c) {
        for (int i = 0; i < segments; i++) {
            double a0 = 2.0D * Math.PI * i / segments;
            double a1 = 2.0D * Math.PI * (i + 1) / segments;
            for (int j = 0; j < tube; j++) {
                double b0 = 2.0D * Math.PI * j / tube;
                double b1 = 2.0D * Math.PI * (j + 1) / tube;
                double[][] p = new double[4][];
                double[][] n = new double[4][];
                double[] aa = {a0, a1, a1, a0};
                double[] bb = {b0, b0, b1, b1};
                for (int k = 0; k < 4; k++) {
                    double ca = Math.cos(aa[k]);
                    double sa = Math.sin(aa[k]);
                    double cb = Math.cos(bb[k]);
                    double sb = Math.sin(bb[k]);
                    p[k] = new double[] {(major + minor * cb) * ca, cy + minor * sb, (major + minor * cb) * sa};
                    n[k] = new double[] {cb * ca, sb, cb * sa};
                }
                int[] order = {0, 1, 2, 0, 2, 3};
                for (int k : order) {
                    m.vertex(p[k][0], p[k][1], p[k][2], n[k][0], n[k][1], n[k][2], c, 1.0F);
                }
            }
        }
    }

    /** A sphere around the origin; the colour is a swirl of two golds so that it looks alive when it turns. */
    static void orb(Mesh m, double radius, int lat, int lon, float[] bright, float[] dark, float alpha) {
        for (int i = 0; i < lat; i++) {
            double p0 = Math.PI * i / lat - Math.PI / 2.0D;
            double p1 = Math.PI * (i + 1) / lat - Math.PI / 2.0D;
            for (int j = 0; j < lon; j++) {
                double t0 = 2.0D * Math.PI * j / lon;
                double t1 = 2.0D * Math.PI * (j + 1) / lon;
                double[][] pt = {{p0, t0}, {p0, t1}, {p1, t1}, {p0, t0}, {p1, t1}, {p1, t0}};
                for (double[] q : pt) {
                    double x = Math.cos(q[0]) * Math.cos(q[1]);
                    double y = Math.sin(q[0]);
                    double z = Math.cos(q[0]) * Math.sin(q[1]);
                    double swirl = 0.5D + 0.5D * Math.sin(3.0D * q[1] + 4.0D * q[0]) * Math.cos(2.0D * q[0] + q[1]);
                    float[] col = {
                            (float) (dark[0] + (bright[0] - dark[0]) * swirl),
                            (float) (dark[1] + (bright[1] - dark[1]) * swirl),
                            (float) (dark[2] + (bright[2] - dark[2]) * swirl)};
                    m.vertex(radius * x, radius * y, radius * z, x, y, z, col, alpha);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the pads

    static Pad bigPad() {
        Pad p = new Pad();
        double phase = Math.toRadians(90.0D); // one lobe points along +z
        double[][] rim = outline(2.25D, 0.72D, 3, phase, 72, 1.07D);
        double[][] plate = outline(2.25D, 0.72D, 3, phase, 72, 1.0D);
        prism(p.solid, rim[0], rim[1], 0.0D, 0.12D, BLACK, BLACK);
        prism(p.solid, plate[0], plate[1], 0.12D, 0.30D, SILVER, STEEL);

        // arrow marks between the socket and each lobe
        for (int k = 0; k < 3; k++) {
            double a = phase + k * 2.0D * Math.PI / 3.0D;
            addArrow(p.solid, a, 1.95D, 2.65D, 0.62D, 0.308D);
        }
        // the socket: dark recess, silver ring, glowing rings
        double[][] recess = circle(1.30D, 40, 0.0D, 0.0D);
        polygon(p.solid, recess[0], recess[1], 0.306D, 1.0D, BLACK);
        torus(p.solid, 1.40D, 0.13D, 40, 8, 0.34D, STEEL);
        double[][] disc = circle(1.05D, 40, 0.0D, 0.0D);
        polygon(p.glow, disc[0], disc[1], 0.33D, 1.0D, ORANGE);
        double[][] ring = circle(0.62D, 32, 0.0D, 0.0D);
        polygon(p.glow, ring[0], ring[1], 0.34D, 1.0D, YELLOW);

        // three glowing fins at the corners, with their column of fire
        for (int k = 0; k < 3; k++) {
            double a = phase + k * 2.0D * Math.PI / 3.0D;
            double fx = 2.55D * Math.cos(a);
            double fz = 2.55D * Math.sin(a);
            double[][] frame = rect(fx, fz, 1.25D, 1.0D, a);
            prism(p.solid, frame[0], frame[1], 0.30D, 0.52D, BLACK, STEEL);
            double[][] inner = rect(fx, fz, 0.95D, 0.72D, a);
            polygon(p.glow, inner[0], inner[1], 0.525D, 1.0D, ORANGE);
            column(p.beam, fx, fz, 0.42D, 0.52D, 2.1D, 14, ORANGE, 0.55F, 0.0F);
        }
        // the big column over the socket
        column(p.beam, 0.0D, 0.0D, 1.28D, 0.34D, 3.6D, 40, AMBER, 0.45F, 0.0F);
        column(p.beam, 0.0D, 0.0D, 0.72D, 0.34D, 3.2D, 28, YELLOW, 0.55F, 0.0F);

        // the golden orb of 100 boost
        orb(p.orb, ORB_RADIUS, 14, 24, GOLD, new float[] {0.80F, 0.38F, 0.04F}, 1.0F);
        orb(p.halo, ORB_RADIUS * 1.35D, 12, 20, YELLOW, AMBER, 0.20F);
        return p;
    }

    static Pad smallPad() {
        Pad p = new Pad();
        double[][] rim = outline(1.50D, 0.10D, 6, 0.0D, 60, 1.08D);
        double[][] plate = outline(1.50D, 0.10D, 6, 0.0D, 60, 1.0D);
        prism(p.solid, rim[0], rim[1], 0.0D, 0.10D, BLACK, BLACK);
        prism(p.solid, plate[0], plate[1], 0.10D, 0.24D, SILVER, STEEL);
        double[][] recess = circle(0.82D, 32, 0.0D, 0.0D);
        polygon(p.solid, recess[0], recess[1], 0.246D, 1.0D, BLACK);
        torus(p.solid, 0.88D, 0.08D, 32, 6, 0.27D, STEEL);
        double[][] disc = circle(0.66D, 32, 0.0D, 0.0D);
        polygon(p.glow, disc[0], disc[1], 0.262D, 1.0D, ORANGE);
        double[][] ring = circle(0.34D, 24, 0.0D, 0.0D);
        polygon(p.glow, ring[0], ring[1], 0.268D, 1.0D, YELLOW);
        column(p.beam, 0.0D, 0.0D, 0.70D, 0.27D, 1.9D, 32, AMBER, 0.45F, 0.0F);
        return p;
    }

    /** A flat arrow (a chevron pointing away from the centre) on the plate, in dark steel. */
    private static void addArrow(Mesh m, double angle, double r0, double r1, double halfWidth, double y) {
        double ca = Math.cos(angle);
        double sa = Math.sin(angle);
        // tip, left base, notch, right base in (radial, lateral) coordinates
        double[][] q = {{r1, 0.0D}, {r0, halfWidth}, {r0 + (r1 - r0) * 0.38D, 0.0D}, {r0, -halfWidth}};
        double[][] p = new double[4][];
        for (int i = 0; i < 4; i++) {
            p[i] = new double[] {q[i][0] * ca - q[i][1] * sa, y, q[i][0] * sa + q[i][1] * ca};
        }
        m.flat(p[0], p[1], p[2], STEEL, 1.0F);
        m.flat(p[0], p[2], p[3], STEEL, 1.0F);
        // (the normals of these two triangles may point down: force them up)
        for (int i = m.vertexCount - 6; i < m.vertexCount; i++) {
            int o = i * FLOATS_PER_VERTEX;
            m.data[o + 3] = 0.0F;
            m.data[o + 4] = 1.0F;
            m.data[o + 5] = 0.0F;
        }
    }
}
