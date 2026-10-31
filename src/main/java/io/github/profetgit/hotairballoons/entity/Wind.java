package io.github.profetgit.hotairballoons.entity;

/**
 * Wind per height band: each band has its own direction from the world seed, turning slowly over the day, and wind
 * gets stronger with height (gentle low down, nearly twice a balloon's own thrust speed at the top). Server-side only;
 * balloons sync the result to clients.
 */
public final class Wind {
    public static final int BAND = 16;
    public static final double CALM_Y = 64.0;
    public static final double MIN_SPEED = 0.008;
    public static final double MAX_SPEED = 0.18;
    /**
     * A piloted balloon's wind turns toward the balloon's heading by this share of the angle between them: a tailwind
     * stays, a crosswind becomes a quartering tailwind, a headwind a light crosswind against. So most heights help you
     * on your way, and the best one is still worth climbing for.
     */
    public static final double FAVOUR = 0.4;
    /** Height over which wind goes from MIN to MAX. */
    public static final double RAMP = 160.0;
    /** One full turn of a band's direction takes this many ticks (about two days). */
    public static final double TURN_TICKS = 48000.0;

    /**
     * Gusts: each band's time is cut into slots of this many ticks (offset per band), and a slot holds one gust with
     * {@link #GUST_CHANCE}, lasting {@link #GUST_MIN}..{@link #GUST_MAX} ticks and multiplying that band's speed by
     * 1 + 1..2 at its peak (eased in over {@link #GUST_RISE}, out over {@link #GUST_FALL}). Bands gust on their own, so
     * the air a band up or down may be gusting while yours is calm.
     */
    public static final int GUST_SLOT = 600;
    public static final double GUST_CHANCE = 0.5;
    public static final int GUST_MIN = 100, GUST_MAX = 220, GUST_RISE = 40, GUST_FALL = 60;
    public static final double GUST_PEAK_MIN = 1.0, GUST_PEAK_MAX = 2.0;

    private Wind() {}

    /** Speed multiplier of a band's gust at this time: 1 between gusts, up to 1 + {@link #GUST_PEAK_MAX}. */
    public static double gust(long seed, int band, long gameTime) {
        long t = gameTime + Long.remainderUnsigned(mix(seed ^ (band * 0xD1B54A32D192ED03L)), GUST_SLOT);
        long slot = Math.floorDiv(t, GUST_SLOT);
        long h = mix(seed ^ (band * 0x9E3779B97F4A7C15L) ^ (slot * 0xC2B2AE3D27D4EB4FL));
        if ((h >>> 11) * 0x1.0p-53 >= GUST_CHANCE) return 1.0;
        int len = GUST_MIN + (int) Long.remainderUnsigned(h >>> 8, GUST_MAX - GUST_MIN + 1);
        int start = (int) Long.remainderUnsigned(h >>> 24, GUST_SLOT - len);
        double peak = GUST_PEAK_MIN + (GUST_PEAK_MAX - GUST_PEAK_MIN) * ((h & 0xFFFF) / 65535.0);
        double local = Math.floorMod(t, GUST_SLOT) - start;
        if (local <= 0 || local >= len) return 1.0;
        return 1.0 + peak * Math.min(ease(local / GUST_RISE), ease((len - local) / GUST_FALL));
    }

    private static double ease(double x) {
        x = BalloonPhysics.clamp(x, 0.0, 1.0);
        return x * x * (3 - 2 * x);
    }

    public static int band(double y) {
        return Math.floorDiv((int) Math.floor(y), BAND);
    }

    public static double angle(long seed, int band, long gameTime) {
        double base = (mix(seed ^ (band * 0x9E3779B97F4A7C15L)) >>> 11) * 0x1.0p-53 * Math.PI * 2;
        double dir = ((band & 1) == 0) ? 1 : -1;
        return base + dir * (gameTime % (long) TURN_TICKS) / TURN_TICKS * Math.PI * 2;
    }

    public static double speed(double y) {
        double t = BalloonPhysics.clamp((y - CALM_Y) / RAMP, 0.0, 1.0);
        return MIN_SPEED + (MAX_SPEED - MIN_SPEED) * t * t;
    }

    /**
     * Where the wind (x, z) blows relative to a look direction (yaw in degrees, Minecraft convention): 0 = the way
     * you look, positive = to your right, in radians (-pi..pi).
     */
    public static double relativeAngle(double wx, double wz, float yawDeg) {
        double r = Math.toRadians(yawDeg);
        double fwd = -Math.sin(r) * wx + Math.cos(r) * wz;
        double right = -Math.cos(r) * wx - Math.sin(r) * wz;
        return Math.atan2(right, fwd);
    }

    /** The wind (x, z) turned toward a heading (yaw in degrees) by {@code share} of the shorter arc; speed is kept. */
    public static double[] favour(double[] w, float yawDeg, double share) {
        double s = Math.hypot(w[0], w[1]);
        if (s < 1e-12 || share <= 0) return w;
        double a = Math.atan2(w[1], w[0]);
        double r = Math.toRadians(yawDeg);
        double h = Math.atan2(Math.cos(r), -Math.sin(r));
        double b = a + Math.atan2(Math.sin(h - a), Math.cos(h - a)) * share;
        return new double[] {Math.cos(b) * s, Math.sin(b) * s};
    }

    /** How much the wind carries you the way you look: 1 = straight there, 0 = across, -1 = straight back. */
    public static double help(double wx, double wz, float yawDeg) {
        return wx * wx + wz * wz < 1e-12 ? 0.0 : Math.cos(relativeAngle(wx, wz, yawDeg));
    }

    /**
     * Wind velocity (x, z) in blocks per tick. The direction turns between the two nearest bands along the shorter arc
     * (blending the vectors would cancel out when two bands blow nearly opposite ways).
     */
    public static double[] at(long seed, double y, long gameTime) {
        return at(seed, y, gameTime, true);
    }

    /** As {@link #at(long, double, long)}, with or without gusts. */
    public static double[] at(long seed, double y, long gameTime, boolean gusts) {
        double f = y / BAND - 0.5;
        int lo = (int) Math.floor(f);
        double t = f - lo;
        t = t * t * (3 - 2 * t);
        double a0 = angle(seed, lo, gameTime), a1 = angle(seed, lo + 1, gameTime);
        double d = Math.atan2(Math.sin(a1 - a0), Math.cos(a1 - a0));
        double a = a0 + d * t;
        double s = speed(y);
        if (gusts) s *= gust(seed, lo, gameTime) + (gust(seed, lo + 1, gameTime) - gust(seed, lo, gameTime)) * t;
        return new double[] {Math.cos(a) * s, Math.sin(a) * s};
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
