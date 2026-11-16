package com.rlfennec.physics;

/**
 * The shape of the arena, as pure maths (no Minecraft classes), in arena coordinates: the origin is the middle of the
 * pitch on the floor, +x is east, +y is up and +z is south (the long axis, goal to goal).
 *
 * <p>The pitch is modelled on Rocket League's standard arena (8192 x 10240 x 2044 uu with 1152 uu cut corners): one
 * block is 50 uu. Its inside is a <em>rounded</em> prism: an octagon in plan (a rectangle with big 45 degree cut
 * corners) between a floor and a ceiling, with every edge and corner rounded by a real curve of radius
 * {@link #RADIUS}. So the floor curves up into the walls (the ramps), the walls curve into the ceiling and the corner
 * walls curve into the side walls, and the car can drive on all of it.</p>
 *
 * <p>The shape is the set of points that are at most {@link #RADIUS} away from the "core": a smaller octagonal prism.
 * That makes a signed distance exact and cheap: {@link #sd} is the distance to the nearest surface (positive inside,
 * negative in the wall).</p>
 */
public final class Arena {
    /** Half the width (x) of the pitch at the walls, in blocks. */
    public static final double HALF_WIDTH = 82.0D;
    /** Half the length (z) of the pitch at the back walls, in blocks. */
    public static final double HALF_LENGTH = 102.0D;
    /** Height of the ceiling above the floor, in blocks. */
    public static final double HEIGHT = 41.0D;
    /** How far the 45 degree corner walls cut into the rectangle along each side, in blocks (1152 uu). */
    public static final double CORNER_CUT = 23.0D;
    /** Radius of every curve (floor to wall ramps, wall to ceiling, corners), in blocks. */
    public static final double RADIUS = 8.0D;

    // the core: the pitch shrunk by RADIUS, |x| <= CORE_X, |z| <= CORE_Z, |x| + |z| <= CORE_DIAGONAL, RADIUS <= y <= HEIGHT - RADIUS
    public static final double CORE_X = HALF_WIDTH - RADIUS;
    public static final double CORE_Z = HALF_LENGTH - RADIUS;
    public static final double CORE_DIAGONAL = (HALF_WIDTH + HALF_LENGTH - CORNER_CUT) - RADIUS * Math.sqrt(2.0D);

    /** Pickup radius and height of the pads (208 uu and 144 uu, 168 uu tall), in blocks. */
    public static final double BIG_PAD_RADIUS = 4.2D;
    public static final double SMALL_PAD_RADIUS = 2.9D;
    public static final double PAD_HEIGHT = 3.4D;
    public static final float BIG_PAD_BOOST = 100.0F;
    public static final float SMALL_PAD_BOOST = 12.0F;
    public static final int BIG_PAD_RESPAWN_TICKS = 200;
    public static final int SMALL_PAD_RESPAWN_TICKS = 80;

    /** The six big pads: x, z in blocks (RL: (+-3584, 0) and (+-3072, +-4096) uu). */
    public static final double[][] BIG_PADS = {
            {-71.68, 0.0}, {71.68, 0.0},
            {-61.44, -81.92}, {61.44, -81.92}, {-61.44, 81.92}, {61.44, 81.92}
    };

    /** The 28 small pads: x, z in blocks (the standard layout, uu / 50). */
    public static final double[][] SMALL_PADS = {
            {0.0, -84.8}, {-35.84, -83.68}, {35.84, -83.68}, {-18.8, -66.16}, {18.8, -66.16}, {0.0, -56.32},
            {-71.68, -49.68}, {71.68, -49.68}, {-35.76, -46.0}, {35.76, -46.0}, {-40.96, -20.72}, {0.0, -20.48},
            {40.96, -20.72}, {-20.48, 0.0}, {20.48, 0.0}, {-40.96, 20.72}, {0.0, 20.48}, {40.96, 20.72},
            {-35.76, 46.0}, {35.76, 46.0}, {-71.68, 49.68}, {71.68, 49.68}, {0.0, 56.32}, {-18.8, 66.16},
            {18.8, 66.16}, {-35.84, 83.68}, {35.84, 83.68}, {0.0, 84.8}
    };

    /** Kickoff spots: x, z, yaw (degrees, 0 = facing +z). The first one is where a player who enters the arena starts. */
    public static final double[][] KICKOFFS = {
            {-40.96, -51.2, -38.66}, {40.96, -51.2, 38.66}, {-5.12, -76.8, -3.8}, {5.12, -76.8, 3.8}, {0.0, -92.16, 0.0}
    };

    private Arena() {
    }

    /**
     * Distance to the nearest surface of the arena: positive inside the pitch (the free space), zero on the surface,
     * negative inside the wall.
     */
    public static double sd(double x, double y, double z) {
        double d2 = coreSd2D(x, z);                               // octagon in plan
        double dy = Math.abs(y - HEIGHT / 2.0D) - (HEIGHT / 2.0D - RADIUS); // between the floor and the ceiling cores
        double ox = Math.max(d2, 0.0D);
        double oy = Math.max(dy, 0.0D);
        double distanceToCore = Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(d2, dy), 0.0D);
        return RADIUS - distanceToCore;
    }

    /** The unit normal of the surface nearest to the point, pointing into the pitch (the way the sd grows). */
    public static void normal(double x, double y, double z, double[] out) {
        double e = 0.01D;
        double nx = sd(x + e, y, z) - sd(x - e, y, z);
        double ny = sd(x, y + e, z) - sd(x, y - e, z);
        double nz = sd(x, y, z + e) - sd(x, y, z - e);
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-9D) {
            out[0] = 0.0D;
            out[1] = 1.0D;
            out[2] = 0.0D;
            return;
        }
        out[0] = nx / len;
        out[1] = ny / len;
        out[2] = nz / len;
    }

    /** Signed distance to the octagon of the core, in plan (negative inside). */
    static double coreSd2D(double x, double z) {
        double ax = Math.abs(x);
        double az = Math.abs(z);
        double inside = Math.max(Math.max(ax - CORE_X, az - CORE_Z), (ax + az - CORE_DIAGONAL) * Math.sqrt(0.5D));
        if (inside <= 0.0D) {
            return inside;
        }
        // outside: the distance to the nearest point of the octagon (vertices of the first quadrant, mirrored)
        double dz0 = CORE_DIAGONAL - CORE_X;   // the corner cut meets x = CORE_X at z = dz0
        double dx0 = CORE_DIAGONAL - CORE_Z;   // and z = CORE_Z at x = dx0
        double best = Double.MAX_VALUE;
        best = Math.min(best, segmentDistance(ax, az, 0.0D, CORE_Z, dx0, CORE_Z));
        best = Math.min(best, segmentDistance(ax, az, dx0, CORE_Z, CORE_X, dz0));
        best = Math.min(best, segmentDistance(ax, az, CORE_X, dz0, CORE_X, 0.0D));
        return best;
    }

    private static double segmentDistance(double px, double pz, double ax, double az, double bx, double bz) {
        double abx = bx - ax;
        double abz = bz - az;
        double t = ((px - ax) * abx + (pz - az) * abz) / (abx * abx + abz * abz);
        t = Math.max(0.0D, Math.min(1.0D, t));
        double dx = px - (ax + abx * t);
        double dz = pz - (az + abz * t);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Whether a point is inside the pitch (not in the wall). */
    public static boolean contains(double x, double y, double z) {
        return sd(x, y, z) >= 0.0D;
    }
}
