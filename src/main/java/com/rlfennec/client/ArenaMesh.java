package com.rlfennec.client;

import com.rlfennec.physics.Arena;
import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of the arena: the real curved shell, built from the same description the physics uses
 * ({@link Arena}). Pure data (no Minecraft classes), so it can be tested: every vertex of the shell lies on the surface that
 * the car drives on.
 *
 * <p>The shell is a swept profile: around the octagon of the core, each section has a quarter circle that rises from the
 * floor into the wall (the ramp), the wall, and a quarter circle into the ceiling. Around the eight corners of the
 * octagon the same profile is turned (a vertical cylinder with spherical ends), so every edge is a real curve.</p>
 *
 * <p>Vertices are 11 floats: x y z, normal (pointing into the pitch), u v, and a colour r g b.</p>
 */
public final class ArenaMesh {
    public static final int FLOATS_PER_VERTEX = 11;
    private static final int ARC_STEPS = 12;
    private static final int CORNER_STEPS = 6;
    private static final int EDGE_STEPS = 8;

    /** Half the thickness of the glowing lines on the walls, ... */
    private static final double GOAL_HALF_WIDTH = 17.9D;
    private static final double GOAL_HEIGHT = 12.9D;

    private static final float[] BLUE = {0.12F, 0.45F, 1.00F};
    private static final float[] ORANGE = {1.00F, 0.45F, 0.08F};
    private static final float[] WHITE = {0.85F, 0.90F, 1.00F};
    private static final float[] SHELL = {0.11F, 0.15F, 0.30F};
    private static final float[] RAMP = {0.15F, 0.19F, 0.33F};
    private static final float[] CEILING = {0.10F, 0.12F, 0.24F};

    /** A mesh: triangles of 11-float vertices. */
    public static final class Mesh {
        public float[] data = new float[FLOATS_PER_VERTEX * 1024];
        public int vertexCount;
        /** The arena has no sun: the shade of a surface is baked into its colour (a little darker the more it faces down). */
        private final boolean shaded;

        Mesh(boolean shaded) {
            this.shaded = shaded;
        }

        void vertex(double x, double y, double z, double nx, double ny, double nz, double u, double v, float[] c) {
            int need = (vertexCount + 1) * FLOATS_PER_VERTEX;
            if (need > data.length) {
                data = java.util.Arrays.copyOf(data, data.length * 2);
            }
            int o = vertexCount * FLOATS_PER_VERTEX;
            data[o] = (float) x;
            data[o + 1] = (float) y;
            data[o + 2] = (float) z;
            data[o + 3] = (float) nx;
            data[o + 4] = (float) ny;
            data[o + 5] = (float) nz;
            data[o + 6] = (float) u;
            data[o + 7] = (float) v;
            float shade = 1.0F;
            if (shaded) {
                double lx = 0.15D;
                double ly = 1.0D;
                double lz = 0.25D;
                double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
                double facing = Math.max(0.0D, (nx * lx + ny * ly + nz * lz) / len);
                shade = (float) (0.66D + 0.34D * facing);
            }
            data[o + 8] = c[0] * shade;
            data[o + 9] = c[1] * shade;
            data[o + 10] = c[2] * shade;
            vertexCount++;
        }
    }

    /** One point of the cross section: horizontal offset from the core edge, height, and the normal as (h along the outward direction, v up). */
    private record Prof(double offset, double y, double h, double v) {
    }

    /** One point of the ring around the core: a point of the core outline and the outward direction there. */
    private record Sample(double x, double z, double nx, double nz) {
    }

    public final Mesh shell = new Mesh(true);
    public final Mesh glow = new Mesh(false);
    public final Mesh floor = new Mesh(false);

    public static ArenaMesh build() {
        ArenaMesh m = new ArenaMesh();
        m.buildShell();
        m.buildFloor();
        m.buildCeilingAndGoals();
        return m;
    }

