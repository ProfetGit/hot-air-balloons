package io.github.profetgit.hotairballoons.entity;

/**
 * Wind per height band: each band has its own direction from the world seed, turning slowly over the day, and wind
 * gets stronger with height. Server-side only; balloons sync the result to clients.
 */
public final class Wind {
    public static final int BAND = 16;
    public static final double CALM_Y = 64.0;
    public static final double MIN_SPEED = 0.008;
    public static final double MAX_SPEED = 0.035;
    /** Height over which wind goes from MIN to MAX. */
    public static final double RAMP = 160.0;
    /** One full turn of a band's direction takes this many ticks (about two days). */
    public static final double TURN_TICKS = 48000.0;

    private Wind() {}

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
        return MIN_SPEED + (MAX_SPEED - MIN_SPEED) * t;
    }

    /**
     * Wind velocity (x, z) in blocks per tick. The direction turns between the two nearest bands along the shorter arc
     * (blending the vectors would cancel out when two bands blow nearly opposite ways).
     */
    public static double[] at(long seed, double y, long gameTime) {
        double f = y / BAND - 0.5;
        int lo = (int) Math.floor(f);
        double t = f - lo;
        t = t * t * (3 - 2 * t);
        double a0 = angle(seed, lo, gameTime), a1 = angle(seed, lo + 1, gameTime);
        double d = Math.atan2(Math.sin(a1 - a0), Math.cos(a1 - a0));
        double a = a0 + d * t;
        double s = speed(y);
        return new double[] {Math.cos(a) * s, Math.sin(a) * s};
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