    // ------------------------------------------------------------------ ring and profile

    private static List<Sample> ring() {
        double c = Arena.CORE_DIAGONAL / Math.sqrt(2.0D);
        double[] d = {Arena.CORE_X, c, Arena.CORE_Z, c, Arena.CORE_X, c, Arena.CORE_Z, c};
        double[][] v = new double[8][];
        for (int k = 0; k < 8; k++) {
            int j = (k + 1) % 8;
            double ax = Math.cos(Math.toRadians(45.0D * k));
            double az = Math.sin(Math.toRadians(45.0D * k));
            double bx = Math.cos(Math.toRadians(45.0D * j));
            double bz = Math.sin(Math.toRadians(45.0D * j));
            double det = ax * bz - az * bx;
            v[k] = new double[] {(d[k] * bz - d[j] * az) / det, (ax * d[j] - bx * d[k]) / det};
        }
        List<Sample> out = new ArrayList<>();
        for (int k = 0; k < 8; k++) {
            int prev = (k + 7) % 8;
            // the corner at the end of the previous edge: the outward direction turns from edge prev to edge k
            for (int j = 0; j <= CORNER_STEPS; j++) {
                double a = Math.toRadians(45.0D * prev + 45.0D * j / CORNER_STEPS);
                out.add(new Sample(v[prev][0], v[prev][1], Math.cos(a), Math.sin(a)));
            }
            // then along edge k
            double nx = Math.cos(Math.toRadians(45.0D * k));
            double nz = Math.sin(Math.toRadians(45.0D * k));
            for (int m = 1; m < EDGE_STEPS; m++) {
                double t = m / (double) EDGE_STEPS;
                out.add(new Sample(v[prev][0] + (v[k][0] - v[prev][0]) * t, v[prev][1] + (v[k][1] - v[prev][1]) * t, nx, nz));
            }
        }
        return out;
    }

    private static List<Prof> profile() {
        double r = Arena.RADIUS;
        double h = Arena.HEIGHT;
        List<Prof> p = new ArrayList<>();
        for (int i = 0; i <= ARC_STEPS; i++) {
            double phi = Math.PI / 2.0D * i / ARC_STEPS;
            p.add(new Prof(r * Math.sin(phi), r - r * Math.cos(phi), -Math.sin(phi), Math.cos(phi)));
        }
        double[] wall = {r + 0.5D, h / 2.0D - 0.15D, h / 2.0D + 0.15D, h - r - 0.5D, h - r};
        for (double y : wall) {
            p.add(new Prof(r, y, -1.0D, 0.0D));
        }
        for (int i = 1; i <= ARC_STEPS; i++) {
            double psi = Math.PI / 2.0D * i / ARC_STEPS;
            p.add(new Prof(r * Math.cos(psi), h - r + r * Math.sin(psi), -Math.cos(psi), -Math.sin(psi)));
        }
        return p;
    }

    /** The kind of a strip of the profile (between point j and j + 1). */
    private enum Strip { RAMP_EDGE, RAMP, WALL, WALL_LINE, WALL_LINE_THIN, CEILING_EDGE }

    private static Strip stripKind(int j) {
        if (j < ARC_STEPS) {
            return j == 0 ? Strip.RAMP_EDGE : Strip.RAMP;
        }
        int w = j - ARC_STEPS; // 0..4 on the wall: line, wall, thin line, wall, line (points R, R+.5, H/2-.15, H/2+.15, H-R-.5, H-R)
        return switch (w) {
            case 0, 4 -> Strip.WALL_LINE;
            case 2 -> Strip.WALL_LINE_THIN;
            case 1, 3 -> Strip.WALL;
            default -> Strip.CEILING_EDGE;
        };
    }

    // ------------------------------------------------------------------ colours

    /** Blue at the -z end, white in the middle, orange at the +z end. */
    private static float[] team(double z, float scale) {
        double t = Math.max(-1.0D, Math.min(1.0D, z / 30.0D));
        float[] a = t < 0 ? BLUE : ORANGE;
        double k = Math.abs(t);
        return new float[] {
                (float) ((WHITE[0] + (a[0] - WHITE[0]) * k) * scale),
                (float) ((WHITE[1] + (a[1] - WHITE[1]) * k) * scale),
                (float) ((WHITE[2] + (a[2] - WHITE[2]) * k) * scale)};
    }

    private static float[] tinted(float[] base, double z, float amount) {
        float[] t = team(z, amount);
        return new float[] {Math.min(1.0F, base[0] + t[0] * 0.10F), Math.min(1.0F, base[1] + t[1] * 0.10F), Math.min(1.0F, base[2] + t[2] * 0.10F)};
    }

    // ------------------------------------------------------------------ shell

    private void buildShell() {
        List<Sample> ring = ring();
        List<Prof> prof = profile();
        for (int i = 0; i < ring.size(); i++) {
            Sample a = ring.get(i);
            Sample b = ring.get((i + 1) % ring.size());
            for (int j = 0; j + 1 < prof.size(); j++) {
                Strip kind = stripKind(j);
                boolean line = kind == Strip.WALL_LINE || kind == Strip.WALL_LINE_THIN || kind == Strip.RAMP_EDGE;
                Prof p0 = prof.get(j);
                Prof p1 = prof.get(j + 1);
                if (line) {
                    quad(glow, a, b, p0, p1, 0.04D, c -> team(c, kind == Strip.WALL_LINE_THIN ? 0.8F : 1.0F), null);
                } else {
                    float[] base = kind == Strip.RAMP ? RAMP : kind == Strip.CEILING_EDGE ? CEILING : SHELL;
                    quad(shell, a, b, p0, p1, 0.0D, z -> tinted(base, z, 1.0F), null);
                }
                if (kind == Strip.RAMP_EDGE) {
                    quad(shell, a, b, p0, p1, 0.0D, z -> tinted(RAMP, z, 1.0F), null);
                }
            }
        }
    }

    private interface ColorAt {
        float[] at(double z);
    }

    private static double px(Sample s, Prof p) {
        return s.x() + s.nx() * p.offset();
    }

    private static double pz(Sample s, Prof p) {
        return s.z() + s.nz() * p.offset();
    }

    private static void quad(Mesh mesh, Sample a, Sample b, Prof p0, Prof p1, double inset, ColorAt color, double[] unused) {
        double[][] pos = new double[4][];
        double[][] nor = new double[4][];
        double[] zs = new double[4];
        Sample[] ss = {a, b, b, a};
        Prof[] ps = {p0, p0, p1, p1};
        for (int i = 0; i < 4; i++) {
            Sample s = ss[i];
            Prof p = ps[i];
            double nx = s.nx() * p.h();
            double ny = p.v();
            double nz = s.nz() * p.h();
            pos[i] = new double[] {px(s, p) + nx * inset, p.y() + ny * inset, pz(s, p) + nz * inset};
            nor[i] = new double[] {nx, ny, nz};
            zs[i] = pos[i][2];
        }
        int[] order = {0, 1, 2, 0, 2, 3};
        for (int k : order) {
            mesh.vertex(pos[k][0], pos[k][1], pos[k][2], nor[k][0], nor[k][1], nor[k][2], 0.0D, 0.0D, color.at(zs[k]));
        }
    }

    // ------------------------------------------------------------------ floor, ceiling, goals

    private static List<double[]> coreOutline() {
        List<double[]> out = new ArrayList<>();
        List<Sample> ring = ring();
        // one point per corner (the first sample of every corner is on the core vertex)
        for (int k = 0; k < 8; k++) {
            Sample s = ring.get(k * (CORNER_STEPS + 1 + EDGE_STEPS - 1));
            out.add(new double[] {s.x(), s.z()});
        }
        return out;
    }

    private void buildFloor() {
        List<double[]> outline = coreOutline();
        double y = 0.02D;
        for (int i = 0; i < outline.size(); i++) {
            double[] a = outline.get(i);
            double[] b = outline.get((i + 1) % outline.size());
            double[][] tri = {{0.0D, 0.0D}, a, b};
            for (double[] p : tri) {
                floor.vertex(p[0], y, p[1], 0.0D, 1.0D, 0.0D, (p[0] + Arena.CORE_X) / (2.0D * Arena.CORE_X), (p[1] + Arena.CORE_Z) / (2.0D * Arena.CORE_Z), new float[] {1.0F, 1.0F, 1.0F});
            }
        }
    }

    private void buildCeilingAndGoals() {
        List<double[]> outline = coreOutline();
        double y = Arena.HEIGHT - 0.01D;
        for (int i = 0; i < outline.size(); i++) {
            double[] a = outline.get(i);
            double[] b = outline.get((i + 1) % outline.size());
            double[][] tri = {{0.0D, 0.0D}, b, a};
            for (double[] p : tri) {
                shell.vertex(p[0], y, p[1], 0.0D, -1.0D, 0.0D, 0.0D, 0.0D, CEILING);
            }
        }
        // light panels in the ceiling
        float[] light = {1.00F, 0.93F, 0.78F};
        for (double px : new double[] {-44.0D, 0.0D, 44.0D}) {
            for (double pz : new double[] {-66.0D, -22.0D, 22.0D, 66.0D}) {
                flatQuad(glow, px - 9.0D, px + 9.0D, pz - 5.0D, pz + 5.0D, Arena.HEIGHT - 0.04D, light);
            }
        }
        // goal frames painted on the two back walls (the goals themselves are not part of the arena)
        double wall = Arena.HALF_LENGTH - 0.05D;
        double y0 = Arena.RADIUS + 0.4D;
        for (int end = -1; end <= 1; end += 2) {
            float[] color = end < 0 ? BLUE : ORANGE;
            double z = end * wall;
            double nz = -end;
            wallQuad(shell, -GOAL_HALF_WIDTH, GOAL_HALF_WIDTH, y0, y0 + GOAL_HEIGHT, z - end * 0.01D, nz, new float[] {0.005F, 0.01F, 0.02F});
            wallQuad(glow, -GOAL_HALF_WIDTH - 0.7D, GOAL_HALF_WIDTH + 0.7D, y0 + GOAL_HEIGHT, y0 + GOAL_HEIGHT + 0.7D, z, nz, color);
            wallQuad(glow, -GOAL_HALF_WIDTH - 0.7D, -GOAL_HALF_WIDTH, y0 - 0.0D, y0 + GOAL_HEIGHT + 0.7D, z, nz, color);
            wallQuad(glow, GOAL_HALF_WIDTH, GOAL_HALF_WIDTH + 0.7D, y0 - 0.0D, y0 + GOAL_HEIGHT + 0.7D, z, nz, color);
            wallQuad(glow, -GOAL_HALF_WIDTH - 0.7D, GOAL_HALF_WIDTH + 0.7D, y0 - 0.7D, y0, z, nz, color);
        }
    }

    private static void flatQuad(Mesh mesh, double x0, double x1, double z0, double z1, double y, float[] c) {
        double[][] p = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z0}, {x1, z1}, {x0, z1}};
        for (double[] q : p) {
            mesh.vertex(q[0], y, q[1], 0.0D, -1.0D, 0.0D, 0.0D, 0.0D, c);
        }
    }

    private static void wallQuad(Mesh mesh, double x0, double x1, double y0, double y1, double z, double nz, float[] c) {
        double[][] p = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y0}, {x1, y1}, {x0, y1}};
        for (double[] q : p) {
            mesh.vertex(q[0], q[1], z, 0.0D, 0.0D, nz, 0.0D, 0.0D, c);
        }
    }
}
